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

package com.android.cts.verifier.wifiaware;

import android.content.Context;

import com.android.cts.verifier.wifiaware.testcase.DataPathInBandTestCase;

/**
 * Test activity for data-path, passphrase, passive subscribe. Pair with accept any publish
 */
public class DataPathPassphrasePassiveSubscribeAcceptAnyTestActivity extends BaseTestActivity {
    @Override
    protected BaseTestCase getTestCase(Context context) {
        return new DataPathInBandTestCase(context, /* isSecurityOpen */ false,
                /* isPublish */ false, /* isUnsolicited */ true, /* usePmk */ false,
                /* acceptAny */ false, /* forceChannel */false);
    }
}
