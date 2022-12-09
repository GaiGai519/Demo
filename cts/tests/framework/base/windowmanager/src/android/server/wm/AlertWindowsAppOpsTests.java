/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License
 */

package android.server.wm;

import static android.app.AppOpsManager.MODE_ALLOWED;
import static android.app.AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.app.AppOpsManager;
import android.os.Process;
import android.platform.test.annotations.Presubmit;

import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.AppOpsUtils;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Test whether system alert window properly interacts with app ops.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceTestCases:AlertWindowsAppOpsTests
 */
@Presubmit
public class AlertWindowsAppOpsTests {
    private static final long APP_OP_CHANGE_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(2);

    private static int sPreviousSawAppOp;

    @Rule
    public final ActivityTestRule<AlertWindowsAppOpsTestsActivity> mActivityRule =
            new ActivityTestRule<>(AlertWindowsAppOpsTestsActivity.class);

    @BeforeClass
    public static void grantSystemAlertWindowAccess() throws IOException {
        String packageName = getInstrumentation().getContext().getPackageName();
        sPreviousSawAppOp = AppOpsUtils.getOpMode(packageName, OPSTR_SYSTEM_ALERT_WINDOW);
        AppOpsUtils.setOpMode(packageName, OPSTR_SYSTEM_ALERT_WINDOW, MODE_ALLOWED);
    }

    @AfterClass
    public static void revokeSystemAlertWindowAccess() throws IOException {
        AppOpsUtils.setOpMode(getInstrumentation().getContext().getPackageName(),
                OPSTR_SYSTEM_ALERT_WINDOW, sPreviousSawAppOp);
    }

    @Test
    public void testSystemAlertWindowAppOpsInitiallyAllowed() {
        final String packageName = getInstrumentation().getContext().getPackageName();
        final int uid = Process.myUid();

        final AppOpsManager appOpsManager = getInstrumentation().getContext()
                .getSystemService(AppOpsManager.class);
        final AppOpsManager.OnOpActiveChangedListener listener = mock(
                AppOpsManager.OnOpActiveChangedListener.class);

        // Launch our activity.
        final AlertWindowsAppOpsTestsActivity activity = mActivityRule.getActivity();

        // Start watching for app op
        appOpsManager.startWatchingActive(new String[] { OPSTR_SYSTEM_ALERT_WINDOW },
                getInstrumentation().getContext().getMainExecutor(), listener);

        // Assert the app op is not started
        assertFalse(appOpsManager.isOpActive(OPSTR_SYSTEM_ALERT_WINDOW, uid, packageName));


        // Show a system alert window.
        getInstrumentation().runOnMainSync(activity::showSystemAlertWindow);

        // The app op should start
        verify(listener, timeout(APP_OP_CHANGE_TIMEOUT_MILLIS)
                .only()).onOpActiveChanged(eq(OPSTR_SYSTEM_ALERT_WINDOW),
                eq(uid), eq(packageName), isNull(), eq(true), anyInt(), anyInt());

        // The app op should be reported as started
        assertTrue(appOpsManager.isOpActive(OPSTR_SYSTEM_ALERT_WINDOW,
                uid, packageName));


        // Start with a clean slate
        reset(listener);

        // Hide a system alert window.
        getInstrumentation().runOnMainSync(activity::hideSystemAlertWindow);

        // The app op should finish
        verify(listener, timeout(APP_OP_CHANGE_TIMEOUT_MILLIS)
                .only()).onOpActiveChanged(eq(OPSTR_SYSTEM_ALERT_WINDOW),
                eq(uid), eq(packageName), isNull(), eq(false), anyInt(), anyInt());

        // The app op should be reported as finished
        assertFalse(appOpsManager.isOpActive(OPSTR_SYSTEM_ALERT_WINDOW, uid, packageName));


        // Start with a clean slate
        reset(listener);

        // Stop watching for app op
        appOpsManager.stopWatchingActive(listener);

        // Show a system alert window
        getInstrumentation().runOnMainSync(activity::showSystemAlertWindow);

        // No other callbacks expected
        verify(listener, timeout(APP_OP_CHANGE_TIMEOUT_MILLIS).times(0))
                .onOpActiveChanged(eq(OPSTR_SYSTEM_ALERT_WINDOW),
                        anyInt(), anyString(), anyBoolean());

        // The app op should be reported as started
        assertTrue(appOpsManager.isOpActive(OPSTR_SYSTEM_ALERT_WINDOW, uid, packageName));
    }
}
