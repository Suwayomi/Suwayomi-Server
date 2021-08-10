/*
 * Copyright 2016 Andy Bao
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xyz.nulldev.androidcompat.androidimpl;

import android.content.*;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.*;
import android.view.Display;
import android.view.DisplayAdjustments;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kodein.di.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.nulldev.androidcompat.info.ApplicationInfoImpl;
import xyz.nulldev.androidcompat.io.AndroidFiles;
import xyz.nulldev.androidcompat.io.sharedprefs.JavaSharedPreferences;
import xyz.nulldev.androidcompat.service.ServiceSupport;
import xyz.nulldev.androidcompat.util.KodeinGlobalHelper;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Custom context implementation.
 *
 */
public class CustomContext extends Context implements DIAware {
    private final DI kodein;
    public CustomContext() {
        this(KodeinGlobalHelper.kodein());
    }

    public CustomContext(DI kodein) {
        this.kodein = kodein;

        //Init configs
        androidFiles = KodeinGlobalHelper.instance(AndroidFiles.class, getDi());
        applicationInfo = KodeinGlobalHelper.instance(ApplicationInfoImpl.class, getDi());
        serviceSupport = KodeinGlobalHelper.instance(ServiceSupport.class, getDi());
        fakePackageManager = KodeinGlobalHelper.instance(FakePackageManager.class, getDi());
    }

    @NotNull
    @Override
    public DI getDi() {
        return kodein;
    }

    private AndroidFiles androidFiles;
    private ApplicationInfoImpl applicationInfo;
    private ServiceSupport serviceSupport;
    private FakePackageManager fakePackageManager;

    private Logger logger = LoggerFactory.getLogger(CustomContext.class);

    private Map<String, Object> serviceMap = new HashMap<>();
    {
        serviceMap.put(Context.CONNECTIVITY_SERVICE, ConnectivityManager.INSTANCE);
        serviceMap.put(Context.POWER_SERVICE, PowerManager.INSTANCE);
    }

    @Override
    public AssetManager getAssets() {
        return null;
    }

    @Override
    public Resources getResources() {
        return null;
    }

    @Override
    public PackageManager getPackageManager() {
        return fakePackageManager;
    }

    @Override
    public ContentResolver getContentResolver() {
        return null;
    }

    @Override
    public Looper getMainLooper() {
        return null;
    }

    @Override
    public Context getApplicationContext() {
        return this;
    }

    @Override
    public void setTheme(int i) {

    }

    @Override
    public Resources.Theme getTheme() {
        return null;
    }

    @Override
    public ClassLoader getClassLoader() {
        return this.getClass().getClassLoader();
    }

    @Override
    public String getPackageName() {
        return null;
    }

    @Override
    public String getBasePackageName() {
        return null;
    }

    @Override
    public String getOpPackageName() {
        return null;
    }

    @Override
    public ApplicationInfo getApplicationInfo() {
        return applicationInfo;
    }

    @Override
    public String getPackageResourcePath() {
        return null;
    }

    @Override
    public String getPackageCodePath() {
        return null;
    }

    /** Fake shared prefs! **/
    private Map<String, SharedPreferences> prefs = new HashMap<>(); //Cache

    @Override
    public synchronized SharedPreferences getSharedPreferences(String s, int i) {
        SharedPreferences preferences = prefs.get(s);
        //Create new shared preferences if one does not exist
        if(preferences == null) {
            preferences = new JavaSharedPreferences(s);
            prefs.put(s, preferences);
        }
        return preferences;
    }

    @Override
    public SharedPreferences getSharedPreferences(@NotNull File file, int mode) {
        String path = file.getAbsolutePath().replace('\\', '/');
        int firstSlash = path.indexOf("/");
        return new JavaSharedPreferences(path.substring(firstSlash));
    }

    @Override
    public boolean moveSharedPreferencesFrom(Context sourceContext, String name) {
        return false;
    }

    @Override
    public boolean deleteSharedPreferences(String name) {
        JavaSharedPreferences item = (JavaSharedPreferences) prefs.remove(name);
        return item.deleteAll();
    }

