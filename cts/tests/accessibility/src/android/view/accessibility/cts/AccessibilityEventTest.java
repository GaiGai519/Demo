/*
 * Copyright (C) 2010 The Android Open Source Project
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

package android.view.accessibility.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibility.cts.common.InstrumentedAccessibilityServiceTestRule;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.os.Message;
import android.os.Parcel;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.LocaleSpan;
import android.view.Display;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityRecord;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

/** Class for testing {@link AccessibilityEvent}. */
@Presubmit
@RunWith(AndroidJUnit4.class)
public class AccessibilityEventTest {
    private static final long IDLE_TIMEOUT_MS = 500;
    private static final long DEFAULT_TIMEOUT_MS = 1000;

    // From ViewConfiguration.SEND_RECURRING_ACCESSIBILITY_EVENTS_INTERVAL_MILLIS
    private static final long SEND_RECURRING_ACCESSIBILITY_EVENTS_INTERVAL_MILLIS = 100;

    private EventReportingLinearLayout mParentView;
    private View mChildView;
    private TextView mTextView;
    private String mPackageName;

    private static Instrumentation sInstrumentation;
    private static UiAutomation sUiAutomation;
    private final ActivityTestRule<DummyActivity> mActivityRule =
            new ActivityTestRule<>(DummyActivity.class, false, false);//规则提供单个活动的功能测试
    //故障时辅助功能转储规则
    private final AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();
    //自动化辅助功能服务规则

    private InstrumentedAccessibilityServiceTestRule<SpeakingAccessibilityService>
            mInstrumentedAccessibilityServiceRule =
                    new InstrumentedAccessibilityServiceTestRule<>(
                            SpeakingAccessibilityService.class, false);

    //@Rule的特点是：在一个class中所有的@Test标注过的测试方法都会共享这个Rule，例如定义一个Timeout,所有方法运行都会自动检测是否超时。
    //@Rule的作用域是测试方法，每个测试方法执行时都会调用被注解的Rule
   // 假如你测试代码中需要多个rule，可以使用RuleChain，它还可以控制多个rule的执行顺序
    //RuleChain规则：继承TestRule 可以对多个TestRule进行排序 执行属性 先执行外层
    @Rule
    public final RuleChain mRuleChain =
            RuleChain.outerRule(mActivityRule)
                    .around(mInstrumentedAccessibilityServiceRule)
                    .around(mDumpOnFailureRule);

