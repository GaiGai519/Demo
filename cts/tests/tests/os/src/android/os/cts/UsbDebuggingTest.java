/*
 * Copyright (C) 2013 The Android Open Source Project
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

package android.os.cts;

import android.os.Build;
import android.os.SystemProperties;
import android.platform.test.annotations.RestrictedBuildTest;
import android.test.AndroidTestCase;
import java.io.File;

public class UsbDebuggingTest extends AndroidTestCase {

    @RestrictedBuildTest
    public void testUsbDebugging() {
        if (!SystemProperties.get("ro.build.type").equals("user")) {
            return;
        }

        // Secure USB debugging must be enabled
        assertEquals("1", SystemProperties.get("ro.adb.secure"));

        // Don't ship vendor keys in user build
        if ("user".equals(Build.TYPE)) {
            File keys = new File("/adb_keys");
            assertFalse(keys.exists());
        }
    }

}
