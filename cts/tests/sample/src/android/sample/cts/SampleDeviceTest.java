/*
 * Copyright (C) 2014 The Android Open Source Project
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
package android.sample.cts;

import android.sample.SampleDeviceActivity;

import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * A simple compatibility test which tests the SharedPreferences API.
 *
 * This test uses {@link ActivityTestRule} to instrument the
 * {@link android.sample.SampleDeviceActivity}.
 */
@RunWith(AndroidJUnit4.class)
public class SampleDeviceTest {

    private static final String KEY = "foo";

    private static final String VALUE = "bar";

    /**
     * A reference to the activity whose shared preferences are being tested.
     */
    @Rule
    public ActivityTestRule<SampleDeviceActivity> mActivityRule =
        new ActivityTestRule(SampleDeviceActivity.class);

    /**
     * Tests the SharedPreferences API.
     *
     * This inserts the key value pair and assert they can be retrieved. Then it clears the
     * preferences and asserts they can no longer be retrieved.
     *
     * @throws Exception
     */
    @Test
    public void testSharedPreferences() throws Exception {
        // Save the key value pair to the preferences and assert they were saved.
        mActivityRule.getActivity().savePreference(KEY, VALUE);
        Assert.assertEquals("Preferences were not saved", VALUE,
            mActivityRule.getActivity().getPreference(KEY));

        // Clear the shared preferences and assert the data was removed.
        mActivityRule.getActivity().clearPreferences();
        Assert.assertNull("Preferences were not cleared",
            mActivityRule.getActivity().getPreference(KEY));
    }
}
