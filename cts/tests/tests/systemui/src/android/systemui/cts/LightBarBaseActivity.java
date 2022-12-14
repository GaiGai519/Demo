/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package android.systemui.cts;

import android.annotation.MainThread;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowInsets;

public class LightBarBaseActivity extends Activity {

    private View mContent;

    @Override
    protected void onCreate(Bundle bundle){
        super.onCreate(bundle);
        mContent = new View(this);
        mContent.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        setContentView(mContent);
    }

    @MainThread
    public WindowInsets getRootWindowInsets() {
        return getWindow().getDecorView().getRootWindowInsets();
    }

    public int getSystemUiVisibility() {
        return mContent.getWindowSystemUiVisibility();
    }

    public int getLeft() {
        return mContent.getLocationOnScreen()[0];
    }

    public int getTop() {
        return mContent.getLocationOnScreen()[1];
    }

    public int getBottom() {
        return mContent.getLocationOnScreen()[1] + mContent.getHeight();
    }

    public int getRight() {
        return mContent.getLocationOnScreen()[0] + mContent.getWidth();
    }

    public int getWidth() {
        return mContent.getWidth();
    }
}