    //初始化数据  对于每一个测试的方法都要执行一次 9 8+2.6+3.4=14W
    @Before
    public void setUp() throws Throwable {
        final Activity activity = mActivityRule.launchActivity(null);//相当于activity的引用，启动测试中的活动
        mPackageName = activity.getApplicationContext().getPackageName();
        sInstrumentation = InstrumentationRegistry.getInstrumentation();//获得instrumentation对象，获取到该对象后就可以调用其成员函数对应用程序进行控制
        ////获得UiAutomation的一个实例 模拟用户操作和屏幕内容的内省与设备的 UI 交互的类。它依赖于平台可访问性 API
        // 来检查屏幕并在远程视图树上执行一些操作。它还允许注入任意原始输入事件，模拟用户与键盘和触摸设备的交互。
        // 可以将 UiAutomation 视为一种特殊类型
        sUiAutomation = sInstrumentation.getUiAutomation();
        mInstrumentedAccessibilityServiceRule.enableService();//启用测试中的工具化可访问性服务。
        mActivityRule.runOnUiThread(
                () -> {
                    final LinearLayout grandparent = new LinearLayout(activity);
                    activity.setContentView(grandparent);
                    mParentView = new EventReportingLinearLayout(activity);
                    mChildView = new View(activity);
                    mTextView = new TextView(activity);
                    grandparent.addView(mParentView);
                    mParentView.addView(mChildView);
                    mParentView.addView(mTextView);
                });
        sUiAutomation.waitForIdle(IDLE_TIMEOUT_MS, DEFAULT_TIMEOUT_MS);


//        //执行并等待事件
//
//        /**
//         * executeAndWaitForEvent (Runnable command,UiAutomation.AccessibilityEventFilter filter,long timeoutMillis)
//         *执行命令并等待特定的可访问性事件直到给定的等待超时。要检测一系列事件，可以实现一个过滤器，
//         *该过滤器跟踪预期序列的已见事件，并在接收到该序列的最后一个事件后返回 true。
//         * command：Runnable执行的命令
//         * filter：识别预期事件的过滤器
//         *long：以毫秒为单位的等待超时
//         */
//        sUiAutomation.executeAndWaitForEvent(() -> {
//                    try {
//                        mActivityRule.runOnUiThread(() -> {//便捷的执行子线程时对ui进行操作 更新UI操作
//                            final LinearLayout grandparent = new LinearLayout(activity);
//                            activity.setContentView(grandparent);
//                            mParentView = new EventReportingLinearLayout(activity);
//                            mChildView = new View(activity);
//                            mTextView = new TextView(activity);
//                            grandparent.addView(mParentView);
//                            mParentView.addView(mChildView);
//                            mParentView.addView(mTextView);
//                        });
//                    } catch (Throwable e) {
//                        fail(e.toString());
//                    }
//                },
//                // There can be a race where the test Activity gets focus and we start test.
//                // Because we don't specify flagRetrieveInteractiveWindows in this test, until
//                // the Activity gets focus, no events will be delivered from it.
//                // So. this waits for any event from the test activity.
//                accessibilityEvent -> mPackageName.equals(accessibilityEvent.getPackageName()),
//                DEFAULT_TIMEOUT_MS);
//        //idle? 新Event与上一个Event之间的时间间隔大于某一固定的毫秒数，即视为Idle
//        //可见在1000毫秒内，没有出现Idle，也就是说事件操作至少在等待的500毫秒内，没能完成。
//       // waitForIdle(long a,long b)
//        //a：两个事件之间的间隔时间
//        //b: 全局等待的时间
//        sUiAutomation.waitForIdle(IDLE_TIMEOUT_MS, DEFAULT_TIMEOUT_MS);
    }

    private static class EventReportingLinearLayout extends LinearLayout {
        public List<AccessibilityEvent> mReceivedEvents = new ArrayList<AccessibilityEvent>();

        public EventReportingLinearLayout(Context context) {
            super(context);
        }

        @Override
        public boolean requestSendAccessibilityEvent(View child, AccessibilityEvent event) {
            mReceivedEvents.add(AccessibilityEvent.obtain(event));
            return super.requestSendAccessibilityEvent(child, event);
        }
    }

    //Instrumentation是早期Google提供的Android自动化测试工具类，虽然在那时候JUnit也可以对Android进行测试，但是Instrumentation允许你对应用程序做更为复杂的测试，
    // 甚至是框架层面的。通过Instrumentation你可以模拟按键按下、抬起、屏幕点击、滚动等事件。Instrumentation是通过将主程序和测试程序运行在同一个进程来实现这些功能，
    // 你可以把Instrumentation看成一个类似Activity或者Service并且不带界面的组件，在程序运行期间监控你的主程序。

