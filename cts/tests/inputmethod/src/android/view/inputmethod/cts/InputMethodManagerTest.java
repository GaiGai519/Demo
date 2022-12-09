/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.view.inputmethod.cts;

import static android.content.Intent.ACTION_CLOSE_SYSTEM_DIALOGS;
import static android.content.Intent.FLAG_RECEIVER_FOREGROUND;
import static android.view.inputmethod.cts.util.TestUtils.waitOnMainUntil;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.platform.test.annotations.AppModeFull;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.view.inputmethod.cts.util.TestActivity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;

import androidx.annotation.NonNull;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class InputMethodManagerTest {
    private static final String MOCK_IME_ID = "com.android.cts.mockime/.MockIme";
    private static final String MOCK_IME_LABEL = "Mock IME";
    private static final String HIDDEN_FROM_PICKER_IME_ID =
            "com.android.cts.hiddenfrompickerime/.HiddenFromPickerIme";
    private static final String HIDDEN_FROM_PICKER_IME_LABEL = "Hidden From Picker IME";
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);
    private Instrumentation mInstrumentation;
    private Context mContext;
    private InputMethodManager mImManager;
    private boolean mNeedsImeReset = false;

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getTargetContext();
        mImManager = mContext.getSystemService(InputMethodManager.class);
    }

    @After
    public void resetImes() {
        if (mNeedsImeReset) {
            runShellCommand("ime reset");
            mNeedsImeReset = false;
        }
    }

    @Test
    public void testIsActive() throws Throwable {
        final AtomicReference<EditText> focusedEditTextRef = new AtomicReference<>();
        final AtomicReference<EditText> nonFocusedEditTextRef = new AtomicReference<>();
        TestActivity.startSync(activity -> {
            final LinearLayout layout = new LinearLayout(activity);
            layout.setOrientation(LinearLayout.VERTICAL);

            final EditText focusedEditText = new EditText(activity);
            layout.addView(focusedEditText);
            focusedEditTextRef.set(focusedEditText);
            focusedEditText.requestFocus();

            final EditText nonFocusedEditText = new EditText(activity);
            layout.addView(nonFocusedEditText);
            nonFocusedEditTextRef.set(nonFocusedEditText);

            return layout;
        });
        waitOnMainUntil(() -> mImManager.isActive(), TIMEOUT);
        assertTrue(mImManager.isAcceptingText());
        assertTrue(mImManager.isActive(focusedEditTextRef.get()));
        assertFalse(mImManager.isActive(nonFocusedEditTextRef.get()));
    }

    @Test
    public void testIsAcceptingText() throws Throwable {
        final AtomicReference<EditText> focusedFakeEditTextRef = new AtomicReference<>();
        TestActivity.startSync(activity -> {
            final LinearLayout layout = new LinearLayout(activity);
            layout.setOrientation(LinearLayout.VERTICAL);

            final EditText focusedFakeEditText = new EditText(activity) {
                @Override
                public InputConnection onCreateInputConnection(EditorInfo info) {
                    super.onCreateInputConnection(info);
                    return null;
                }
            };
            layout.addView(focusedFakeEditText);
            focusedFakeEditTextRef.set(focusedFakeEditText);
            focusedFakeEditText.requestFocus();
            return layout;
        });
        waitOnMainUntil(() -> mImManager.isActive(), TIMEOUT);
        assertTrue(mImManager.isActive(focusedFakeEditTextRef.get()));
        assertFalse("InputMethodManager#isAcceptingText() must return false "
                + "if target View returns null from onCreateInputConnection().",
                mImManager.isAcceptingText());
    }

    @Test
    public void testGetInputMethodList() throws Exception {
        final List<InputMethodInfo> enabledImes = mImManager.getEnabledInputMethodList();
        assertNotNull(enabledImes);
        final List<InputMethodInfo> imes = mImManager.getInputMethodList();
        assertNotNull(imes);

        // Make sure that IMM#getEnabledInputMethodList() is a subset of IMM#getInputMethodList().
        // TODO: Consider moving this to hostside test to test more realistic and useful scenario.
        if (!imes.containsAll(enabledImes)) {
            fail("Enabled IMEs must be a subset of all the IMEs.\n"
                    + "all=" + dumpInputMethodInfoList(imes) + "\n"
                    + "enabled=" + dumpInputMethodInfoList(enabledImes));
        }
    }

    @Test
    public void testGetEnabledInputMethodList() throws Exception {
        enableImes(HIDDEN_FROM_PICKER_IME_ID);
        final List<InputMethodInfo> enabledImes = mImManager.getEnabledInputMethodList();
        assertThat(enabledImes).isNotNull();
        final List<String> enabledImeIds =
                enabledImes.stream().map(InputMethodInfo::getId).collect(Collectors.toList());
        assertThat(enabledImeIds).contains(HIDDEN_FROM_PICKER_IME_ID);
    }

    private static String dumpInputMethodInfoList(@NonNull List<InputMethodInfo> imiList) {
        return "[" + imiList.stream().map(imi -> {
            final StringBuilder sb = new StringBuilder();
            final int subtypeCount = imi.getSubtypeCount();
            sb.append("InputMethodInfo{id=").append(imi.getId())
                    .append(", subtypeCount=").append(subtypeCount)
                    .append(", subtypes=[");
            for (int i = 0; i < subtypeCount; ++i) {
                if (i != 0) {
                    sb.append(",");
                }
                final InputMethodSubtype subtype = imi.getSubtypeAt(i);
                sb.append("{id=0x").append(Integer.toHexString(subtype.hashCode()));
                if (!TextUtils.isEmpty(subtype.getMode())) {
                    sb.append(",mode=").append(subtype.getMode());
                }
                if (!TextUtils.isEmpty(subtype.getLocale())) {
                    sb.append(",locale=").append(subtype.getLocale());
                }
                if (!TextUtils.isEmpty(subtype.getLanguageTag())) {
                    sb.append(",languageTag=").append(subtype.getLanguageTag());
                }
                sb.append("}");
            }
            sb.append("]");
            return sb.toString();
        }).collect(Collectors.joining(", ")) + "]";
    }

    @AppModeFull(reason = "Instant apps cannot rely on ACTION_CLOSE_SYSTEM_DIALOGS")
    @Test
    public void testShowInputMethodPicker() throws Exception {
        assumeTrue(mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_INPUT_METHODS));
        enableImes(MOCK_IME_ID, HIDDEN_FROM_PICKER_IME_ID);

        TestActivity.startSync(activity -> {
            final View view = new View(activity);
            view.setLayoutParams(new LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            return view;
        });

        // Make sure that InputMethodPicker is not shown in the initial state.
        mContext.sendBroadcast(
                new Intent(ACTION_CLOSE_SYSTEM_DIALOGS).setFlags(FLAG_RECEIVER_FOREGROUND));
        waitOnMainUntil(() -> !mImManager.isInputMethodPickerShown(), TIMEOUT,
                "InputMethod picker should be closed");

        // Test InputMethodManager#showInputMethodPicker() works as expected.
        mImManager.showInputMethodPicker();
        waitOnMainUntil(() -> mImManager.isInputMethodPickerShown(), TIMEOUT,
                "InputMethod picker should be shown");

        // UiDevice.getInstance(Instrumentation) may return a cached instance if it's already called
        // in this process and for some unknown reasons it fails to detect MOCK_IME_LABEL.
        // As a quick workaround, here we clear its internal singleton value.
        // TODO(b/230698095): Fix this in UiDevice or stop using UiDevice.
        try {
            final Field field = UiDevice.class.getDeclaredField("sInstance");
            field.setAccessible(true);
            field.set(null, null);
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException
                | IllegalAccessException e) {
            // We don't treat this as an error as it's an implementation detail of UiDevice.
        }

        final UiDevice uiDevice = UiDevice.getInstance(mInstrumentation);
        assertThat(uiDevice.wait(Until.hasObject(By.text(MOCK_IME_LABEL)), TIMEOUT)).isTrue();
        assertThat(uiDevice.findObject(By.text(HIDDEN_FROM_PICKER_IME_LABEL))).isNull();

        // Make sure that InputMethodPicker can be closed with ACTION_CLOSE_SYSTEM_DIALOGS
        mContext.sendBroadcast(
                new Intent(ACTION_CLOSE_SYSTEM_DIALOGS).setFlags(FLAG_RECEIVER_FOREGROUND));
        waitOnMainUntil(() -> !mImManager.isInputMethodPickerShown(), TIMEOUT,
                "InputMethod picker should be closed");
    }

    private void enableImes(String... ids) {
        for (String id : ids) {
            runShellCommand("ime enable " + id);
        }
        mNeedsImeReset = true;
    }
}
