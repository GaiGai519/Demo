/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package android.systemui.cts;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class TestActiveTileService extends TestTileService {

    private static final String EXTRA_BAD_PACKAGE = "android.systemui.cts.EXTRA_BAD_PACKAGE";
    private static final String TAG = "TestActiveTileService";

    @Override
    public void onTileAdded() {
        Log.i(TAG, TEST_PREFIX + "onTileAdded");
    }

    public static class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TestActiveTileService.class.getSimpleName(),
                    TEST_PREFIX + "requestListeningState");
            ComponentName componentName;
            boolean useBadPackage = intent.getBooleanExtra(EXTRA_BAD_PACKAGE, false);
            if (useBadPackage) {
                componentName = ComponentName.unflattenFromString("pkg/.cls");
            } else {
                componentName = new ComponentName(context, TestActiveTileService.class);
            }
            try {
                requestListeningState(context, componentName);
            } catch (SecurityException e) {
                Log.i(TAG, TEST_PREFIX + "SecurityException");
            }
        }
    }
}