    @Test
    public void testScrollEvent() throws Exception {//测试滚动事件
        //Instrumentation.runOnMainSync:
        sUiAutomation.executeAndWaitForEvent(() -> sInstrumentation.runOnMainSync(//在应用程序的主线程上执行调用，阻塞直到完成
                () -> mChildView.scrollTo(0, 100)), new ScrollEventFilter(1), DEFAULT_TIMEOUT_MS);//为什么是1 ？

        //完整版
        sUiAutomation.executeAndWaitForEvent(new Runnable() {
            @Override
            public void run() {
                sInstrumentation.runOnMainSync(new Runnable() {
                    @Override
                    public void run() {
                        mChildView.scrollTo(0, 100);
                    }
                }
            }
        },new ScrollEventFilter(1), DEFAULT_TIMEOUT_MS);
    }

    @Test
    public void testScrollEventBurstCombined() throws Exception {//测试滚动事件突发组合
        sUiAutomation.executeAndWaitForEvent(
                () -> sInstrumentation.runOnMainSync(
                        () -> {
                            mChildView.scrollTo(0, 100);
                            mChildView.scrollTo(0, 125);
                            mChildView.scrollTo(0, 150);
                            mChildView.scrollTo(0, 175);
                        }),
                new ScrollEventFilter(1),
                DEFAULT_TIMEOUT_MS);
    }

    @Test
    public void testScrollEventsDeliveredInCorrectInterval() throws Exception {//测试在正确的时间间隔内滚动事件
        sUiAutomation.executeAndWaitForEvent(
                () -> {
                    sInstrumentation.runOnMainSync(() -> {
                        mChildView.scrollTo(0, 25);
                        mChildView.scrollTo(0, 50);
                        mChildView.scrollTo(0, 100);
                    });
                    SystemClock.sleep(SEND_RECURRING_ACCESSIBILITY_EVENTS_INTERVAL_MILLIS * 2);
                    sInstrumentation.runOnMainSync(() -> {
                        mChildView.scrollTo(0, 150);
                        mChildView.scrollTo(0, 175);
                    });
                    SystemClock.sleep(SEND_RECURRING_ACCESSIBILITY_EVENTS_INTERVAL_MILLIS / 2);
                    sInstrumentation.runOnMainSync(() -> {
                        mChildView.scrollTo(0, 200);
                    });
                },
                new ScrollEventFilter(2),
                DEFAULT_TIMEOUT_MS);
    }

    class ScrollEventFilter extends AccessibilityEventFilter {
        private int mCount = 0;
        private int mTargetCount;

        ScrollEventFilter(int count) {
            mTargetCount = count;
        }

        public boolean accept(AccessibilityEvent event) {
            if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                mCount += 1;
                mEvents.add(event);
                return mCount >= mTargetCount;
            }
            return false;
        }
    }

    @Test
    public void testScrollEventsClearedOnDetach() throws Throwable {//test滚动事件在分离时清除
        ScrollEventFilter scrollEventFilter = new ScrollEventFilter(1);
        sUiAutomation.executeAndWaitForEvent(
                () -> sInstrumentation.runOnMainSync(
                        () -> {
                            mChildView.scrollTo(0, 25);
                            mChildView.scrollTo(5, 50);
                            mChildView.scrollTo(7, 100);
                        }),
                scrollEventFilter,
                DEFAULT_TIMEOUT_MS);
        mActivityRule.runOnUiThread(
                () -> {
                    mParentView.removeView(mChildView);
                    mParentView.addView(mChildView);
                });
        sUiAutomation.executeAndWaitForEvent(
                () -> sInstrumentation.runOnMainSync(
                        () -> {
                            mChildView.scrollTo(0, 150);
                        }),
                scrollEventFilter,
                DEFAULT_TIMEOUT_MS);
        AccessibilityEvent event = scrollEventFilter.getLastEvent();
        assertEquals(-7, event.getScrollDeltaX());
        assertEquals(50, event.getScrollDeltaY());
    }

    @Test
    public void testScrollEventsCaptureTotalDelta() throws Throwable {
        ScrollEventFilter scrollEventFilter = new ScrollEventFilter(1);
        sUiAutomation.executeAndWaitForEvent(
                () -> sInstrumentation.runOnMainSync(
                        () -> {
                            mChildView.scrollTo(0, 25);
                            mChildView.scrollTo(5, 50);
                            mChildView.scrollTo(7, 100);
                        }),
                scrollEventFilter,
                DEFAULT_TIMEOUT_MS);
        AccessibilityEvent event = scrollEventFilter.getLastEvent();
        assertEquals(7, event.getScrollDeltaX());
        assertEquals(100, event.getScrollDeltaY());
    }