    @Override
    public FileInputStream openFileInput(String s) throws FileNotFoundException {
        return null;
    }

    @Override
    public FileOutputStream openFileOutput(String s, int i) throws FileNotFoundException {
        return null;
    }

    @Override
    public boolean deleteFile(String s) {
        return false;
    }

    @Override
    public File getFileStreamPath(String s) {
        return null;
    }

    @Override
    public File getSharedPreferencesPath(String name) {
        return null;
    }

    @Override
    public File getDataDir() {
        return androidFiles.getDataDir();
    }

    @Override
    public File getFilesDir() {
        return androidFiles.getFilesDir();
    }

    @Override
    public File getNoBackupFilesDir() {
        return androidFiles.getNoBackupFilesDir();
    }

    @Override
    public File getExternalFilesDir(String s) {
        return androidFiles.getExternalFilesDirs().get(0);
    }

    @Override
    public File[] getExternalFilesDirs(String s) {
        List<File> files = androidFiles.getExternalFilesDirs();
        return files.toArray(new File[files.size()]);
    }

    @Override
    public File getObbDir() {
        return androidFiles.getObbDirs().get(0);
    }

    @Override
    public File[] getObbDirs() {
        List<File> files = androidFiles.getObbDirs();
        return files.toArray(new File[files.size()]);
    }

    @Override
    public File getCacheDir() {
        return androidFiles.getCacheDir();
    }

    @Override
    public File getCodeCacheDir() {
        return androidFiles.getCodeCacheDir();
    }

    @Override
    public File getExternalCacheDir() {
        return androidFiles.getExternalCacheDirs().get(0);
    }

    @Override
    public File[] getExternalCacheDirs() {
        List<File> files = androidFiles.getExternalCacheDirs();
        return files.toArray(new File[files.size()]);
    }

    @Override
    public File[] getExternalMediaDirs() {
        List<File> files = androidFiles.getExternalMediaDirs();
        return files.toArray(new File[files.size()]);
    }

    @Override
    public String[] fileList() {
        return new String[0];
    }

    @Override
    public File getDir(String s, int i) {
        return null;
    }

    @Override
    public SQLiteDatabase openOrCreateDatabase(String s, int i, SQLiteDatabase.CursorFactory cursorFactory) {
        return openOrCreateDatabase(s, i, cursorFactory, null);
    }

