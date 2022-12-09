/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.security.cts;

import static org.junit.Assume.assumeTrue;
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.AsbSecurityTest;

import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class Bug_183410508 extends SecurityTestCase {
    private static final String TEST_PKG = "android.security.cts.BUG_183410508";
    private static final String TEST_CLASS = TEST_PKG + "." + "DeviceTest";
    private static final String TEST_APP = "BUG-183410508.apk";

    @Before
    public void setUp() throws Exception {
        super.setUp();
        assumeTrue(
                "not an Automotive device",
                getDevice().hasFeature("feature:android.hardware.type.automotive"));
        uninstallPackage(getDevice(), TEST_PKG);
    }

    @Test
    @AsbSecurityTest(cveBugId = 183410508)
    public void testRunDeviceTestsPassesFull() throws Exception {
        installPackage(TEST_APP);

        // Grant permission to draw overlays.
        getDevice().executeShellCommand(
                "pm grant " + TEST_PKG + " android.permission.SYSTEM_ALERT_WINDOW");

        assertTrue(runDeviceTests(TEST_PKG, TEST_CLASS, "testTapjacking"));
    }
}
