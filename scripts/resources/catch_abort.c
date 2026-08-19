// Linux only:
// Attempts to catch SIGTRAP and exit the thread instead of bringing down the whole process

#define _GNU_SOURCE
#include <stdio.h>
#include <dlfcn.h>
#include <signal.h>
#include <pthread.h>
#include <execinfo.h>

void signalHandler(int signum, siginfo_t* si, void* uc) {
  void *retaddrs[64];
  int n = backtrace(retaddrs, sizeof(retaddrs) / sizeof(retaddrs[0]));
  printf("\n### ABORT :: Backtrace: ###\n");
  backtrace_symbols_fd(retaddrs, n, STDERR_FILENO);
  printf("### ABORT :: Exiting this thread. If this causes problems, please report the above backtrace to Suwayomi. ###\n\n");
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
  if (sigaction(SIGILL, &sa, NULL) != 0) {
    printf("[FATAL] sigaction failed\n");
  }
}
