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
import org.koin.core.Koin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.nulldev.androidcompat.info.ApplicationInfoImpl;
import xyz.nulldev.androidcompat.io.AndroidFiles;
import xyz.nulldev.androidcompat.io.sharedprefs.JavaSharedPreferences;
import xyz.nulldev.androidcompat.service.ServiceSupport;
import xyz.nulldev.androidcompat.util.KoinGlobalHelper;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom context implementation.
 *
 */
public class CustomContext extends Context {
    private final Koin koin;
    public CustomContext() {
        this(KoinGlobalHelper.koin());
    }

    public CustomContext(Koin koin) {
        this.koin = koin;

        //Init configs
        androidFiles = KoinGlobalHelper.instance(AndroidFiles.class, getDi());
        applicationInfo = KoinGlobalHelper.instance(ApplicationInfoImpl.class, getDi());
        serviceSupport = KoinGlobalHelper.instance(ServiceSupport.class, getDi());
        fakePackageManager = KoinGlobalHelper.instance(FakePackageManager.class, getDi());
    }

    @NotNull
    public Koin getDi() {
        return koin;
    }

    private AndroidFiles androidFiles;
    private ApplicationInfoImpl applicationInfo;
    private ServiceSupport serviceSupport;
    private FakePackageManager fakePackageManager;

    private Logger logger = LoggerFactory.getLogger(CustomContext.class);

    private Map<String, Object> serviceMap = new ConcurrentHashMap<>();
    {
        serviceMap.put(Context.CONNECTIVITY_SERVICE, ConnectivityManager.INSTANCE);
        serviceMap.put(Context.POWER_SERVICE, PowerManager.INSTANCE);
    }

    @Override
    public AssetManager getAssets() {
        throw new UnsupportedOperationException(
                "AssetManager is not available in the Suwayomi AndroidCompat environment");
    }

    @Override
    public Resources getResources() {
        throw new UnsupportedOperationException(
                "Resources is not available in the Suwayomi AndroidCompat environment");
    }

    @Override
    public PackageManager getPackageManager() {
        return fakePackageManager;
    }

    @Override
    public ContentResolver getContentResolver() {
        throw new UnsupportedOperationException(
                "ContentResolver is not available in the Suwayomi AndroidCompat environment");
    }

    @Override
    public Looper getMainLooper() {
        return Looper.getMainLooper();
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
        throw new UnsupportedOperationException(
                "Themes are not available in the Suwayomi AndroidCompat environment");
    }

    @Override
    public ClassLoader getClassLoader() {
        return this.getClass().getClassLoader();
    }

    @Override
    public String getPackageName() {
        String packageName = applicationInfo.packageName;
        return packageName != null ? packageName : "suwayomi";
    }

    @Override
    public String getBasePackageName() {
        return getPackageName();
    }

    @Override
    public String getOpPackageName() {
        return getPackageName();
    }

    @Override
    public ApplicationInfo getApplicationInfo() {
        return applicationInfo;
    }

    @Override
    public String getPackageResourcePath() {
        return new File(androidFiles.getPackagesDir(), getPackageName()).getAbsolutePath();
    }

    @Override
    public String getPackageCodePath() {
        return new File(androidFiles.getPackagesDir(), getPackageName()).getAbsolutePath();
    }

    /** Fake shared prefs, keyed by caller name but stored isolated per package! **/
    private Map<String, SharedPreferences> prefs = new ConcurrentHashMap<>(); //Cache

    /**
     * Builds the on-disk key used by {@link JavaSharedPreferences} for a logical
     * preference name. Keys are namespaced by the current package name so that
     * extensions using the same preference name don't share storage. A hash of
     * the raw name is appended because {@code SafePath.buildValidFilename}
     * collapses distinct names such as {@code "a/b"} and {@code "a_b"} onto the
     * same file.
     */
    private String sharedPrefsStorageKey(String name) {
        String hash = Integer.toHexString(name.hashCode());
        return getPackageName() + ":" + name + "." + hash;
    }

    @Override
    public SharedPreferences getSharedPreferences(String s, int i) {
        // The plain name doubles as the legacy adoption source: releases
        // before the per-package namespacing stored the values under
        // settings/<safe(name)>.xml.
        return prefs.computeIfAbsent(s, name -> new JavaSharedPreferences(sharedPrefsStorageKey(name), name));
    }