    @Test
    public void testScrollEventsClearDeltaAfterSending() throws Throwable {
        ScrollEventFilter scrollEventFilter = new ScrollEventFilter(2);
        sUiAutomation.executeAndWaitForEvent(
                () -> {
                    sInstrumentation.runOnMainSync(() -> {
                        mChildView.scrollTo(0, 25);
                        mChildView.scrollTo(5, 50);
                        mChildView.scrollTo(7, 100);
                    });
                    SystemClock.sleep(SEND_RECURRING_ACCESSIBILITY_EVENTS_INTERVAL_MILLIS * 2);
                    sInstrumentation.runOnMainSync(() -> {
                        mChildView.scrollTo(0, 25);
                        mChildView.scrollTo(5, 50);
                        mChildView.scrollTo(7, 100);
                        mChildView.scrollTo(0, 150);
                    });
                },
                scrollEventFilter,
                DEFAULT_TIMEOUT_MS);
        AccessibilityEvent event = scrollEventFilter.getLastEvent();
        assertEquals(-7, event.getScrollDeltaX());
        assertEquals(50, event.getScrollDeltaY());
    }

    @Test
    public void testStateEvent() throws Throwable {
        sUiAutomation.executeAndWaitForEvent(
                () -> {
                    sendStateDescriptionChangedEvent(mChildView);
                },
                new StateDescriptionEventFilter(1),
                DEFAULT_TIMEOUT_MS);
    }

    @Test
    public void testStateEventBurstCombined() throws Throwable {
        sUiAutomation.executeAndWaitForEvent(
                () -> {
                    sendStateDescriptionChangedEvent(mChildView);
                    sendStateDescriptionChangedEvent(mChildView);
                    sendStateDescriptionChangedEvent(mChildView);
                    sendStateDescriptionChangedEvent(mChildView);
                },
                new StateDescriptionEventFilter(1),
                DEFAULT_TIMEOUT_MS);
    }

    @Test
    public void testStateEventsDeliveredInCorrectInterval() throws Throwable {
        sUiAutomation.executeAndWaitForEvent(
                () -> {
                    sendStateDescriptionChangedEvent(mChildView);
                    sendStateDescriptionChangedEvent(mChildView);
                    sendStateDescriptionChangedEvent(mChildView);
                    SystemClock.sleep(SEND_RECURRING_ACCESSIBILITY_EVENTS_INTERVAL_MILLIS * 2);
                    sendStateDescriptionChangedEvent(mChildView);
                    sendStateDescriptionChangedEvent(mChildView);
                    SystemClock.sleep(SEND_RECURRING_ACCESSIBILITY_EVENTS_INTERVAL_MILLIS / 2);
                    sendStateDescriptionChangedEvent(mChildView);
                },
                new StateDescriptionEventFilter(2),
                DEFAULT_TIMEOUT_MS);
    }

    @Test
    public void testStateEventsHaveLastEventText() throws Throwable {
        StateDescriptionEventFilter stateDescriptionEventFilter =
                new StateDescriptionEventFilter(1);
        String expectedState = "Second state";
        sUiAutomation.executeAndWaitForEvent(
                () -> {
                    sendStateDescriptionChangedEvent(mChildView, "First state");
                    sendStateDescriptionChangedEvent(mChildView, expectedState);
                },
                stateDescriptionEventFilter,
                DEFAULT_TIMEOUT_MS);
        AccessibilityEvent event = stateDescriptionEventFilter.getLastEvent();
        assertEquals(expectedState, event.getText().get(0));
    }

    class StateDescriptionEventFilter extends AccessibilityEventFilter {
        private int mCount;
        private int mTargetCount;

        StateDescriptionEventFilter(int count) {
            mTargetCount = count;
        }

        public boolean accept(AccessibilityEvent event) {
            if (event.getContentChangeTypes()
                    == AccessibilityEvent.CONTENT_CHANGE_TYPE_STATE_DESCRIPTION) {
                mCount += 1;
                mEvents.add(event);
                return mCount >= mTargetCount;
            }
            return false;
        }
    }
    ;

    private abstract class AccessibilityEventFilter
            implements UiAutomation.AccessibilityEventFilter {
        protected List<AccessibilityEvent> mEvents = new ArrayList<>();

        public abstract boolean accept(AccessibilityEvent event);

        void assertReceivedEventCount(int count) {
            assertEquals(count, mEvents.size());
        }

        AccessibilityEvent getLastEvent() {
            if (mEvents.size() > 0) {
                return mEvents.get(mEvents.size() - 1);
            }
            return null;
        }
    }

    private void sendStateDescriptionChangedEvent(View view) {
        sendStateDescriptionChangedEvent(view, null);
    }

