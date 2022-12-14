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

package android.appenumeration.testapp;

import android.accounts.Account;
import android.app.Service;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.os.Bundle;
import android.os.IBinder;

public class MockSyncAdapterService extends Service {

    private static SyncAdapter sInstance;

    @Override
    public IBinder onBind(Intent intent) {
        if (sInstance == null) {
            sInstance = new SyncAdapter(getApplicationContext());
        }
        return sInstance.getSyncAdapterBinder();
    }

    static class SyncAdapter extends AbstractThreadedSyncAdapter {

        public SyncAdapter(Context context) {
            super(context, false /* autoInitialize */);
        }

        @Override
        public void onPerformSync(Account account, Bundle extras, String authority,
                ContentProviderClient provider, SyncResult syncResult) {
        }
    }
}
