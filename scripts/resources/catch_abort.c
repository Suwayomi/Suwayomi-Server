// Linux only:
// Attempts to catch SIGTRAP, inform Java, then exit the thread instead of bringing down the whole process

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <dlfcn.h>
#include <signal.h>
#include <pthread.h>
#include <execinfo.h>

#include <jni.h>

JavaVM *g_vm;

void load_vm() {
  if (g_vm) return;
  JavaVM *vms[1];
  jsize n = 0;
  // JNI_OnLoad won't be called when loaded via LD_PRELOAD, so attempt to find the VM now
  if (JNI_GetCreatedJavaVMs(vms, 1, &n) == JNI_OK && n > 0) {
    g_vm = vms[0];
  }
}

jint throwThreadDeath(JNIEnv *env, char *message) {
  char *className = "java/lang/UnknownError";
  jclass exClass = (*env)->FindClass(env, className);
  if (exClass == NULL) return JNI_ERR;
  return (*env)->ThrowNew(env, exClass, message);
}

void signalHandler(int signum, siginfo_t* si, void* uc) {
  void *retaddrs[64];
  int n = backtrace(retaddrs, sizeof(retaddrs) / sizeof(retaddrs[0]));
  printf("\n### ABORT :: Backtrace: ###\n");
  backtrace_symbols_fd(retaddrs, n, STDERR_FILENO);
  printf("### ABORT :: Exiting this thread. If this causes problems, please report the above backtrace to Suwayomi. ###\n\n");

  load_vm();
  if (g_vm) {
    JNIEnv *env;
    jint getEnvStat = (*g_vm)->GetEnv(g_vm, (void**) &env, JNI_VERSION_1_2);
    if (getEnvStat == JNI_EDETACHED) (*g_vm)->AttachCurrentThread(g_vm, (void**) &env, NULL);
    jint exStat = throwThreadDeath(env, "SIGTRAP caught");
    if (exStat != 0) printf("Exception throwing failed: %d\n", exStat);
    (*g_vm)->DetachCurrentThread(g_vm);
  }
  pthread_exit(NULL);
}

__attribute__((constructor))
void dlmain() {
  struct sigaction sa = {0};
  sa.sa_flags     = SA_SIGINFO | SA_RESTART;
  sa.sa_sigaction = &signalHandler;
  sigemptyset(&sa.sa_mask);
  if (sigaction(SIGTRAP, &sa, NULL) != 0) {
    printf("[FATAL] sigaction failed\n");
  }
}