    @Override
    public SharedPreferences getSharedPreferences(@NotNull File file, int mode) {
        // Key the preferences by the file's absolute path (the previous
        // implementation truncated the path at the first slash, both losing the
        // drive letter on Windows and colliding distinct files).
        String name = "file:" + file.getAbsolutePath();
        // Those older files were stored under the slash-truncated path.
        String normalized = file.getAbsolutePath().replace('\\', '/');
        int firstSlash = normalized.indexOf('/');
        String legacyName = firstSlash >= 0 ? normalized.substring(firstSlash) : normalized;
        return prefs.computeIfAbsent(
                name, n -> new JavaSharedPreferences(sharedPrefsStorageKey(n), legacyName));
    }

    @Override
    public boolean moveSharedPreferencesFrom(Context sourceContext, String name) {
        SharedPreferences source = sourceContext.getSharedPreferences(name, Context.MODE_PRIVATE);
        if (source == null) {
            return false;
        }
        SharedPreferences.Editor editor = getSharedPreferences(name, Context.MODE_PRIVATE).edit();
        editor.clear();
        for (Map.Entry<String, ?> entry : source.getAll().entrySet()) {
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            String key = entry.getKey();
            if (value instanceof String) {
                editor.putString(key, (String) value);
            } else if (value instanceof Integer) {
                editor.putInt(key, (Integer) value);
            } else if (value instanceof Long) {
                editor.putLong(key, (Long) value);
            } else if (value instanceof Float) {
                editor.putFloat(key, (Float) value);
            } else if (value instanceof Boolean) {
                editor.putBoolean(key, (Boolean) value);
            } else if (value instanceof java.util.Set) {
                editor.putStringSet(key, new java.util.HashSet<>((java.util.Set<String>) value));
            }
        }
        return editor.commit();
    }

    @Override
    public boolean deleteSharedPreferences(String name) {
        SharedPreferences item = prefs.remove(name);
        JavaSharedPreferences preferences;
        if (item instanceof JavaSharedPreferences) {
            preferences = (JavaSharedPreferences) item;
        } else {
            // Not in the cache: delete whatever is persisted under the storage
            // key (and under the legacy name, so it is not adopted back later).
            preferences = new JavaSharedPreferences(sharedPrefsStorageKey(name), name);
        }
        return preferences.deleteAll();
    }

    private static void validateFileName(String name) {
        if (name == null || name.contains("/") || name.contains("\\")) {
            throw new IllegalArgumentException("File " + name + " contains a path separator");
        }
    }

    @Override
    public FileInputStream openFileInput(String s) throws FileNotFoundException {
        return new FileInputStream(getFileStreamPath(s));
    }

    @Override
    public FileOutputStream openFileOutput(String s, int i) throws FileNotFoundException {
        boolean append = (i & Context.MODE_APPEND) != 0;
        File target = getFileStreamPath(s);
        File parent = target.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        return new FileOutputStream(target, append);
    }

    @Override
    public boolean deleteFile(String s) {
        return getFileStreamPath(s).delete();
    }

    @Override
    public File getFileStreamPath(String s) {
        validateFileName(s);
        return new File(getFilesDir(), s);
    }

    @Override
    public File getSharedPreferencesPath(String name) {
        // Adopt the pre-namespacing file first so the returned path reflects
        // the effective on-disk location even before the prefs are opened.
        JavaSharedPreferences.adoptLegacyStorageFile(sharedPrefsStorageKey(name), name);
        // Mirrors JavaSharedPreferences' storage layout:
        // <ApplicationRootDir>/settings/<safe(name)>.xml
        return Paths.get(
                        xyz.nulldev.ts.config.ApplicationRootDirKt.getApplicationRootDir(),
                        "settings",
                        xyz.nulldev.androidcompat.util.SafePath.INSTANCE
                                .buildValidFilename(sharedPrefsStorageKey(name)) + ".xml")
                .toFile();
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
        List<File> dirs = androidFiles.getExternalFilesDirs();
        return dirs.isEmpty() ? null : dirs.get(0);
    }

    @Override
    public File[] getExternalFilesDirs(String s) {
        List<File> files = androidFiles.getExternalFilesDirs();
        return files.toArray(new File[files.size()]);
    }

    @Override
    public File getObbDir() {
        List<File> dirs = androidFiles.getObbDirs();
        return dirs.isEmpty() ? null : dirs.get(0);
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
        List<File> dirs = androidFiles.getExternalCacheDirs();
        return dirs.isEmpty() ? null : dirs.get(0);
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
        String[] files = getFilesDir().list();
        return files != null ? files : new String[0];
    }

