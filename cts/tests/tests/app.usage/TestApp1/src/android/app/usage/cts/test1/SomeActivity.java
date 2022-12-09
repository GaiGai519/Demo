/**
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package android.app.usage.cts.test1;

import static android.content.Intent.EXTRA_REMOTE_CALLBACK;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteCallback;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.Nullable;

public final class SomeActivity extends Activity {
    private static final String TAG = "SomeActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "onCreate(): " + getIntent());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onResume() {
        super.onResume();

        final Intent intent = getIntent();
        Log.d(TAG, "onResume(): " + intent);
        if (intent.hasExtra(EXTRA_REMOTE_CALLBACK)) {
            final RemoteCallback remoteCallback = intent.getParcelableExtra(EXTRA_REMOTE_CALLBACK);
            remoteCallback.sendResult(null);
        }
    }
}