    @Override
    public SQLiteDatabase openOrCreateDatabase(String s, int i, SQLiteDatabase.CursorFactory cursorFactory, DatabaseErrorHandler databaseErrorHandler) {
        return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(s).getAbsolutePath(), cursorFactory, databaseErrorHandler);
    }

    @Override
    public boolean moveDatabaseFrom(Context sourceContext, String name) {
        return false;
    }

    @Override
    public boolean deleteDatabase(String s) {
        return false;
    }

    @Override
    public File getDatabasePath(String s) {
        return new File(new File(androidFiles.getRootDir(), "databases"), s);
    }

    @Override
    public String[] databaseList() {
        return new String[0];
    }

    @Override
    public Drawable getWallpaper() {
        return null;
    }

    @Override
    public Drawable peekWallpaper() {
        return null;
    }

    @Override
    public int getWallpaperDesiredMinimumWidth() {
        return 0;
    }

    @Override
    public int getWallpaperDesiredMinimumHeight() {
        return 0;
    }

    @Override
    public void setWallpaper(Bitmap bitmap) throws IOException {

    }

    @Override
    public void setWallpaper(InputStream inputStream) throws IOException {

    }

    @Override
    public void clearWallpaper() throws IOException {

    }

    @Override
    public void startActivity(Intent intent) {

    }

    @Override
    public void startActivity(Intent intent, Bundle bundle) {

    }

    @Override
    public void startActivities(Intent[] intents) {

    }

    @Override
    public void startActivities(Intent[] intents, Bundle bundle) {

    }

    @Override
    public void startIntentSender(IntentSender intentSender, Intent intent, int i, int i1, int i2) throws IntentSender.SendIntentException {

    }

    @Override
    public void startIntentSender(IntentSender intentSender, Intent intent, int i, int i1, int i2, Bundle bundle) throws IntentSender.SendIntentException {

    }

    @Override
    public void sendBroadcast(Intent intent) {

    }

    @Override
    public void sendBroadcast(Intent intent, String s) {

    }

    @Override
    public void sendBroadcastMultiplePermissions(Intent intent, String[] receiverPermissions) {

    }

    @Override
    public void sendBroadcast(Intent intent, String receiverPermission, Bundle options) {

    }

    @Override
    public void sendBroadcast(Intent intent, String receiverPermission, int appOp) {

    }

    @Override
    public void sendOrderedBroadcast(Intent intent, String s) {

    }

    @Override
    public void sendOrderedBroadcast(Intent intent, String s, BroadcastReceiver broadcastReceiver, Handler handler, int i, String s1, Bundle bundle) {

    }

    @Override
    public void sendOrderedBroadcast(Intent intent, String receiverPermission, Bundle options, BroadcastReceiver resultReceiver, Handler scheduler, int initialCode, String initialData, Bundle initialExtras) {

    }

    @Override
    public void sendOrderedBroadcast(Intent intent, String receiverPermission, int appOp, BroadcastReceiver resultReceiver, Handler scheduler, int initialCode, String initialData, Bundle initialExtras) {

    }

    @Override
    public void sendBroadcastAsUser(Intent intent, UserHandle userHandle) {

    }

    @Override
    public void sendBroadcastAsUser(Intent intent, UserHandle userHandle, String s) {

    }

    @Override
    public void sendBroadcastAsUser(Intent intent, UserHandle user, String receiverPermission, int appOp) {

    }

    @Override
    public void sendOrderedBroadcastAsUser(Intent intent, UserHandle userHandle, String s, BroadcastReceiver broadcastReceiver, Handler handler, int i, String s1, Bundle bundle) {

    }

    @Override
    public void sendOrderedBroadcastAsUser(Intent intent, UserHandle user, String receiverPermission, int appOp, BroadcastReceiver resultReceiver, Handler scheduler, int initialCode, String initialData, Bundle initialExtras) {

    }

    @Override
    public void sendOrderedBroadcastAsUser(Intent intent, UserHandle user, String receiverPermission, int appOp, Bundle options, BroadcastReceiver resultReceiver, Handler scheduler, int initialCode, String initialData, Bundle initialExtras) {

    }

    @Override
    public void sendStickyBroadcast(Intent intent) {

    }

    @Override
    public void sendStickyOrderedBroadcast(Intent intent, BroadcastReceiver broadcastReceiver, Handler handler, int i, String s, Bundle bundle) {

    }

    @Override
    public void removeStickyBroadcast(Intent intent) {

    }

    @Override
    public void sendStickyBroadcastAsUser(Intent intent, UserHandle userHandle) {

    }

    @Override
    public void sendStickyBroadcastAsUser(Intent intent, UserHandle user, Bundle options) {

    }

    @Override
    public void sendStickyOrderedBroadcastAsUser(Intent intent, UserHandle userHandle, BroadcastReceiver broadcastReceiver, Handler handler, int i, String s, Bundle bundle) {

    }

    @Override
    public void removeStickyBroadcastAsUser(Intent intent, UserHandle userHandle) {

    }

    @Override
    public Intent registerReceiver(BroadcastReceiver broadcastReceiver, IntentFilter intentFilter) {
        return null;
    }

    @Override
    public Intent registerReceiver(BroadcastReceiver broadcastReceiver, IntentFilter intentFilter, String s, Handler handler) {
        return null;
    }

    @Override
    public Intent registerReceiverAsUser(BroadcastReceiver receiver, UserHandle user, IntentFilter filter, String broadcastPermission, Handler scheduler) {
        return null;
    }

    public void unregisterReceiver(BroadcastReceiver broadcastReceiver) {

    }

    @Override
    public ComponentName startService(Intent intent) {
        serviceSupport.startService(this, intent);
        return intent.getComponent();
    }

    @Override
    public boolean stopService(Intent intent) {
        serviceSupport.stopService(this, intent);
        return true;
    }

    @Override
    public ComponentName startServiceAsUser(Intent service, UserHandle user) {
        logger.warn("An attempt was made to start the service: '{}' as another user! Since multiple user services are currently not supported, the service will be started as the current user!", service.getComponent().getClassName());
        return startService(service);
    }

    @Override
    public boolean stopServiceAsUser(Intent service, UserHandle user) {
        logger.warn("An attempt was made to stop the service: '{}' as another user! Since multiple user services are currently not supported, the service will be stopped as the current user!", service.getComponent().getClassName());
        return stopService(service);
    }

    @Override
    public boolean bindService(Intent intent, ServiceConnection serviceConnection, int i) {
        return false;
    }

    @Override
    public void unbindService(ServiceConnection serviceConnection) {

    }

    @Override
    public boolean startInstrumentation(ComponentName componentName, String s, Bundle bundle) {
        return false;
    }

    @Override
    public Object getSystemService(String s) {
        return serviceMap.get(s);
    }

    @Override
    public String getSystemServiceName(Class<?> aClass) {
        return null;
    }

    @Override
    public int checkPermission(String s, int i, int i1) {
        return PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public int checkPermission(String permission, int pid, int uid, IBinder callerToken) {
        return PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public int checkCallingPermission(String s) {
        return PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public int checkCallingOrSelfPermission(String s) {
        return PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public int checkSelfPermission(String s) {
        return PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void enforcePermission(String s, int i, int i1, String s1) {

    }

    @Override
    public void enforceCallingPermission(String s, String s1) {

    }

    @Override
    public void enforceCallingOrSelfPermission(String s, String s1) {

    }

    @Override
    public void grantUriPermission(String s, Uri uri, int i) {

    }

    @Override
    public void revokeUriPermission(Uri uri, int i) {

    }

    @Override
    public int checkUriPermission(Uri uri, int i, int i1, int i2) {
        return 0;
    }

    @Override
    public int checkUriPermission(Uri uri, int pid, int uid, int modeFlags, IBinder callerToken) {
        return 0;
    }

    @Override
    public int checkCallingUriPermission(Uri uri, int i) {
        return 0;
    }

    @Override
    public int checkCallingOrSelfUriPermission(Uri uri, int i) {
        return 0;
    }

    @Override
    public int checkUriPermission(Uri uri, String s, String s1, int i, int i1, int i2) {
        return 0;
    }

    @Override
    public void enforceUriPermission(Uri uri, int i, int i1, int i2, String s) {

    }

    @Override
    public void enforceCallingUriPermission(Uri uri, int i, String s) {

    }

    @Override
    public void enforceCallingOrSelfUriPermission(Uri uri, int i, String s) {

    }

    @Override
    public void enforceUriPermission(Uri uri, String s, String s1, int i, int i1, int i2, String s2) {

    }

    @Override
    public Context createPackageContext(String s, int i) throws PackageManager.NameNotFoundException {
        return null;
    }

    @Override
    public Context createPackageContextAsUser(String packageName, int flags, UserHandle user) throws PackageManager.NameNotFoundException {
        return null;
    }

    @Override
    public Context createApplicationContext(ApplicationInfo application, int flags) throws PackageManager.NameNotFoundException {
        return null;
    }

    @Override
    public int getUserId() {
        return 0;
    }

    @Override
    public Context createConfigurationContext(Configuration configuration) {
        return null;
    }

    @Override
    public Context createDisplayContext(Display display) {
        return null;
    }

    @Override
    public Context createDeviceProtectedStorageContext() {
        return null;
    }

    @Override
    public Context createCredentialProtectedStorageContext() {
        return null;
    }

    @Override
    public DisplayAdjustments getDisplayAdjustments(int displayId) {
        return null;
    }

    @Override
    public Display getDisplay() {
        return null;
    }

    @Override
    public boolean isDeviceProtectedStorage() {
        return false;
    }

    @Override
    public boolean isCredentialProtectedStorage() {
        return false;
    }

    @NotNull
    @Override
    public DIContext<?> getDiContext() {
        return getDi().getDiContext();
    }

    @Nullable
    @Override
    public DITrigger getDiTrigger() {
        return null;
    }
}