    @Override
    public File getDir(String s, int i) {
        validateFileName(s);
        File dir = new File(getDataDir(), s);
        dir.mkdirs();
        return dir;
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
        File source = sourceContext.getDatabasePath(name);
        if (source == null || !source.exists()) {
            return false;
        }
        try {
            File target = getDatabasePath(name);
            File parent = target.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException | IllegalArgumentException e) {
            logger.error("Failed to move database " + name, e);
            return false;
        }
    }

    @Override
    public boolean deleteDatabase(String s) {
        try {
            return getDatabasePath(s).delete();
        } catch (IllegalArgumentException e) {
            logger.warn("Refusing to delete database with illegal name: {}", s);
            return false;
        }
    }

    @Override
    public File getDatabasePath(String s) {
        File databasesDir = androidFiles.getDatabasesDir();
        try {
            File parent = databasesDir.getCanonicalFile();
            File target = new File(parent, s).getCanonicalFile();
            // "" or "." canonicalize to the databases directory itself — a database
            // name must denote a file strictly inside it.
            if (target.equals(parent) || !target.getPath().startsWith(parent.getPath() + File.separator)) {
                throw new IllegalArgumentException(
                        "Database name escapes the databases directory: " + s);
            }
            return target;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to resolve database path", e);
        }
    }

    @Override
    public String[] databaseList() {
        String[] list = androidFiles.getDatabasesDir().list();
        return list != null ? list : new String[0];
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

    @Override
    public void unregisterReceiver(BroadcastReceiver broadcastReceiver) {

    }

    @Override
    public ComponentName startService(Intent intent) {
        serviceSupport.startService(this, intent);
        return intent.getComponent();
    }

    @Override
    public boolean stopService(Intent intent) {
        String name = serviceSupport.intentToClassName(intent);
        boolean wasRunning = serviceSupport.getRunningServices().containsKey(name);
        serviceSupport.stopService(this, intent);
        return wasRunning;
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
        // Service binding is not implemented in this environment; services can
        // only be started/stopped. Returning false is the documented failure
        // contract (the ServiceConnection callbacks are never invoked).
        logger.warn("bindService is not supported; service not bound: {}", intent);
        return false;
    }

    @Override
    public void unbindService(ServiceConnection serviceConnection) {
        // no-op: services are never bound (see bindService)
    }

    @Override
    public boolean startInstrumentation(ComponentName componentName, String s, Bundle bundle) {
        return false;
    }

    @Override
    public Object getSystemService(String s) {
        Object service = serviceMap.get(s);
        if (service == null) {
            logger.warn("getSystemService: no compat implementation registered for '{}'", s);
        }
        return service;
    }

    @Override
    public String getSystemServiceName(Class<?> aClass) {
        for (Map.Entry<String, Object> entry : serviceMap.entrySet()) {
            Object service = entry.getValue();
            if (service != null && aClass.isInstance(service)) {
                return entry.getKey();
            }
        }
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
        return PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public int checkUriPermission(Uri uri, int pid, int uid, int modeFlags, IBinder callerToken) {
        return PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public int checkCallingUriPermission(Uri uri, int i) {
        return PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public int checkCallingOrSelfUriPermission(Uri uri, int i) {
        return PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public int checkUriPermission(Uri uri, String s, String s1, int i, int i1, int i2) {
        return PackageManager.PERMISSION_GRANTED;
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
        // Validate the package exists, then degrade to this context: there is
        // only one filesystem sandbox in this environment.
        fakePackageManager.getPackageInfo(s, 0);
        return this;
    }

    @Override
    public Context createPackageContextAsUser(String packageName, int flags, UserHandle user) throws PackageManager.NameNotFoundException {
        fakePackageManager.getPackageInfo(packageName, flags);
        return this;
    }

    @Override
    public Context createApplicationContext(ApplicationInfo application, int flags) throws PackageManager.NameNotFoundException {
        fakePackageManager.getPackageInfo(application.packageName, 0);
        return this;
    }

    @Override
    public int getUserId() {
        return 0;
    }

    @Override
    public Context createConfigurationContext(Configuration configuration) {
        // No configuration subsystem: the single compat context is returned.
        return this;
    }

    @Override
    public Context createDisplayContext(Display display) {
        // Single-display environment: the single compat context is returned.
        return this;
    }

    @Override
    public Context createDeviceProtectedStorageContext() {
        // No separate device-protected storage area: use this context.
        return this;
    }

    @Override
    public Context createCredentialProtectedStorageContext() {
        return this;
    }

    @Override
    public DisplayAdjustments getDisplayAdjustments(int displayId) {
        return new DisplayAdjustments();
    }

    @Override
    public Display getDisplay() {
        // There is no display subsystem; null is the documented "no display" contract.
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
}

