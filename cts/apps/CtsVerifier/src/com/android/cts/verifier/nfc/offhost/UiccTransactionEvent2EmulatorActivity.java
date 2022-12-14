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

import android.content.Context;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.nfc.hce.HceUtils;

public class UiccTransactionEvent2EmulatorActivity extends PassFailButtons.Activity {
    static final String TAG = "UiccTransactionEvent2EmulatorActivity";

    TextView mTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.pass_fail_text);
        setPassFailButtonClickListeners();
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
            getPassButton().setEnabled(false);
        } else {
            getPassButton().setEnabled(true);
        }

        mTextView = (TextView) findViewById(R.id.text);
        mTextView.setTextSize(12.0f);
        mTextView.setText(R.string.nfc_offhost_uicc_transaction_event_emulator_help);

        initProcess();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        setContentView(R.layout.pass_fail_text);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);

        mTextView = (TextView) findViewById(R.id.text);
        mTextView.setTextSize(12.0f);

        setIntent(intent);
        initProcess();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    public static Intent buildReaderIntent(Context context) {
        Intent readerIntent = new Intent(context, SimpleOffhostReaderActivity.class);
        readerIntent.putExtra(SimpleOffhostReaderActivity.EXTRA_APDUS,
                UiccTransactionEvent2Service.APDU_COMMAND_SEQUENCE);
        readerIntent.putExtra(SimpleOffhostReaderActivity.EXTRA_RESPONSES,
                UiccTransactionEvent2Service.APDU_RESPOND_SEQUENCE);
        readerIntent.putExtra(SimpleOffhostReaderActivity.EXTRA_LABEL,
                context.getString(R.string.nfc_offhost_uicc_transaction_event2_reader));
        readerIntent.putExtra(SimpleOffhostReaderActivity.EXTRA_DESELECT, true);
        return readerIntent;
    }

    private void initProcess() {
        Bundle bundle = getIntent().getExtras();
        if (bundle != null && getIntent().getAction() != null) {
            byte[] transactionData = bundle.getByteArray(NfcAdapter.EXTRA_DATA);
            if (transactionData != null) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mTextView.setText("Pass - NFC Action:" + getIntent().getAction() + " uri:" + getIntent().getDataString()
                            + " data:" + HceUtils.getHexBytes(null, transactionData));
                        getPassButton().setEnabled(true);
                    }
                });
            } else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mTextView.setText("Fail - Action:" + getIntent().getAction() + " uri:" + getIntent().getDataString()
                            + " data: null");
                        getPassButton().setEnabled(false);
                    }
                });
            }
        }
    }
}
