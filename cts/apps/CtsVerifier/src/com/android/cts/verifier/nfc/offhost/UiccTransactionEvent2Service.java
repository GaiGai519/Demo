/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.cts.verifier.nfc.offhost;

import android.content.ComponentName;

import com.android.cts.verifier.nfc.hce.HceUtils;
import com.android.cts.verifier.nfc.hce.CommandApdu;

public class UiccTransactionEvent2Service {
    public static final ComponentName COMPONENT =
            new ComponentName("com.android.cts.verifier",
                    UiccTransactionEvent2Service.class.getName());

    public static final CommandApdu[] APDU_COMMAND_SEQUENCE = {
        HceUtils.buildCommandApdu("02" +
                HceUtils.buildSelectApdu(HceUtils.TRANSACTION_EVENT_AID, true).getApdu(), true),
    };

    public static final String[] APDU_RESPOND_SEQUENCE = {
        "02900000",
    };
}
