/*
 * Copyright (C) 2018 The Android Open Source Project
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
package android.view.cts.surfacevalidator;

import android.content.Context;
import android.graphics.Rect;
import android.widget.FrameLayout;

public interface ISurfaceValidatorTestCase {
    PixelChecker getChecker();

    void start(Context context, FrameLayout parent);

    void end();

    default boolean hasAnimation() {
        return true;
    }

    default Rect getBoundsToCheck(FrameLayout parent) {
        Rect boundsToCheck = new Rect(0, 0, parent.getWidth(), parent.getHeight());
        int[] topLeft = new int[2];
        parent.getLocationOnScreen(topLeft);
        boundsToCheck.offset(topLeft[0], topLeft[1]);
        return  boundsToCheck;
    }

    default void waitForReady() {
        return;
    }
}
