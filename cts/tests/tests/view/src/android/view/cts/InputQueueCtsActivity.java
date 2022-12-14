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

package android.view.cts;

import android.app.Activity;
import android.os.Bundle;
import android.view.InputQueue;

public class InputQueueCtsActivity extends Activity implements InputQueue.Callback {
    private InputQueue mInputQueue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().takeInputQueue(this);
    }

    @Override
    public void onInputQueueCreated(InputQueue inputQueue) {
        mInputQueue = inputQueue;
    }

    @Override
    public void onInputQueueDestroyed(InputQueue inputQueue) {}

    public InputQueue getInputQueue() {
        return mInputQueue;
    }
}
