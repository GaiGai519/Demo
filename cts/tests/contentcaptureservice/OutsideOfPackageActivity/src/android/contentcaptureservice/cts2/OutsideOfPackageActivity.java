/*
 * Copyright (C) 2019 The Android Open Source Project
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
package android.contentcaptureservice.cts2;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

/**
 * This activity is used to test temporary Content Capture Service interactions with activities
 * outside of its own package. It is intentionally empty.
 */
public class OutsideOfPackageActivity extends Activity {

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        // finish activity after displayed.
        new Handler(Looper.getMainLooper()).post(() -> finish());
    }
}
