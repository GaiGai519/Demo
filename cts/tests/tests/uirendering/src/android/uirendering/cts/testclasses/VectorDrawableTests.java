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

package android.uirendering.cts.testclasses;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.VectorDrawable;
import android.uirendering.cts.R;
import android.uirendering.cts.bitmapverifiers.RectVerifier;
import android.uirendering.cts.bitmapverifiers.SamplePointVerifier;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class VectorDrawableTests extends ActivityTestBase {
    @Test
    public void testScaleDown() {
        VectorDrawable vd = (VectorDrawable) getActivity().getResources().getDrawable(
                R.drawable.rectangle, null);
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    canvas.scale(0.5f, 0.5f);
                    vd.setBounds(new Rect(0, 0, 50, 50));
                    vd.draw(canvas);
                })
                .runWithVerifier(
                        new RectVerifier(Color.WHITE, Color.RED, new Rect(0, 0, 25, 25)));
    }

    class VectorDrawableView extends View {
        private VectorDrawable mVd;
        private Rect mVdBounds;

        public VectorDrawableView(Context context) {
            super(context);
            mVd = (VectorDrawable) context.getResources().getDrawable(R.drawable.circle, null);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            mVd.setBounds(mVdBounds);
            mVd.draw(canvas);
        }

        public void setVDSize(Rect vdBounds) {
            mVdBounds = vdBounds;
        }
    }

    /*
     * The following test verifies that VectorDrawable.setBounds invalidates the bitmap cache.
     */
    @Test
    public void testInvalidateCache() {
        final CountDownLatch fence = new CountDownLatch(1);
        createTest()
                .addLayout(R.layout.frame_layout, view -> {
                    FrameLayout root = (FrameLayout) view.findViewById(R.id.frame_layout);
                    root.setBackgroundColor(Color.BLUE);
                    final VectorDrawableView child = new VectorDrawableView(view.getContext());
                    // VectorDrawable is a red circle drawn on top of a blue background.
                    // The first frame has VectorDrawable size set to 1x1 pixels, which deforms
                    // the red circle into a 1x1 red-ish square.
                    // After first draw we grow VectorDrawable bounds from 0x0 to 90x90. If VD cache
                    // is refreshed, then we should see a red circle on top of a blue background.
                    // If VD cache is stale, then VD will upscale the original 1x1 cached image to
                    // 90x90 red-ish square.
                    // At the end we verify the color of top left pixel, which should be a blue
                    // background pixel.
                    child.setVDSize(new Rect(0, 0, 2, 2));

                    root.addView(child, new FrameLayout.LayoutParams(TEST_WIDTH, TEST_HEIGHT,
                              Gravity.TOP | Gravity.LEFT));

                    // Post a new VD size a few frames in, so that the initial draw completes.
                    root.getViewTreeObserver().addOnPreDrawListener(
                            new ViewTreeObserver.OnPreDrawListener() {
                                int mDrawCount = 0;
                                @Override
                                public boolean onPreDraw() {
                                    if (mDrawCount++ == 5) {
                                        child.setVDSize(new Rect(0, 0,
                                                (int) (child.getWidth()),
                                                (int) (child.getHeight())));
                                        child.invalidate();

                                        root.getViewTreeObserver().removeOnPreDrawListener(this);
                                        root.post(fence::countDown);
                                    } else {
                                        root.postInvalidate();
                                    }
                                    return true;
                                }
                            });
                }, true, fence)
                .runWithVerifier(new SamplePointVerifier(
                    new Point[] { new Point(0, 0) },
                    new int[] { 0xff0000ff }
                ));
    }
}

