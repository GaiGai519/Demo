/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import java.io.InputStream;

import android.platform.test.annotations.AsbSecurityTest;
import android.security.cts.R;

import androidx.test.runner.AndroidJUnit4;
import org.junit.runner.RunWith;
import org.junit.Test;

@RunWith(AndroidJUnit4.class)
public class BigRleTest extends StsExtraBusinessLogicTestCase {
    /**
     * Verifies that the device does not run OOM decoding a particular RLE encoded BMP.
     *
     * This image reports that its encoded length is over 4 gigs. Prior to fixing issue 33251605,
     * we attempted to allocate space for all the encoded data at once, resulting in OOM.
     */
    @AsbSecurityTest(cveBugId = 33251605)
    @Test
    public void test_android_bug_33251605() {
        InputStream exploitImage = getInstrumentation().getContext().getResources().openRawResource(R.raw.bug_33251605);
        Bitmap bitmap = BitmapFactory.decodeStream(exploitImage);
    }
}
