/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.view.cts;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;

public class OnBackInvokedDispatcherTestActivity extends Activity {
    private Dialog mDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.onbackinvokeddispatcher_layout);
        mDialog = new Dialog(this, 0);
        mDialog.setContentView(R.layout.onbackinvokeddispatcher_dialog_layout);
        mDialog.show();
    }

    public Dialog getDialog() {
        return mDialog;
    }
}
