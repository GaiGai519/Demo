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

package android.os.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNotNull;

import android.os.PerformanceHintManager;
import android.os.PerformanceHintManager.Session;
import android.os.Process;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.google.common.base.Strings;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PerformanceHintManagerTest {
    private final long DEFAULT_TARGET_NS = 16666666L;
    private PerformanceHintManager mPerformanceHintManager;

    static {
        System.loadLibrary("ctsos_jni");
    }

    @Before
    public void setUp() {
        mPerformanceHintManager =
                InstrumentationRegistry.getInstrumentation().getContext().getSystemService(
                        PerformanceHintManager.class);
    }

    private Session createSession() {
        return mPerformanceHintManager.createHintSession(
                new int[]{Process.myPid()}, DEFAULT_TARGET_NS);
    }

    @Test
    public void testCreateHintSession() {
        Session a = createSession();
        Session b = createSession();
        if (a == null) {
            assertNull(b);
        } else if (b == null) {
            assertNull(a);
        } else {
            assertNotEquals(a, b);
        }
    }

    @Test
    public void testNativeCreateHintSession() {
        final String failureMessage = nativeTestCreateHintSession();
        if (!Strings.isNullOrEmpty(failureMessage)) {
            fail(failureMessage);
        }
    }

    @Test
    public void testGetPreferredUpdateRateNanos() {
        if (createSession() != null) {
            assertTrue(mPerformanceHintManager.getPreferredUpdateRateNanos() > 0);
        } else {
            assertEquals(-1, mPerformanceHintManager.getPreferredUpdateRateNanos());
        }
    }

    @Test
    public void testNativeGetPreferredUpdateRateNanos() {
        final String failureMessage = nativeTestGetPreferredUpdateRateNanos();
        if (!Strings.isNullOrEmpty(failureMessage)) {
            fail(failureMessage);
        }
    }

    @Test
    public void testUpdateTargetWorkDuration() {
        Session s = createSession();
        assumeNotNull(s);
        s.updateTargetWorkDuration(100);
    }

    @Test
    public void testNativeUpdateTargetWorkDuration() {
        final String failureMessage = nativeUpdateTargetWorkDuration();
        if (!Strings.isNullOrEmpty(failureMessage)) {
            fail(failureMessage);
        }
    }

    @Test
    public void testUpdateTargetWorkDurationWithNegativeDuration() {
        Session s = createSession();
        assumeNotNull(s);
        assertThrows(IllegalArgumentException.class, () -> {
            s.updateTargetWorkDuration(-1);
        });
    }

    @Test
    public void testNativeUpdateTargetWorkDurationWithNegativeDuration() {
        final String failureMessage = nativeUpdateTargetWorkDurationWithNegativeDuration();
        if (!Strings.isNullOrEmpty(failureMessage)) {
            fail(failureMessage);
        }
    }

    @Test
    public void testReportActualWorkDuration() {
        Session s = createSession();
        assumeNotNull(s);
        s.updateTargetWorkDuration(100);
        s.reportActualWorkDuration(1);
        s.reportActualWorkDuration(100);
        s.reportActualWorkDuration(1000);
    }

    @Test
    public void testNativeReportActualWorkDuration() {
        final String failureMessage = nativeReportActualWorkDuration();
        if (!Strings.isNullOrEmpty(failureMessage)) {
            fail(failureMessage);
        }
    }

    @Test
    public void testReportActualWorkDurationWithIllegalArgument() {
        Session s = createSession();
        assumeNotNull(s);
        s.updateTargetWorkDuration(100);
        assertThrows(IllegalArgumentException.class, () -> {
            s.reportActualWorkDuration(-1);
        });
    }

    @Test
    public void testNativeReportActualWorkDurationWithIllegalArgument() {
        final String failureMessage = nativeReportActualWorkDurationWithIllegalArgument();
        if (!Strings.isNullOrEmpty(failureMessage)) {
            fail(failureMessage);
        }
    }

    @Test
    public void testCloseHintSession() {
        Session s = createSession();
        assumeNotNull(s);
        s.close();
    }

    private native String nativeTestCreateHintSession();
    private native String nativeTestGetPreferredUpdateRateNanos();
    private native String nativeUpdateTargetWorkDuration();
    private native String nativeUpdateTargetWorkDurationWithNegativeDuration();
    private native String nativeReportActualWorkDuration();
    private native String nativeReportActualWorkDurationWithIllegalArgument();
}