    private void sendStateDescriptionChangedEvent(View view, CharSequence text) {
        AccessibilityEvent event =
                AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
        event.setContentChangeTypes(AccessibilityEvent.CONTENT_CHANGE_TYPE_STATE_DESCRIPTION);
        event.getText().add(text);
        view.sendAccessibilityEventUnchecked(event);
    }

    @Test
    public void setText_unChanged_doNotReceiveEvent() throws Throwable {
        sUiAutomation.executeAndWaitForEvent(
                () -> sInstrumentation.runOnMainSync(() -> mTextView.setText("a")),
                event -> isExpectedChangeType(event, AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT),
                DEFAULT_TIMEOUT_MS);

        assertThrows(
                TimeoutException.class,
                () ->
                        sUiAutomation.executeAndWaitForEvent(
                                () -> {
                                    sInstrumentation.runOnMainSync(
                                            () -> {
                                                mTextView.setText("a");
                                            });
                                },
                                event -> isExpectedSource(event, mTextView),
                                DEFAULT_TIMEOUT_MS));
    }

    @Test
    public void setText_textChanged_receivesTextEvent() throws Throwable {
        sUiAutomation.executeAndWaitForEvent(
                () -> sInstrumentation.runOnMainSync(() -> mTextView.setText("a")),
                event -> isExpectedChangeType(event, AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT),
                DEFAULT_TIMEOUT_MS);

        sUiAutomation.executeAndWaitForEvent(
                () -> {
                    sInstrumentation.runOnMainSync(
                            () -> {
                                mTextView.setText("b");
                            });
                },
                event ->
                        isExpectedSource(event, mTextView)
                                && isExpectedChangeType(
                                        event, AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT),
                DEFAULT_TIMEOUT_MS);
    }

    @Test
    public void setText_parcelableSpanChanged_receivesUndefinedEvent() throws Throwable {
        String text = "a";
        sUiAutomation.executeAndWaitForEvent(
                () -> sInstrumentation.runOnMainSync(() -> mTextView.setText(text)),
                event -> isExpectedChangeType(event, AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT),
                DEFAULT_TIMEOUT_MS);

        sUiAutomation.executeAndWaitForEvent(
                () -> {
                    sInstrumentation.runOnMainSync(
                            () -> {
                                SpannableString spannableString = new SpannableString(text);
                                spannableString.setSpan(new LocaleSpan(Locale.ENGLISH), 0, 1, 0);
                                mTextView.setText(spannableString);
                            });
                },
                event ->
                        isExpectedSource(event, mTextView)
                                && isExpectedChangeType(
                                        event, AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED),
                DEFAULT_TIMEOUT_MS);
    }

    private static boolean isExpectedSource(AccessibilityEvent event, View view) {
        return TextUtils.equals(view.getContext().getPackageName(), event.getPackageName())
                && TextUtils.equals(view.getAccessibilityClassName(), event.getClassName());
    }

    private static boolean isExpectedChangeType(AccessibilityEvent event, int changeType) {
        return (event.getContentChangeTypes() & changeType) == changeType;
    }

    /**
     * Tests whether accessibility events are correctly written and read from a parcel (version 1).
     * 测试是否正确地从包(版本1)中写入和读取易访问性事件。
     */
    @SmallTest
    @Test
    public void testMarshaling() throws Exception {
        // fully populate the event to marshal
        AccessibilityEvent sentEvent = AccessibilityEvent.obtain();
        fullyPopulateAccessibilityEvent(sentEvent);

        // marshal and unmarshal the event
        Parcel parcel = Parcel.obtain();
        sentEvent.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        AccessibilityEvent receivedEvent = AccessibilityEvent.CREATOR.createFromParcel(parcel);

        // make sure all fields properly marshaled
        assertEqualsAccessibilityEvent(sentEvent, receivedEvent);

        parcel.recycle();
    }

    /** Tests if {@link AccessibilityEvent} can be acquired through obtain(). */
    @SmallTest
    @Test
    public void testRecycle() {
        // evaluate that recycle() can be called on an event acquired by obtain()
        AccessibilityEvent.obtain().recycle();
    }

