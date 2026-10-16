package xyz.nulldev.androidcompat.androidimpl;

import android.app.PackageInstallObserver;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.*;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import xyz.nulldev.androidcompat.pm.InstalledPackage;
import xyz.nulldev.androidcompat.pm.PackageController;
import xyz.nulldev.androidcompat.util.KoinGlobalHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class FakePackageManager extends PackageManager {
    private PackageController controller = KoinGlobalHelper.instance(PackageController.class);

    @Override
    public PackageInfo getPackageInfo(String packageName, int flags) throws NameNotFoundException {
        InstalledPackage installedPackage = controller.findPackage(packageName);

        if(installedPackage == null) throw new NameNotFoundException();

        return installedPackage.getInfo();
    }

    @Override
    public PackageInfo getPackageInfo(VersionedPackage versionedPackage, int flags) throws NameNotFoundException {
        return getPackageInfo(versionedPackage.getPackageName(), flags);
    }

    @Override
    public PackageInfo getPackageInfoAsUser(String packageName, int flags, int userId) throws NameNotFoundException {
        // Single-user compatibility environment: flags are passed through, userId is ignored.
        return getPackageInfo(packageName, flags);
    }

    @Override
    public String[] currentToCanonicalPackageNames(String[] names) {
        // No renamed packages in this environment: canonical == current.
        return names == null ? null : names.clone();
    }

    @Override
    public String[] canonicalToCurrentPackageNames(String[] names) {
        return names == null ? null : names.clone();
    }

    @Override
    public Intent getLaunchIntentForPackage(String packageName) {
        if (controller.findPackage(packageName) == null) return null;
        return new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(packageName);
    }

    @Override
    public Intent getLeanbackLaunchIntentForPackage(String packageName) {
        if (controller.findPackage(packageName) == null) return null;
        return new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setPackage(packageName);
    }

    @Override
    public int[] getPackageGids(String packageName) throws NameNotFoundException {
        return getPackageGids(packageName, 0);
    }

    @Override
    public int[] getPackageGids(String packageName, int flags) throws NameNotFoundException {
        // Throws NameNotFoundException for unknown packages, per contract.
        getPackageInfo(packageName, flags);
        return new int[0];
    }

    @Override
    public int getPackageUid(String packageName, int flags) throws NameNotFoundException {
        ApplicationInfo info = getPackageInfo(packageName, flags).applicationInfo;
        if (info == null) throw new NameNotFoundException(packageName);
        return info.uid;
    }

    @Override
    public int getPackageUidAsUser(String packageName, int userId) throws NameNotFoundException {
        return getPackageUid(packageName, 0);
    }

    @Override
    public int getPackageUidAsUser(String packageName, int flags, int userId) throws NameNotFoundException {
        return getPackageUid(packageName, flags);
    }

    @Override
    public PermissionInfo getPermissionInfo(String name, int flags) throws NameNotFoundException {
        // No permission database in this environment.
        throw new NameNotFoundException(name);
    }

    @Override
    public List<PermissionInfo> queryPermissionsByGroup(String group, int flags) throws NameNotFoundException {
        throw new NameNotFoundException(String.valueOf(group));
    }

    @Override
    public boolean isPermissionReviewModeEnabled() {
        return false;
    }

    @Override
    public PermissionGroupInfo getPermissionGroupInfo(String name, int flags) throws NameNotFoundException {
        throw new NameNotFoundException(name);
    }

    @Override
    public List<PermissionGroupInfo> getAllPermissionGroups(int flags) {
        return new ArrayList<>();
    }

    @Override
    public ApplicationInfo getApplicationInfo(String packageName, int flags) throws NameNotFoundException {
        return getPackageInfo(packageName, flags).applicationInfo;
    }

    @Override
    public ApplicationInfo getApplicationInfoAsUser(String packageName, int flags, int userId) throws NameNotFoundException {
        return getPackageInfoAsUser(packageName, flags, userId).applicationInfo;
    }

    @Override
    public ActivityInfo getActivityInfo(ComponentName component, int flags) throws NameNotFoundException {
        throw new NameNotFoundException(String.valueOf(component));
    }

    @Override
    public ActivityInfo getReceiverInfo(ComponentName component, int flags) throws NameNotFoundException {
        throw new NameNotFoundException(String.valueOf(component));
    }

    @Override
    public ServiceInfo getServiceInfo(ComponentName component, int flags) throws NameNotFoundException {
        throw new NameNotFoundException(String.valueOf(component));
    }

    @Override
    public ProviderInfo getProviderInfo(ComponentName component, int flags) throws NameNotFoundException {
        throw new NameNotFoundException(String.valueOf(component));
    }

    //TODO Return loaded extensions
    @Override
    public List<PackageInfo> getInstalledPackages(int flags) {
        return controller.listInstalled().stream().map(InstalledPackage::getInfo).collect(Collectors.toList());
    }

    @Override
    public List<PackageInfo> getPackagesHoldingPermissions(String[] permissions, int flags) {
        return new ArrayList<>();
    }

    @Override
    public List<PackageInfo> getInstalledPackagesAsUser(int flags, int userId) {
        return getInstalledPackages(flags);
    }

    @Override
    public int checkPermission(String permName, String pkgName) {
        return PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public boolean isPermissionRevokedByPolicy(String permName, String pkgName) {
        return false;
    }

    @Override
    public String getPermissionControllerPackageName() {
        return null;
    }

    @Override
    public boolean addPermission(PermissionInfo info) {
        return true;
    }

    @Override
    public boolean addPermissionAsync(PermissionInfo info) {
        return true;
    }

    @Override
    public void removePermission(String name) {
    }

    @Override
    public void grantRuntimePermission(String packageName, String permissionName, UserHandle user) {
    }

    @Override
    public void revokeRuntimePermission(String packageName, String permissionName, UserHandle user) {
    }

    @Override
    public int getPermissionFlags(String permissionName, String packageName, UserHandle user) {
        return 0;
    }

    @Override
    public void updatePermissionFlags(String permissionName, String packageName, int flagMask, int flagValues, UserHandle user) {
        // All permissions are treated as granted in this environment; nothing to update.
    }

    @Override
    public boolean shouldShowRequestPermissionRationale(String permission) {
        // All permissions are granted in this environment, so no rationale is needed.
        return false;
    }

    @Override
    public int checkSignatures(String pkg1, String pkg2) {
        if (pkg1 == null || pkg2 == null
                || controller.findPackage(pkg1) == null
                || controller.findPackage(pkg2) == null) {
            return PackageManager.SIGNATURE_UNKNOWN_PACKAGE;
        }
        // Only identical packages can claim a signature match; there is no
        // certificate store to actually compare signatures with.
        return pkg1.equals(pkg2) ? PackageManager.SIGNATURE_MATCH : PackageManager.SIGNATURE_NO_MATCH;
    }

    @Override
    public int checkSignatures(int uid1, int uid2) {
        return uid1 == uid2 ? PackageManager.SIGNATURE_MATCH : PackageManager.SIGNATURE_NO_MATCH;
    }

    @Override
    public String[] getPackagesForUid(int uid) {
        return new String[0];
    }

    @Override
    public String getNameForUid(int uid) {
        return null;
    }

    @Override
    public String[] getNamesForUids(int[] uids) {
        return new String[0];
    }

    @Override
    public int getUidForSharedUser(String sharedUserName) throws NameNotFoundException {
        // No shared users in this environment.
        throw new NameNotFoundException(sharedUserName);
    }

    @Override
    public List<ApplicationInfo> getInstalledApplications(int flags) {
        return getInstalledPackages(flags).stream().map((it) -> it.applicationInfo).collect(Collectors.toList());
    }

    @Override
    public List<ApplicationInfo> getInstalledApplicationsAsUser(int flags, int userId) {
        return getInstalledApplications(flags);
    }

    @Override
    public List<InstantAppInfo> getInstantApps() {
        return new ArrayList<>();
    }

    @Override
    public Drawable getInstantAppIcon(String packageName) {
        return null;
    }

    @Override
    public boolean isInstantApp() {
        return false;
    }

    @Override
    public boolean isInstantApp(String packageName) {
        return false;
    }

    @Override
    public int getInstantAppCookieMaxBytes() {
        return 0;
    }

    @Override
    public int getInstantAppCookieMaxSize() {
        return 0;
    }

    @Override
    public byte[] getInstantAppCookie() {
        return new byte[0];
    }

    @Override
    public void clearInstantAppCookie() {

    }

    @Override
    public void updateInstantAppCookie(byte[] cookie) {

    }

    @Override
    public boolean setInstantAppCookie(byte[] cookie) {
        return false;
    }

    @Override
    public String[] getSystemSharedLibraryNames() {
        return new String[0];
    }

    @Override
    public List<SharedLibraryInfo> getSharedLibraries(int flags) {
        return new ArrayList<>();
    }

    @Override
    public List<SharedLibraryInfo> getSharedLibrariesAsUser(int flags, int userId) {
        return new ArrayList<>();
    }

    @Override
    public String getServicesSystemSharedLibraryPackageName() {
        return null;
    }

    @Override
    public String getSharedSystemSharedLibraryPackageName() {
        return null;
    }

    @Override
    public ChangedPackages getChangedPackages(int sequenceNumber) {
        return null;
    }

    @Override
    public FeatureInfo[] getSystemAvailableFeatures() {
        return new FeatureInfo[0];
    }

    @Override
    public boolean hasSystemFeature(String name) {
        return false;
    }

    @Override
    public boolean hasSystemFeature(String name, int version) {
        return false;
    }

    @Override
    public ResolveInfo resolveActivity(Intent intent, int flags) {
        return null;
    }

    @Override
    public ResolveInfo resolveActivityAsUser(Intent intent, int flags, int userId) {
        return null;
    }

    @Override
    public List<ResolveInfo> queryIntentActivities(Intent intent, int flags) {
        return new ArrayList<>();
    }

    @Override
    public List<ResolveInfo> queryIntentActivitiesAsUser(Intent intent, int flags, int userId) {
        return new ArrayList<>();
    }

    @Override
    public List<ResolveInfo> queryIntentActivityOptions(ComponentName caller, Intent[] specifics, Intent intent, int flags) {
        return new ArrayList<>();
    }

    @Override
    public List<ResolveInfo> queryBroadcastReceivers(Intent intent, int flags) {
        return new ArrayList<>();
    }

    @Override
    public List<ResolveInfo> queryBroadcastReceiversAsUser(Intent intent, int flags, int userId) {
        return new ArrayList<>();
    }

    @Override
    public ResolveInfo resolveService(Intent intent, int flags) {
        return null;
    }

    @Override
    public List<ResolveInfo> queryIntentServices(Intent intent, int flags) {
        return new ArrayList<>();
    }

    @Override
    public List<ResolveInfo> queryIntentServicesAsUser(Intent intent, int flags, int userId) {
        return new ArrayList<>();
    }

    @Override
    public List<ResolveInfo> queryIntentContentProvidersAsUser(Intent intent, int flags, int userId) {
        return new ArrayList<>();
    }

    @Override
    public List<ResolveInfo> queryIntentContentProviders(Intent intent, int flags) {
        return new ArrayList<>();
    }

    @Override
    public ProviderInfo resolveContentProvider(String name, int flags) {
        return null;
    }

    @Override
    public ProviderInfo resolveContentProviderAsUser(String name, int flags, int userId) {
        return null;
    }

    @Override
    public List<ProviderInfo> queryContentProviders(String processName, int uid, int flags) {
        return new ArrayList<>();
    }

    @Override
    public InstrumentationInfo getInstrumentationInfo(ComponentName className, int flags) throws NameNotFoundException {
        throw new NameNotFoundException(String.valueOf(className));
    }

    @Override
    public List<InstrumentationInfo> queryInstrumentation(String targetPackage, int flags) {
        return new ArrayList<>();
    }

    @Override
    public Drawable getDrawable(String packageName, int resid, ApplicationInfo appInfo) {
        return null;
    }

    @Override
    public Drawable getActivityIcon(ComponentName activityName) throws NameNotFoundException {
        return null;
    }

    @Override
    public Drawable getActivityIcon(Intent intent) throws NameNotFoundException {
        return null;
    }

    @Override
    public Drawable getActivityBanner(ComponentName activityName) throws NameNotFoundException {
        return null;
    }

    @Override
    public Drawable getActivityBanner(Intent intent) throws NameNotFoundException {
        return null;
    }

    @Override
    public Drawable getDefaultActivityIcon() {
        return null;
    }

    @Override
    public Drawable getApplicationIcon(ApplicationInfo info) {
        return null;
    }

    @Override
    public Drawable getApplicationIcon(String packageName) throws NameNotFoundException {
        return null;
    }

    @Override
    public Drawable getApplicationBanner(ApplicationInfo info) {
        return null;
    }

    @Override
    public Drawable getApplicationBanner(String packageName) throws NameNotFoundException {
        return null;
    }

    @Override
    public Drawable getActivityLogo(ComponentName activityName) throws NameNotFoundException {
        return null;
    }

    @Override
    public Drawable getActivityLogo(Intent intent) throws NameNotFoundException {
        return null;
    }

    @Override
    public Drawable getApplicationLogo(ApplicationInfo info) {
        return null;
    }

    @Override
    public Drawable getApplicationLogo(String packageName) throws NameNotFoundException {
        return null;
    }

    @Override
    public Drawable getUserBadgedIcon(Drawable icon, UserHandle user) {
        return null;
    }

    @Override
    public Drawable getUserBadgedDrawableForDensity(Drawable drawable, UserHandle user, Rect badgeLocation, int badgeDensity) {
        return null;
    }

    @Override
    public Drawable getUserBadgeForDensity(UserHandle user, int density) {
        return null;
    }

    @Override
    public Drawable getUserBadgeForDensityNoBackground(UserHandle user, int density) {
        return null;
    }

    @Override
    public CharSequence getUserBadgedLabel(CharSequence label, UserHandle user) {
        return null;
    }

    @Override
    public CharSequence getText(String packageName, int resid, ApplicationInfo appInfo) {
        return null;
    }

    @Override
    public XmlResourceParser getXml(String packageName, int resid, ApplicationInfo appInfo) {
        return null;
    }

    @Override
    public CharSequence getApplicationLabel(ApplicationInfo info) {
        // Fall back to the package name when no label is declared, like Android.
        return info.nonLocalizedLabel != null ? info.nonLocalizedLabel : info.packageName;
    }

    @Override
    public Resources getResourcesForActivity(ComponentName activityName) throws NameNotFoundException {
        return null;
    }

    @Override
    public Resources getResourcesForApplication(ApplicationInfo app) throws NameNotFoundException {
        return null;
    }

    @Override
    public Resources getResourcesForApplication(String appPackageName) throws NameNotFoundException {
        return null;
    }

    @Override
    public Resources getResourcesForApplicationAsUser(String appPackageName, int userId) throws NameNotFoundException {
        return null;
    }

    @Override
    public void installPackage(Uri packageURI, PackageInstallObserver observer, int flags, String installerPackageName) {
        // Package installation is not supported here, but the observer must
        // still be invoked or the caller would wait forever.
        if (observer != null) {
            try {
                observer.onPackageInstalled(
                        packageURI != null ? String.valueOf(packageURI) : null,
                        PackageManager.INSTALL_FAILED_INTERNAL_ERROR,
                        "Package installation is not supported in Suwayomi AndroidCompat",
                        null);
            } catch (RuntimeException e) {
                // Don't let a misbehaving observer crash the caller.
            }
        }
    }

    @Override
    public int installExistingPackage(String packageName) throws NameNotFoundException {
        getPackageInfo(packageName, 0); // NameNotFoundException if missing
        return PackageManager.INSTALL_FAILED_INTERNAL_ERROR;
    }

    @Override
    public int installExistingPackage(String packageName, int installReason) throws NameNotFoundException {
        return installExistingPackage(packageName);
    }

    @Override
    public int installExistingPackageAsUser(String packageName, int userId) throws NameNotFoundException {
        return installExistingPackage(packageName);
    }

    @Override
    public void verifyPendingInstall(int id, int verificationCode) {

    }

    @Override
    public void extendVerificationTimeout(int id, int verificationCodeAtTimeout, long millisecondsToDelay) {

    }

    @Override
    public void verifyIntentFilter(int verificationId, int verificationCode, List<String> failedDomains) {

    }

    @Override
    public int getIntentVerificationStatusAsUser(String packageName, int userId) {
        return 0;
    }

    @Override
    public boolean updateIntentVerificationStatusAsUser(String packageName, int status, int userId) {
        return false;
    }

    @Override
    public List<IntentFilterVerificationInfo> getIntentFilterVerifications(String packageName) {
        return new ArrayList<>();
    }

    @Override
    public List<IntentFilter> getAllIntentFilters(String packageName) {
        return new ArrayList<>();
    }

    @Override
    public String getDefaultBrowserPackageNameAsUser(int userId) {
        return null;
    }

    @Override
    public boolean setDefaultBrowserPackageNameAsUser(String packageName, int userId) {
        return false;
    }

    @Override
    public void setInstallerPackageName(String targetPackage, String installerPackageName) {

    }

    @Override
    public void setUpdateAvailable(String packageName, boolean updateAvaialble) {

    }

    @Override
    public String getInstallerPackageName(String packageName) {
        return null;
    }

    @Override
    public void freeStorage(String volumeUuid, long freeStorageSize, IntentSender pi) {

    }

    @Override
    public void addPackageToPreferred(String packageName) {

    }

    @Override
    public void removePackageFromPreferred(String packageName) {

    }

    @Override
    public List<PackageInfo> getPreferredPackages(int flags) {
        return new ArrayList<>();
    }

    @Override
    public void addPreferredActivity(IntentFilter filter, int match, ComponentName[] set, ComponentName activity) {

    }

    @Override
    public void replacePreferredActivity(IntentFilter filter, int match, ComponentName[] set, ComponentName activity) {

    }

    @Override
    public void clearPackagePreferredActivities(String packageName) {

    }

    @Override
    public int getPreferredActivities(List<IntentFilter> outFilters, List<ComponentName> outActivities, String packageName) {
        return 0;
    }

    @Override
    public ComponentName getHomeActivities(List<ResolveInfo> outActivities) {
        return null;
    }

    @Override
    public void setComponentEnabledSetting(ComponentName componentName, int newState, int flags) {

    }

    @Override
    public int getComponentEnabledSetting(ComponentName componentName) {
        return 0;
    }

    @Override
    public void setApplicationEnabledSetting(String packageName, int newState, int flags) {

    }

    @Override
    public int getApplicationEnabledSetting(String packageName) {
        return 0;
    }

    @Override
    public void flushPackageRestrictionsAsUser(int userId) {

    }

    @Override
    public boolean setApplicationHiddenSettingAsUser(String packageName, boolean hidden, UserHandle userHandle) {
        return false;
    }

    @Override
    public boolean getApplicationHiddenSettingAsUser(String packageName, UserHandle userHandle) {
        return false;
    }

    @Override
    public boolean isSafeMode() {
        return false;
    }

    @Override
    public void addOnPermissionsChangeListener(OnPermissionsChangedListener listener) {

    }

    @Override
    public void removeOnPermissionsChangeListener(OnPermissionsChangedListener listener) {

    }

    @Override
    public KeySet getKeySetByAlias(String packageName, String alias) {
        return null;
    }

    @Override
    public KeySet getSigningKeySet(String packageName) {
        return null;
    }

    @Override
    public boolean isSignedBy(String packageName, KeySet ks) {
        return false;
    }

    @Override
    public boolean isSignedByExactly(String packageName, KeySet ks) {
        return false;
    }

    @Override
    public String[] setPackagesSuspendedAsUser(String[] packageNames, boolean suspended, int userId) {
        return new String[0];
    }

    @Override
    public boolean isPackageSuspendedForUser(String packageName, int userId) {
        return false;
    }

    @Override
    public int getMoveStatus(int moveId) {
        return 0;
    }

    @Override
    public void registerMoveCallback(MoveCallback callback, Handler handler) {

    }

    @Override
    public void unregisterMoveCallback(MoveCallback callback) {

    }

    @Override
    public boolean isUpgrade() {
        return false;
    }

    @Override
    public PackageInstaller getPackageInstaller() {
        return null;
    }

    @Override
    public void addCrossProfileIntentFilter(IntentFilter filter, int sourceUserId, int targetUserId, int flags) {

    }

    @Override
    public void clearCrossProfileIntentFilters(int sourceUserId) {

    }

    @Override
    public Drawable loadItemIcon(PackageItemInfo itemInfo, ApplicationInfo appInfo) {
        return null;
    }

    @Override
    public Drawable loadUnbadgedItemIcon(PackageItemInfo itemInfo, ApplicationInfo appInfo) {
        return null;
    }

    @Override
    public boolean isPackageAvailable(String packageName) {
        return controller.findPackage(packageName) != null;
    }

    @Override
    public int getInstallReason(String packageName, UserHandle user) {
        return 0;
    }

    @Override
    public boolean canRequestPackageInstalls() {
        return false;
    }

    @Override
    public ComponentName getInstantAppResolverSettingsComponent() {
        return null;
    }

    @Override
    public ComponentName getInstantAppInstallerComponent() {
        return null;
    }

    @Override
    public String getInstantAppAndroidId(String packageName, UserHandle user) {
        return null;
    }

    @Override
    public void registerDexModule(String dexModulePath, DexModuleRegisterCallback callback) {

    }
}
