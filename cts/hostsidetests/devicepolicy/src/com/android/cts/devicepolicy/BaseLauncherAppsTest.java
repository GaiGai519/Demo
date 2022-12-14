/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.cts.devicepolicy;

import com.android.tradefed.log.LogUtil.CLog;

/**
 * Common code for the various LauncherApps tests.
 */
public abstract class BaseLauncherAppsTest extends BaseDevicePolicyTest {

    protected static final String SIMPLE_APP_PKG = "com.android.cts.launcherapps.simpleapp";
    protected static final String SIMPLE_APP_APK = "CtsSimpleApp.apk";
    protected static final String LAUNCHER_TESTS_PKG = "com.android.cts.launchertests";
    protected static final String LAUNCHER_TESTS_CLASS = LAUNCHER_TESTS_PKG + ".LauncherAppsTests";
    protected static final String PARAM_TEST_USER = "testUser";

    protected static final String LAUNCHER_TESTS_APK = "CtsLauncherAppsTests.apk";
    protected static final String LAUNCHER_TESTS_SUPPORT_PKG =
            "com.android.cts.launchertests.support";
    protected static final String LAUNCHER_TESTS_SUPPORT_COMPONENT =
            LAUNCHER_TESTS_SUPPORT_PKG + "/.LauncherCallbackTestsService";
    protected static final String LAUNCHER_TESTS_SUPPORT_APK = "CtsLauncherAppsTestsSupport.apk";

    protected void installTestApps(int userId) throws Exception {
        installAppAsUser(LAUNCHER_TESTS_APK, userId);
        installAppAsUser(LAUNCHER_TESTS_SUPPORT_APK, userId);
    }

    protected void uninstallTestApps() throws Exception {
        getDevice().uninstallPackage(LAUNCHER_TESTS_PKG);
        getDevice().uninstallPackage(LAUNCHER_TESTS_SUPPORT_PKG);
        getDevice().uninstallPackage(SIMPLE_APP_PKG);
    }

    protected void startCallbackService(int userId) throws Exception {
        String command = "am startservice --user " + userId
                + " -a " + LAUNCHER_TESTS_SUPPORT_PKG + ".REGISTER_CALLBACK "
                + LAUNCHER_TESTS_SUPPORT_COMPONENT;
        CLog.d("Output for command " + command + ": " + getDevice().executeShellCommand(command));
    }

    protected boolean isCallbackServiceReady() throws Exception {
        String command = "dumpsys activity services " + LAUNCHER_TESTS_SUPPORT_COMPONENT
                + " | grep 'app=ProcessRecord'";
        String result = getDevice().executeShellCommand(command);
        CLog.d("Check service started by " + command + ": " + result);
        if (result.isEmpty()) {
            return false;
        }
        return true;
    }
}