    /** Tests whether the event types are correctly converted to strings. */
    @SmallTest
    @Test
    public void testEventTypeToString() {
        assertEquals(
                "TYPE_NOTIFICATION_STATE_CHANGED",
                AccessibilityEvent.eventTypeToString(
                        AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED));
//        System.out.println("TYPE_NOTIFICATION_STATE_CHANGED11111:"+ AccessibilityEvent.eventTypeToString(
//                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED))
        assertEquals(
                "TYPE_TOUCH_EXPLORATION_GESTURE_END",
                AccessibilityEvent.eventTypeToString(
                        AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END));
        assertEquals(
                "TYPE_TOUCH_EXPLORATION_GESTURE_START",
                AccessibilityEvent.eventTypeToString(
                        AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START));
        assertEquals(
                "TYPE_VIEW_CLICKED",
                AccessibilityEvent.eventTypeToString(AccessibilityEvent.TYPE_VIEW_CLICKED));
        assertEquals(
                "TYPE_VIEW_FOCUSED",
                AccessibilityEvent.eventTypeToString(AccessibilityEvent.TYPE_VIEW_FOCUSED));
        assertEquals(
                "TYPE_VIEW_HOVER_ENTER",
                AccessibilityEvent.eventTypeToString(AccessibilityEvent.TYPE_VIEW_HOVER_ENTER));
        assertEquals(
                "TYPE_VIEW_HOVER_EXIT",
                AccessibilityEvent.eventTypeToString(AccessibilityEvent.TYPE_VIEW_HOVER_EXIT));
        assertEquals(
                "TYPE_VIEW_LONG_CLICKED",
                AccessibilityEvent.eventTypeToString(AccessibilityEvent.TYPE_VIEW_LONG_CLICKED));
        assertEquals(
                "TYPE_VIEW_CONTEXT_CLICKED",
                AccessibilityEvent.eventTypeToString(AccessibilityEvent.TYPE_VIEW_CONTEXT_CLICKED));
        assertEquals(
                "TYPE_VIEW_SCROLLED",
                AccessibilityEvent.eventTypeToString(AccessibilityEvent.TYPE_VIEW_SCROLLED));
        assertEquals(
                "TYPE_VIEW_SELECTED",
                AccessibilityEvent.eventTypeToString(AccessibilityEvent.TYPE_VIEW_SELECTED));
        assertEquals(
                "TYPE_VIEW_TEXT_CHANGED",
                AccessibilityEvent.eventTypeToString(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED));
        assertEquals(
                "TYPE_VIEW_TEXT_SELECTION_CHANGED",
                AccessibilityEvent.eventTypeToString(
                        AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED));
        assertEquals(
                "TYPE_WINDOW_CONTENT_CHANGED",
                AccessibilityEvent.eventTypeToString(
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED));
        assertEquals(
                "TYPE_WINDOW_STATE_CHANGED",
                AccessibilityEvent.eventTypeToString(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED));
    }

    /** Tests whether the event describes its contents consistently. */
    @SmallTest
    @Test
    public void testDescribeContents() {
        AccessibilityEvent event = AccessibilityEvent.obtain();
        assertSame(
                "Accessibility events always return 0 for this method.",
                0,
                event.describeContents());
        fullyPopulateAccessibilityEvent(event);
        assertSame(
                "Accessibility events always return 0 for this method.",
                0,
                event.describeContents());
    }

    /**
     * Tests whether accessibility events are correctly written and read from a parcel (version 2).
     */
    @SmallTest
    @Test
    public void testMarshaling2() {
        // fully populate the event to marshal
        AccessibilityEvent marshaledEvent = AccessibilityEvent.obtain();
        fullyPopulateAccessibilityEvent(marshaledEvent);

        // marshal and unmarshal the event
        Parcel parcel = Parcel.obtain();
        marshaledEvent.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        AccessibilityEvent unmarshaledEvent = AccessibilityEvent.obtain();
        unmarshaledEvent.initFromParcel(parcel);

        // make sure all fields properly marshaled
        assertEqualsAccessibilityEvent(marshaledEvent, unmarshaledEvent);

        parcel.recycle();
    }

