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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Instrumentation;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.view.FrameMetrics;
import android.view.Window;
import android.widget.ScrollView;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.WidgetTestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class FrameMetricsListenerTest {
    private Instrumentation mInstrumentation;
    private Activity mActivity;
    private float mPreviousDurationScale;

    @Rule
    public ActivityTestRule<MockActivity> mActivityRule =
            new ActivityTestRule<>(MockActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
        mPreviousDurationScale = ValueAnimator.getDurationScale();
        ValueAnimator.setDurationScale(0.0f); // Disable animations during frame metrics tests
    }

    @After
    public void tearDown() {
        // Restore animations to previous animation scale after tests are run
        ValueAnimator.setDurationScale(mPreviousDurationScale);
    }

    private void layout(final int layoutId) throws Throwable {
        mActivityRule.runOnUiThread(() -> mActivity.setContentView(layoutId));
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testReceiveData() throws Throwable {
        layout(R.layout.scrollview_layout);
        final ScrollView scrollView = (ScrollView) mActivity.findViewById(R.id.scroll_view);
        final ArrayList<FrameMetrics> data = new ArrayList<>();
        final Handler handler = new Handler(Looper.getMainLooper());
        final Window myWindow = mActivity.getWindow();
        final Window.OnFrameMetricsAvailableListener listener =
            (Window window, FrameMetrics frameMetrics, int dropCount) -> {
                assertEquals(myWindow, window);
                assertEquals(0, dropCount);
                callGetMetric(frameMetrics);
                data.add(new FrameMetrics(frameMetrics));
            };
        mActivityRule.runOnUiThread(() -> mActivity.getWindow().
                addOnFrameMetricsAvailableListener(listener, handler));

        scrollView.postInvalidate();

        PollingCheck.waitFor(() -> data.size() != 0);

        mActivityRule.runOnUiThread(() -> {
            mActivity.getWindow().removeOnFrameMetricsAvailableListener(listener);
        });

        data.clear();

        // Produce 5 frames and assert no metric listeners were invoked
        for (int i = 0; i < 5; i++) {
            WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, scrollView, null);
        }
        assertEquals(0, data.size());
    }

    @Test
    public void testMultipleListeners() throws Throwable {
        layout(R.layout.scrollview_layout);
        final ScrollView scrollView = (ScrollView) mActivity.findViewById(R.id.scroll_view);
        final ArrayList<FrameMetrics> data1 = new ArrayList<>();
        final Handler handler = new Handler(Looper.getMainLooper());
        final Window myWindow = mActivity.getWindow();

        final Window.OnFrameMetricsAvailableListener frameMetricsListener1 =
                (Window window, FrameMetrics frameMetrics, int dropCount) -> {
                    assertEquals(myWindow, window);
                    assertEquals(0, dropCount);
                    callGetMetric(frameMetrics);
                    data1.add(new FrameMetrics(frameMetrics));
                };
        final ArrayList<FrameMetrics> data2 = new ArrayList<>();
        final Window.OnFrameMetricsAvailableListener frameMetricsListener2 =
                (Window window, FrameMetrics frameMetrics, int dropCount) -> {
                    assertEquals(myWindow, window);
                    assertEquals(0, dropCount);
                    callGetMetric(frameMetrics);
                    data2.add(new FrameMetrics(frameMetrics));
                };
        mActivityRule.runOnUiThread(() -> {
            mActivity.getWindow().addOnFrameMetricsAvailableListener(
                    frameMetricsListener1, handler);
            mActivity.getWindow().addOnFrameMetricsAvailableListener(
                    frameMetricsListener2, handler);
        });

        mInstrumentation.waitForIdleSync();

        mActivityRule.runOnUiThread(() -> scrollView.fling(-100));

        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(() -> data1.size() != 0 && data1.size() == data2.size());

        mActivityRule.runOnUiThread(() -> {
            mActivity.getWindow().removeOnFrameMetricsAvailableListener(frameMetricsListener1);
            mActivity.getWindow().removeOnFrameMetricsAvailableListener(frameMetricsListener2);
        });
    }

    @Test
    public void testDropCount() throws Throwable {
        layout(R.layout.scrollview_layout);
        final ScrollView scrollView = (ScrollView) mActivity.findViewById(R.id.scroll_view);

        final AtomicInteger framesDropped = new AtomicInteger();

        final HandlerThread thread = new HandlerThread("Listener");
        thread.start();
        final Window.OnFrameMetricsAvailableListener frameMetricsListener =
                (Window window, FrameMetrics frameMetrics, int dropCount) -> {
                    SystemClock.sleep(100);
                    callGetMetric(frameMetrics);
                    framesDropped.addAndGet(dropCount);
                };

        mActivityRule.runOnUiThread(() -> mActivity.getWindow().
                addOnFrameMetricsAvailableListener(frameMetricsListener,
                        new Handler(thread.getLooper())));

        mInstrumentation.waitForIdleSync();

        mActivityRule.runOnUiThread(() -> scrollView.fling(-100));

        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(() -> framesDropped.get() > 0);

        mActivityRule.runOnUiThread(() -> mActivity.getWindow().
                removeOnFrameMetricsAvailableListener(frameMetricsListener));
    }

    private void callGetMetric(FrameMetrics frameMetrics) {

        // Perform basic checks on timestamp values.
        long unknownDelay = frameMetrics.getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION);
        long input = frameMetrics.getMetric(FrameMetrics.INPUT_HANDLING_DURATION);
        long animation = frameMetrics.getMetric(FrameMetrics.ANIMATION_DURATION);
        long layoutMeasure = frameMetrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION);
        long draw = frameMetrics.getMetric(FrameMetrics.DRAW_DURATION);
        long sync = frameMetrics.getMetric(FrameMetrics.SYNC_DURATION);
        long commandIssue = frameMetrics.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION);
        long swapBuffers = frameMetrics.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION);
        long gpuDuration = frameMetrics.getMetric(FrameMetrics.GPU_DURATION);
        long deadline = frameMetrics.getMetric(FrameMetrics.DEADLINE);
        long totalDuration = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION);
        long intended_vsync = frameMetrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP);
        long vsync = frameMetrics.getMetric(FrameMetrics.VSYNC_TIMESTAMP);

        assertTrue(unknownDelay > 0);
        assertTrue(input > 0);
        assertTrue(animation > 0);
        assertTrue(layoutMeasure > 0);
        assertTrue(draw > 0);
        assertTrue(sync > 0);
        assertTrue(commandIssue > 0);
        assertTrue(swapBuffers > 0);
        assertTrue(intended_vsync > 0);
        assertTrue(vsync > 0);
        assertTrue(gpuDuration >= 0);
        assertTrue(totalDuration > 0);
        assertTrue(deadline > 0);

        long now = System.nanoTime();
        assertTrue(intended_vsync < now);
        assertTrue(vsync < now);
        assertTrue(vsync >= intended_vsync);

        // swapBuffers and gpuDuration may happen in parallel, so instead of counting both we need
        // to take the longer of the two.
        assertTrue(totalDuration >= unknownDelay + input + animation + layoutMeasure + draw + sync
                + commandIssue + Math.max(gpuDuration, swapBuffers));

        // This is the only boolean metric so far
        final long firstDrawFrameMetric = frameMetrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME);
        assertTrue("First draw frame metric should be boolean but is " + firstDrawFrameMetric,
                (firstDrawFrameMetric == 0) || (firstDrawFrameMetric == 1));
    }
}
