/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.admin.cts;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertNotEquals;
import static org.testng.Assert.assertThrows;

import android.app.NotificationManager;
import android.app.NotificationManager.Policy;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.Suppress;
import android.util.Log;

import com.android.bedstead.nene.exceptions.AdbException;
import com.android.bedstead.nene.utils.ShellCommand;
import com.android.bedstead.nene.utils.ShellCommandUtils;

import java.io.ByteArrayInputStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
/**
 * TODO: Make sure DO APIs are not called by PO.
 * Test that exercises {@link DevicePolicyManager}. The test requires that the
 * CtsDeviceAdminReceiver be installed via the CtsDeviceAdmin.apk and be
 * activated via "Settings > Location & security > Select device administrators".
 */
public class DevicePolicyManagerTest extends AndroidTestCase {

    private static final String TAG = DevicePolicyManagerTest.class.getSimpleName();

    private final UserHandle mUser = Process.myUserHandle();

    private DevicePolicyManager mDevicePolicyManager;
    private ComponentName mComponent;
    private boolean mDeviceAdmin;
    private boolean mManagedProfiles;
    private PackageManager mPackageManager;
    private NotificationManager mNotificationManager;
    private boolean mHasSecureLockScreen;

    private static final String TEST_CA_STRING1 =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIICVzCCAgGgAwIBAgIJAMvnLHnnfO/IMA0GCSqGSIb3DQEBBQUAMIGGMQswCQYD\n" +
            "VQQGEwJJTjELMAkGA1UECAwCQVAxDDAKBgNVBAcMA0hZRDEVMBMGA1UECgwMSU1G\n" +
            "TCBQVlQgTFREMRAwDgYDVQQLDAdJTUZMIE9VMRIwEAYDVQQDDAlJTUZMLklORk8x\n" +
            "HzAdBgkqhkiG9w0BCQEWEHJhbWVzaEBpbWZsLmluZm8wHhcNMTMwODI4MDk0NDA5\n" +
            "WhcNMjMwODI2MDk0NDA5WjCBhjELMAkGA1UEBhMCSU4xCzAJBgNVBAgMAkFQMQww\n" +
            "CgYDVQQHDANIWUQxFTATBgNVBAoMDElNRkwgUFZUIExURDEQMA4GA1UECwwHSU1G\n" +
            "TCBPVTESMBAGA1UEAwwJSU1GTC5JTkZPMR8wHQYJKoZIhvcNAQkBFhByYW1lc2hA\n" +
            "aW1mbC5pbmZvMFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAJ738cbTQlNIO7O6nV/f\n" +
            "DJTMvWbPkyHYX8CQ7yXiAzEiZ5bzKJjDJmpRAkUrVinljKns2l6C4++l/5A7pFOO\n" +
            "33kCAwEAAaNQME4wHQYDVR0OBBYEFOdbZP7LaMbgeZYPuds2CeSonmYxMB8GA1Ud\n" +
            "IwQYMBaAFOdbZP7LaMbgeZYPuds2CeSonmYxMAwGA1UdEwQFMAMBAf8wDQYJKoZI\n" +
            "hvcNAQEFBQADQQBdrk6J9koyylMtl/zRfiMAc2zgeC825fgP6421NTxs1rjLs1HG\n" +
            "VcUyQ1/e7WQgOaBHi9TefUJi+4PSVSluOXon\n" +
            "-----END CERTIFICATE-----";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDevicePolicyManager = (DevicePolicyManager)
                mContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
        mComponent = DeviceAdminInfoTest.getReceiverComponent();
        mNotificationManager =
                    (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        mPackageManager = mContext.getPackageManager();
        mDeviceAdmin = mPackageManager.hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN);
        mManagedProfiles = mDeviceAdmin
                && mPackageManager.hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS);
        mHasSecureLockScreen =
                mPackageManager.hasSystemFeature(PackageManager.FEATURE_SECURE_LOCK_SCREEN);
    }

    public void testGetActiveAdmins() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testGetActiveAdmins");
            return;
        }
        List<ComponentName> activeAdmins = mDevicePolicyManager.getActiveAdmins();
        assertFalse(activeAdmins.isEmpty());
        assertTrue(activeAdmins.contains(mComponent));
        assertTrue(mDevicePolicyManager.isAdminActive(mComponent));
    }

    public void testSetGetPreferentialNetworkServiceEnabled() throws Exception {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetGetPreferentialNetworkServiceEnabled");
            return;
        }
        try {
            mDevicePolicyManager.clearProfileOwner(DeviceAdminInfoTest.getProfileOwnerComponent());
            assertThrows(SecurityException.class,
                    () -> mDevicePolicyManager.setPreferentialNetworkServiceEnabled(true));
            assertThrows(SecurityException.class,
                    () -> mDevicePolicyManager.isPreferentialNetworkServiceEnabled());
        }  catch (SecurityException se) {
            Log.w(TAG, "Test is not a profile owner and there is no need to clear.");
        } finally {
            setProfileOwnerAndWaitForSuccess(
                    DeviceAdminInfoTest.getProfileOwnerComponent().flattenToString());
        }

    }

    public void testKeyguardDisabledFeatures() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testKeyguardDisabledFeatures");
            return;
        }
        int originalValue = mDevicePolicyManager.getKeyguardDisabledFeatures(mComponent);
        try {
            // Test all possible combinations which mathematically ends at 2 * LAST - 1
            for (int which = DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_NONE;
                    which < 2 * DevicePolicyManager.KEYGUARD_DISABLE_IRIS; ++which) {
                mDevicePolicyManager.setKeyguardDisabledFeatures(mComponent, which);
                assertEquals(which, mDevicePolicyManager.getKeyguardDisabledFeatures(mComponent));
            }
        } finally {
            mDevicePolicyManager.setKeyguardDisabledFeatures(mComponent, originalValue);
        }
    }

    public void testRequestRemoteBugreport_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testRequestRemoteBugreport_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.requestBugreport(mComponent);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertDeviceOwnerMessage(e.getMessage());
        }
    }

    public void testSetSecurityLoggingEnabled_failIfNotOrganizationOwnedProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetSecurityLoggingEnabled_"
                    + "failIfNotOrganizationOwnedProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.setSecurityLoggingEnabled(mComponent, true);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertOrganizationOwnedProfileOwnerMessage(e.getMessage());
        }
    }

    public void testIsSecurityLoggingEnabled_failIfNotOrganizationOwnedProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testIsSecurityLoggingEnabled_"
                    + "failIfNotOrganizationOwnedProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.isSecurityLoggingEnabled(mComponent);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertOrganizationOwnedProfileOwnerMessage(e.getMessage());
        }
    }

    public void testRetrieveSecurityLogs_failIfNotOrganizationOwnedProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testRetrieveSecurityLogs_failIfNotOrganizationOwnedProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.retrieveSecurityLogs(mComponent);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertOrganizationOwnedProfileOwnerMessage(e.getMessage());
        }
    }

    public void testRetrievePreRebootSecurityLogs_failIfNotOrganizationOwnedProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testRetrievePreRebootSecurityLogs_"
                    + "failIfNotOrganizationOwnedProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.retrievePreRebootSecurityLogs(mComponent);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertOrganizationOwnedProfileOwnerMessage(e.getMessage());
        }
    }

    public void testSetNetworkLoggingEnabled_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetNetworkLoggingEnabled_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.setNetworkLoggingEnabled(mComponent, true);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertDeviceOwnerMessage(e.getMessage());
        }
    }

    public void testIsNetworkLoggingEnabled_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testIsNetworkLoggingEnabled_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.isNetworkLoggingEnabled(mComponent);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertDeviceOwnerOrManageUsersMessage(e.getMessage());
        }
    }

    public void testRetrieveNetworkLogs_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testRetrieveNetworkLogs_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.retrieveNetworkLogs(mComponent, /* batchToken */ 0);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertDeviceOwnerMessage(e.getMessage());
        }
    }
    public void testRemoveUser_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testRemoveUser_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.removeUser(mComponent, Process.myUserHandle());
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertDeviceOwnerMessage(e.getMessage());
        }
    }

    public void testSetApplicationHidden_failIfNotDeviceOrProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetApplicationHidden_failIfNotDeviceOrProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.setApplicationHidden(mComponent, "com.google.anything", true);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testIsApplicationHidden_failIfNotDeviceOrProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testIsApplicationHidden_failIfNotDeviceOrProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.isApplicationHidden(mComponent, "com.google.anything");
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testSetLocationEnabled_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetLocationEnabled_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.setLocationEnabled(mComponent, true);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertDeviceOwnerMessage(e.getMessage());
        }
    }

    public void testSetGlobalSetting_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetGlobalSetting_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.setGlobalSetting(mComponent,
                    Settings.Global.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON, "1");
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertDeviceOwnerMessage(e.getMessage());
        }
    }

    public void testSetSecureSetting_failIfNotDeviceOrProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetSecureSetting_failIfNotDeviceOrProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.setSecureSetting(mComponent,
                    Settings.Secure.SKIP_FIRST_USE_HINTS, "1");
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testSetSecureSetting_failForInstallNonMarketApps() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetSecureSetting_failForInstallNonMarketApps");
            return;
        }
        ComponentName profileOwner = DeviceAdminInfoTest.getProfileOwnerComponent();
        try {
            mDevicePolicyManager.setSecureSetting(profileOwner,
                    Settings.Secure.INSTALL_NON_MARKET_APPS, "0");
            fail("did not throw UnsupportedOperationException");
        } catch (UnsupportedOperationException exc) {
            // Supposed to throw. Pass.
        }
    }

    public void testSetMasterVolumeMuted_failIfNotDeviceOrProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetMasterVolumeMuted_failIfNotDeviceOrProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.setMasterVolumeMuted(mComponent, true);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testIsMasterVolumeMuted_failIfNotDeviceOrProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetMasterVolumeMuted_failIfNotDeviceOrProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.isMasterVolumeMuted(mComponent);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testSetRecommendedGlobalProxy_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetRecommendedGlobalProxy_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.setRecommendedGlobalProxy(mComponent, null);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertDeviceOwnerMessage(e.getMessage());
        }
    }

    public void testSetLockTaskPackages_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetLockTaskPackages_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.setLockTaskPackages(mComponent, new String[] {"package"});
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
        }
    }

    // This test registers itself as DO, so this is no longer testable.  We do a positive test
    // for clearDeviceOwnerApp()
    @Suppress
    public void testClearDeviceOwnerApp_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testClearDeviceOwnerApp_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.clearDeviceOwnerApp("android.admin.app");
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertDeviceOwnerMessage(e.getMessage());
        }
    }

    public void testSwitchUser_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSwitchUser_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.switchUser(mComponent, null);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertDeviceOwnerMessage(e.getMessage());
        }
    }

    public void testCreateAndManageUser_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testCreateAndManageUser_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.createAndManageUser(mComponent, "name", mComponent, null, 0);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertDeviceOwnerMessage(e.getMessage());
        }
    }

    public void testInstallCaCert_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testInstallCaCert_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.installCaCert(mComponent,
                    TEST_CA_STRING1.getBytes());
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testInstallCaCert_failIfNotCertInstaller() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testInstallCaCert_failIfNotCertInstaller");
            return;
        }
        try {
            // Delegated cert installer is identified by using null as the first argument.
            mDevicePolicyManager.installCaCert(null, TEST_CA_STRING1.getBytes());
            fail("did not throw expected SecurityException");
        } catch (SecurityException expected) {
        }
    }

    public void testUninstallCaCert_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testUninstallCaCert_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.uninstallCaCert(mComponent,
                    TEST_CA_STRING1.getBytes());
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testUninstallCaCert_failIfNotCertInstaller() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testUninstallCaCert_failIfNotCertInstaller");
            return;
        }
        try {
            // Delegated cert installer is identified by using null as the first argument.
            mDevicePolicyManager.uninstallCaCert(null, TEST_CA_STRING1.getBytes());
            fail("did not throw expected SecurityException");
        } catch (SecurityException expected) {
        }
    }

    public void testGetInstalledCaCerts_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testGetInstalledCaCerts_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.getInstalledCaCerts(mComponent);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testGetInstalledCaCerts_failIfNotCertInstaller() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testGetInstalledCaCerts_failIfNotCertInstaller");
            return;
        }
        try {
            // Delegated cert installer is identified by using null as the first argument.
            mDevicePolicyManager.getInstalledCaCerts(null);
            fail("did not throw expected SecurityException");
        } catch (SecurityException expected) {
        }
    }

    public void testHasCaCertInstalled_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testHasCaCertInstalled_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.hasCaCertInstalled(mComponent,
                    TEST_CA_STRING1.getBytes());
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testHasCaCertInstalled_failIfNotCertInstaller() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testHasCaCertInstalled_failIfNotCertInstaller");
            return;
        }
        try {
            // Delegated cert installer is identified by using null as the first argument.
            mDevicePolicyManager.hasCaCertInstalled(null, TEST_CA_STRING1.getBytes());
            fail("did not throw expected SecurityException");
        } catch (SecurityException expected) {
        }
    }

    public void testUninstallAllUserCaCerts_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testUninstallAllUserCaCerts_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.uninstallAllUserCaCerts(mComponent);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testUninstallAllUserCaCerts_failIfNotCertInstaller() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testUninstallAllUserCaCerts_failIfNotCertInstaller");
            return;
        }
        try {
            // Delegated cert installer is identified by using null as the first argument.
            mDevicePolicyManager.uninstallAllUserCaCerts(null);
            fail("did not throw expected SecurityException");
        } catch (SecurityException expected) {
        }
    }

    public void testSetScreenCaptureDisabled_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetScreenCaptureDisabled_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.setScreenCaptureDisabled(mComponent, true);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testSetAutoTimeRequired_failIfNotDeviceOrProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetAutoTimeRequired_failIfNotDeviceOrProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.setAutoTimeRequired(mComponent, true);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testAddPersistentPreferredActivity_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testAddPersistentPreferredActivity_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.addPersistentPreferredActivity(mComponent,
                    new IntentFilter(Intent.ACTION_MAIN),
                    new ComponentName("android.admin.cts", "dummy"));
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testClearPackagePersistentPreferredActivities_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testClearPackagePersistentPreferredActivities_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.clearPackagePersistentPreferredActivities(mComponent,
                    "android.admin.cts");
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testSetApplicationRestrictions_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetApplicationRestrictions_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.setApplicationRestrictions(mComponent,
                    "android.admin.cts", null);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testAddUserRestriction_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testAddUserRestriction_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.addUserRestriction(mComponent,
                    UserManager.DISALLOW_SMS);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testSetAccountManagementDisabled_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetAccountManagementDisabled_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.setAccountManagementDisabled(mComponent,
                    "dummy", true);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testSetRestrictionsProvider_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetRestrictionsProvider_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.setRestrictionsProvider(mComponent,
                    new ComponentName("android.admin.cts", "dummy"));
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testSetPermittedAccessibilityServices_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetPermittedAccessibilityServices_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.setPermittedAccessibilityServices(mComponent, null);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testSetCrossProfileContactsSearchDisabled_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetCrossProfileContactsSearchDisabled_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.setCrossProfileContactsSearchDisabled(mComponent, true);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testSetBluetoothContactSharingDisabled_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetBluetoothContactSharingDisabled_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.setBluetoothContactSharingDisabled(mComponent, true);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testSetPermittedInputMethods_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetPermittedInputMethods_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.setPermittedInputMethods(mComponent, null);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    /**
     * Test whether the version of the pre-installed launcher is at least L. This is needed for
     * managed profile support.
     */
    public void testLauncherVersionAtLeastL() throws Exception {
        if (!mManagedProfiles) {
            return;
        }

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> resolveInfos = mPackageManager.queryIntentActivities(intent,
                0 /* default flags */);
        assertFalse("No launcher present", resolveInfos.isEmpty());

        for (ResolveInfo resolveInfo : resolveInfos) {
            ApplicationInfo launcherAppInfo = mPackageManager.getApplicationInfo(
                    resolveInfo.activityInfo.packageName, 0 /* default flags */);
            if ((launcherAppInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    launcherAppInfo.targetSdkVersion >= Build.VERSION_CODES.LOLLIPOP) {
                return;
            }
        }
        fail("No system launcher with version L+ present present on device.");
    }

    private void assertDeviceOwnerMessage(String message) {
        Log.d(TAG, "assertDeviceOwnerMessage(): " + message);
        boolean ok = message.contains("does not own the device")
                || message.contains("can only be called by the device owner")
                || message.contains("Calling identity is not authorized");
        //TODO(b/205178429): work-around as test is always run on current user
        if (!ok && UserManager.isHeadlessSystemUserMode() && !mUser.isSystem()) {
            ok = message.contains("was called from non-system user");
        }

        assertTrue("message is: "+ message, ok);
    }

    private void assertOrganizationOwnedProfileOwnerMessage(String message) {
        assertTrue("message is: " + message, message.contains(
                "is not the profile owner on organization-owned device")
                || message.contains("Calling identity is not authorized"));
    }

    private void assertDeviceOwnerOrManageUsersMessage(String message) {
        assertTrue("message is: "+ message, message.contains("does not own the device")
                || message.contains("can only be called by the device owner")
                || (message.startsWith("Neither user ") && message.endsWith(
                        " nor current process has android.permission.MANAGE_USERS."))
                || message.contains("Calling identity is not authorized"));
    }

    private void assertProfileOwnerMessage(String message) {
        assertTrue("message is: "+ message, message.contains("does not own the profile")
                || message.contains("is not profile owner")
                || message.contains("Calling identity is not authorized"));
    }

    public void testSetDelegatedCertInstaller_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetDelegatedCertInstaller_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.setCertInstallerPackage(mComponent, "com.test.package");
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testGetDelegatedCertInstaller_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testGetDelegatedCertInstaller_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.getCertInstallerPackage(mComponent);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testSetSystemUpdatePolicy_failIfNotOrganizationOwnedProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetSystemUpdatePolicy_failIfNotOrganizationOwnedProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.setSystemUpdatePolicy(mComponent, null);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertOrganizationOwnedProfileOwnerMessage(e.getMessage());
        }
    }

    public void testReboot_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testReboot_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.reboot(mComponent);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertDeviceOwnerMessage(e.getMessage());
        }
    }

    public void testSetBackupServiceEnabled_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetBackupServiceEnabled");
            return;
        }
        try {
            mDevicePolicyManager.setBackupServiceEnabled(mComponent, false);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testIsBackupServiceEnabled_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testIsBackupServiceEnabled");
            return;
        }
        try {
            mDevicePolicyManager.isBackupServiceEnabled(mComponent);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testCreateAdminSupportIntent_returnNullIfRestrictionIsNotSet() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testCreateAdminSupportIntent");
            return;
        }
        Intent intent = mDevicePolicyManager.createAdminSupportIntent(
                DevicePolicyManager.POLICY_DISABLE_CAMERA);
        assertNull(intent);
        intent = mDevicePolicyManager.createAdminSupportIntent(UserManager.DISALLOW_ADJUST_VOLUME);
        assertNull(intent);
    }

    public void testSetResetPasswordToken_failIfNotDeviceOrProfileOwner() {
        if (!mDeviceAdmin || !mHasSecureLockScreen) {
            Log.w(TAG, "Skipping testSetResetPasswordToken_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.setResetPasswordToken(mComponent, new byte[32]);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testClearResetPasswordToken_failIfNotDeviceOrProfileOwner() {
        if (!mDeviceAdmin || !mHasSecureLockScreen) {
            Log.w(TAG, "Skipping testClearResetPasswordToken_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.clearResetPasswordToken(mComponent);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testIsResetPasswordTokenActive_failIfNotDeviceOrProfileOwner() {
        if (!mDeviceAdmin || !mHasSecureLockScreen) {
            Log.w(TAG, "Skipping testIsResetPasswordTokenActive_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.isResetPasswordTokenActive(mComponent);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testResetPasswordWithToken_failIfNotDeviceOrProfileOwner() {
        if (!mDeviceAdmin || !mHasSecureLockScreen) {
            Log.w(TAG, "Skipping testResetPasswordWithToken_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.resetPasswordWithToken(mComponent, "1234", new byte[32], 0);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testIsUsingUnifiedPassword_failIfNotProfileOwner() {
        if (!mDeviceAdmin || !mHasSecureLockScreen) {
            Log.w(TAG, "Skipping testIsUsingUnifiedPassword_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.isUsingUnifiedPassword(mComponent);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testGenerateKeyPair_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testGenerateKeyPair_failIfNotProfileOwner");
            return;
        }
        try {
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                    "gen-should-fail",
                    KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                    .setKeySize(2048)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PSS,
                        KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .build();

            mDevicePolicyManager.generateKeyPair(mComponent, "RSA", spec, 0);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testSetKeyPairCertificate_failIfNotProfileOwner() throws CertificateException {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetKeyPairCertificate_failIfNotProfileOwner");
            return;
        }
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Certificate cert  = cf.generateCertificate(
                    new ByteArrayInputStream(TEST_CA_STRING1.getBytes()));
            List<Certificate> certs = new ArrayList();
            certs.add(cert);
            mDevicePolicyManager.setKeyPairCertificate(mComponent, "set-should-fail", certs, true);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testNotificationPolicyAccess() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testNotificationPolicyAccess_failIfNotProfileOwner");
            return;
        }
        NotificationManager.Policy origPolicy = mNotificationManager.getNotificationPolicy();
        try {
            assertTrue("Notification policy access was not granted ",
                    mNotificationManager.isNotificationPolicyAccessGranted());

            // Clear out the old policy
            mNotificationManager.setNotificationPolicy(new Policy(0, 0, 0, -1));

            Policy expected = new Policy(Policy.PRIORITY_CATEGORY_CALLS,
                    Policy.PRIORITY_SENDERS_STARRED,
                    Policy.PRIORITY_SENDERS_STARRED,
                    Policy.SUPPRESSED_EFFECT_STATUS_BAR);

            assertNotEquals(mNotificationManager.getNotificationPolicy(), expected);

            mNotificationManager.setNotificationPolicy(expected);

            NotificationManager.Policy actual = mNotificationManager.getNotificationPolicy();
            assertTrue((actual.priorityCategories & Policy.PRIORITY_CATEGORY_CALLS) != 0);
            assertEquals(expected.priorityCallSenders, actual.priorityCallSenders);
            assertEquals(expected.priorityMessageSenders, actual.priorityMessageSenders);
            assertEquals(expected.suppressedVisualEffects, actual.suppressedVisualEffects);
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        } finally {
            mNotificationManager.setNotificationPolicy(origPolicy);
        }
    }

    private void setInterruptionFilter(int interruptionFilter) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                latch.countDown();
            }
        };

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED);
        mContext.registerReceiver(receiver, intentFilter);

        try {
            mNotificationManager.setInterruptionFilter(interruptionFilter);
            latch.await(5, TimeUnit.SECONDS);
            assertEquals(mNotificationManager.getCurrentInterruptionFilter(), interruptionFilter);
        } finally {
            mContext.unregisterReceiver(receiver);
        }
    }

    public void testSetInterruptionFilter() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testNotificationPolicyAccess_failIfNotProfileOwner");
            return;
        }

        try {
            setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);
            setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
        } catch (Exception tolerated) {
            assertProfileOwnerMessage(tolerated.getMessage());
        }
    }

    public void testSetStorageEncryption_noAdmin() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetStorageEncryption_noAdmin");
            return;
        }
        final ComponentName notAdmin = new ComponentName("com.test.foo", ".bar");
        //TODO(b/205178429): work-around as test is always run on current user
        if (UserManager.isHeadlessSystemUserMode() && !mUser.isSystem()) {
            assertWithMessage("setStorageEncryption(%s, true) on user %s", notAdmin, mUser)
                    .that(mDevicePolicyManager.setStorageEncryption(notAdmin, true))
                    .isEqualTo(DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED);
            assertWithMessage("setStorageEncryption(%s, false) on user %s", notAdmin, mUser)
                    .that(mDevicePolicyManager.setStorageEncryption(notAdmin, false))
                    .isEqualTo(DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED);
        } else {
            assertThrows(SecurityException.class,
                () -> mDevicePolicyManager.setStorageEncryption(notAdmin, true));
            assertThrows(SecurityException.class,
                () -> mDevicePolicyManager.setStorageEncryption(notAdmin, false));
        }
    }

    public void testCrossProfileCalendar_failIfNotProfileOwner() {
        final String TEST_PACKAGE_NAME = "test.package.name";
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testCrossProfileCalendar_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.setCrossProfileCalendarPackages(mComponent,
                    Collections.singleton(TEST_PACKAGE_NAME));
            fail("setCrossProfileCalendarPackages did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
        try {
            mDevicePolicyManager.getCrossProfileCalendarPackages(mComponent);
            fail("getCrossProfileCalendarPackages did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testSetNearbyNotificationStreamingPolicy_failIfNotDeviceOrProfileOwner()
            throws Exception {
        if (!mDeviceAdmin) {
            String message =
                    "Skipping"
                        + " testSetNearbyNotificationStreamingPolicy_failIfNotDeviceOrProfileOwner";
            Log.w(TAG, message);
            return;
        }
        try {
            tryClearProfileOwner();
            assertThrows(
                    SecurityException.class,
                    () ->
                            mDevicePolicyManager.setNearbyNotificationStreamingPolicy(
                                    DevicePolicyManager.NEARBY_STREAMING_ENABLED));
        } finally {
            setProfileOwnerAndWaitForSuccess(
                    DeviceAdminInfoTest.getProfileOwnerComponent().flattenToString());
        }
    }

    public void testGetNearbyNotificationStreamingPolicy_failIfNotDeviceOrProfileOwner()
            throws Exception {
        if (!mDeviceAdmin) {
            String message =
                    "Skipping"
                        + " testGetNearbyNotificationStreamingPolicy_failIfNotDeviceOrProfileOwner";
            Log.w(TAG, message);
            return;
        }
        try {
            tryClearProfileOwner();
            assertThrows(
                    SecurityException.class,
                    () -> mDevicePolicyManager.getNearbyNotificationStreamingPolicy());
        } finally {
            setProfileOwnerAndWaitForSuccess(
                    DeviceAdminInfoTest.getProfileOwnerComponent().flattenToString());
        }
    }

    public void testSetNearbyAppStreamingPolicy_failIfNotDeviceOrProfileOwner() throws Exception {
        if (!mDeviceAdmin) {
            String message =
                    "Skipping testSetNearbyAppStreamingPolicy_failIfNotDeviceOrProfileOwner";
            Log.w(TAG, message);
            return;
        }
        try {
            tryClearProfileOwner();
            assertThrows(
                    SecurityException.class,
                    () ->
                            mDevicePolicyManager.setNearbyAppStreamingPolicy(
                                    DevicePolicyManager.NEARBY_STREAMING_ENABLED));
        } finally {
            setProfileOwnerAndWaitForSuccess(
                    DeviceAdminInfoTest.getProfileOwnerComponent().flattenToString());
        }
    }

    public void testGetNearbyAppStreamingPolicy_failIfNotDeviceOrProfileOwner() throws Exception {
        if (!mDeviceAdmin) {
            String message =
                    "Skipping testGetNearbyAppStreamingPolicy_failIfNotDeviceOrProfileOwner";
            Log.w(TAG, message);
            return;
        }
        try {
            tryClearProfileOwner();
            assertThrows(
                    SecurityException.class,
                    () -> mDevicePolicyManager.getNearbyAppStreamingPolicy());
        } finally {
            setProfileOwnerAndWaitForSuccess(
                    DeviceAdminInfoTest.getProfileOwnerComponent().flattenToString());
        }
    }

    private void setProfileOwnerAndWaitForSuccess(String componentName)
            throws InterruptedException, AdbException {
        ShellCommand.builder("dpm set-profile-owner")
            .addOperand("--user cur")
            .addOperand(componentName)
            .validate(ShellCommandUtils::startsWithSuccess)
            .executeUntilValid();
    }

    private void tryClearProfileOwner() {
        try {
            mDevicePolicyManager.clearProfileOwner(DeviceAdminInfoTest.getProfileOwnerComponent());
        } catch (SecurityException se) {
            Log.w(TAG, "Test is not a profile owner and there is no need to clear.");
        }
    }
}