    /**
     * While CharSequence is immutable, some classes implementing it are mutable. Make sure they
     * can't change the object by changing the objects backing CharSequence
     */
    @SmallTest
    @Test
    public void testChangeTextAfterSetting_shouldNotAffectEvent() {
        final String originalText = "Cassowary";
        final String newText = "Hornbill";
        AccessibilityEvent event = AccessibilityEvent.obtain();
        StringBuffer updatingString = new StringBuffer(originalText);
        event.setBeforeText(updatingString);
        event.setContentDescription(updatingString);

        updatingString.delete(0, updatingString.length());
        updatingString.append(newText);

        assertTrue(TextUtils.equals(originalText, event.getBeforeText()));
        assertTrue(TextUtils.equals(originalText, event.getContentDescription()));
    }

    @SmallTest
    @Test
    public void testConstructors() {
        final AccessibilityEvent populatedEvent = new AccessibilityEvent();
        fullyPopulateAccessibilityEvent(populatedEvent);
        final AccessibilityEvent event = new AccessibilityEvent(populatedEvent);

        assertEqualsAccessibilityEvent(event, populatedEvent);

        final AccessibilityEvent firstEvent = new AccessibilityEvent();
        firstEvent.setEventType(AccessibilityEvent.TYPE_VIEW_FOCUSED);
        final AccessibilityEvent secondEvent =
                new AccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);

