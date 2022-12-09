/**
 * Copyright (C) 2022 The Android Open Source Project
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

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.StsExtraBusinessLogicHostTestBase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2020_0448 extends StsExtraBusinessLogicHostTestBase {

    static final String TEST_APP = "CVE-2020-0448.apk";
    static final String TEST_PKG = "android.security.cts.CVE_2020_0448";
    static final String TEST_CLASS = TEST_PKG + "." + "DeviceTest";

    /**
     * b/153995334
     */
    @AsbSecurityTest(cveBugId = 153995334)
    @Test
    public void testPocCVE_2020_0448() throws Exception {
        ITestDevice device = getDevice();
        uninstallPackage(device, TEST_PKG);

        AdbUtils.runCommandLine("input keyevent KEYCODE_WAKEUP", device);
        AdbUtils.runCommandLine("input keyevent KEYCODE_MENU", device);
        AdbUtils.runCommandLine("input keyevent KEYCODE_HOME", device);

        installPackage(TEST_APP);
        Assert.assertTrue(runDeviceTests(TEST_PKG, TEST_CLASS, "testCVE_2020_0448"));
    }
}