        assertEqualsAccessibilityEvent(firstEvent, secondEvent);
    }

    /**
     * Fully populates the {@link AccessibilityEvent} to marshal.
     *
     * @param sentEvent The event to populate.
     */
    private void fullyPopulateAccessibilityEvent(AccessibilityEvent sentEvent) {
        sentEvent.setAddedCount(1);
        sentEvent.setBeforeText("BeforeText");
        sentEvent.setChecked(true);
        sentEvent.setClassName("foo.bar.baz.Class");
        sentEvent.setContentDescription("ContentDescription");
        sentEvent.setCurrentItemIndex(1);
        sentEvent.setEnabled(true);
        sentEvent.setEventType(AccessibilityEvent.TYPE_VIEW_FOCUSED);
        sentEvent.setEventTime(1000);
        sentEvent.setFromIndex(1);
        sentEvent.setFullScreen(true);
        sentEvent.setItemCount(1);
        sentEvent.setPackageName("foo.bar.baz");
        sentEvent.setParcelableData(Message.obtain(null, 1, 2, 3));
        sentEvent.setPassword(true);
        sentEvent.setRemovedCount(1);
        sentEvent.getText().add("Foo");
        sentEvent.setMaxScrollX(1);
        sentEvent.setMaxScrollY(1);
        sentEvent.setScrollX(1);
        sentEvent.setScrollY(1);
        sentEvent.setScrollDeltaX(3);
        sentEvent.setScrollDeltaY(3);
        sentEvent.setToIndex(1);
        sentEvent.setScrollable(true);
        sentEvent.setAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
        sentEvent.setMovementGranularity(AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE);
        sentEvent.setDisplayId(Display.DEFAULT_DISPLAY);
        sentEvent.setSpeechStateChangeTypes(AccessibilityEvent.SPEECH_STATE_SPEAKING_START);

        AccessibilityRecord record = AccessibilityRecord.obtain();
        AccessibilityRecordTest.fullyPopulateAccessibilityRecord(record);
        sentEvent.appendRecord(record);
    }

    /**
     * Compares all properties of the <code>expectedEvent</code> and the <code>receivedEvent</code>
     * to verify that the received event is the one that is expected.
     */
    private static void assertEqualsAccessibilityEvent(
            AccessibilityEvent expectedEvent, AccessibilityEvent receivedEvent) {
        assertEquals(
                "addedCount has incorrect value",
                expectedEvent.getAddedCount(),
                receivedEvent.getAddedCount());
        assertEquals(
                "beforeText has incorrect value",
                expectedEvent.getBeforeText(),
                receivedEvent.getBeforeText());
        assertEquals(
                "checked has incorrect value",
                expectedEvent.isChecked(),
                receivedEvent.isChecked());
        assertEquals(
                "className has incorrect value",
                expectedEvent.getClassName(),
                receivedEvent.getClassName());
        assertEquals(
                "contentDescription has incorrect value",
                expectedEvent.getContentDescription(),
                receivedEvent.getContentDescription());
        assertEquals(
                "currentItemIndex has incorrect value",
                expectedEvent.getCurrentItemIndex(),
                receivedEvent.getCurrentItemIndex());
        assertEquals(
                "enabled has incorrect value",
                expectedEvent.isEnabled(),
                receivedEvent.isEnabled());
        assertEquals(
                "eventType has incorrect value",
                expectedEvent.getEventType(),
                receivedEvent.getEventType());
        assertEquals(
                "fromIndex has incorrect value",
                expectedEvent.getFromIndex(),
                receivedEvent.getFromIndex());
        assertEquals(
                "fullScreen has incorrect value",
                expectedEvent.isFullScreen(),
                receivedEvent.isFullScreen());
        assertEquals(
                "itemCount has incorrect value",
                expectedEvent.getItemCount(),
                receivedEvent.getItemCount());
        assertEquals(
                "password has incorrect value",
                expectedEvent.isPassword(),
                receivedEvent.isPassword());
        assertEquals(
                "removedCount has incorrect value",
                expectedEvent.getRemovedCount(),
                receivedEvent.getRemovedCount());
        assertSame(
                "maxScrollX has incorrect value",
                expectedEvent.getMaxScrollX(),
                receivedEvent.getMaxScrollX());
        assertSame(
                "maxScrollY has incorrect value",
                expectedEvent.getMaxScrollY(),
                receivedEvent.getMaxScrollY());
        assertSame(
                "scrollX has incorrect value",
                expectedEvent.getScrollX(),
                receivedEvent.getScrollX());
        assertSame(
                "scrollY has incorrect value",
                expectedEvent.getScrollY(),
                receivedEvent.getScrollY());
        assertSame(
                "scrollDeltaX has incorrect value",
                expectedEvent.getScrollDeltaX(),
                receivedEvent.getScrollDeltaX());
        assertSame(
                "scrollDeltaY has incorrect value",
                expectedEvent.getScrollDeltaY(),
                receivedEvent.getScrollDeltaY());
        assertSame(
                "toIndex has incorrect value",
                expectedEvent.getToIndex(),
                receivedEvent.getToIndex());
        assertSame(
                "scrollable has incorrect value",
                expectedEvent.isScrollable(),
                receivedEvent.isScrollable());
        assertSame(
                "granularity has incorrect value",
                expectedEvent.getMovementGranularity(),
                receivedEvent.getMovementGranularity());
        assertSame(
                "action has incorrect value", expectedEvent.getAction(), receivedEvent.getAction());
        assertSame(
                "windowChangeTypes has incorrect value",
                expectedEvent.getWindowChanges(),
                receivedEvent.getWindowChanges());
        assertEquals(
                "speechStateChangeTypes has incorrect value,",
                expectedEvent.getSpeechStateChangeTypes(),
                receivedEvent.getSpeechStateChangeTypes());

        AccessibilityRecordTest.assertEqualsText(expectedEvent.getText(), receivedEvent.getText());
        AccessibilityRecordTest.assertEqualAccessibilityRecord(expectedEvent, receivedEvent);

        assertEqualAppendedRecord(expectedEvent, receivedEvent);
    }

    private static void assertEqualAppendedRecord(
            AccessibilityEvent expectedEvent, AccessibilityEvent receivedEvent) {
        assertEquals(
                "recordCount has incorrect value",
                expectedEvent.getRecordCount(),
                receivedEvent.getRecordCount());
        if (expectedEvent.getRecordCount() != 0 && receivedEvent.getRecordCount() != 0) {
            AccessibilityRecord expectedRecord = expectedEvent.getRecord(0);
            AccessibilityRecord receivedRecord = receivedEvent.getRecord(0);
            AccessibilityRecordTest.assertEqualAccessibilityRecord(expectedRecord, receivedRecord);
        }
    }
}
