/*
 * Copyright (C) 2015 The Android Open Source Project
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
package android.media.misc.cts;

import android.content.ComponentName;
import android.media.browse.MediaBrowser;
import android.media.browse.MediaBrowser.MediaItem;
import android.media.cts.NonMediaMainlineTest;
import android.os.Bundle;
import android.test.InstrumentationTestCase;

import com.android.compatibility.common.util.PollingCheck;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test {@link android.media.browse.MediaBrowser}.
 */
@NonMediaMainlineTest
public class MediaBrowserTest extends InstrumentationTestCase {
    // The maximum time to wait for an operation.
    private static final long TIME_OUT_MS = 3000L;

    /**
     * To check {@link MediaBrowser#unsubscribe} works properly,
     * we notify to the browser after the unsubscription that the media items have changed.
     * Then {@link MediaBrowser.SubscriptionCallback#onChildrenLoaded} should not be called.
     *
     * The measured time from calling {@link StubMediaBrowserService#notifyChildrenChanged}
     * to {@link MediaBrowser.SubscriptionCallback#onChildrenLoaded} being called is about 50ms.
     * So we make the thread sleep for 100ms to properly check that the callback is not called.
     */
    private static final long SLEEP_MS = 100L;
    private static final ComponentName TEST_BROWSER_SERVICE = new ComponentName(
            "android.media.misc.cts", "android.media.misc.cts.StubMediaBrowserService");
    private static final ComponentName TEST_INVALID_BROWSER_SERVICE = new ComponentName(
            "invalid.package", "invalid.ServiceClassName");
    private final StubConnectionCallback mConnectionCallback = new StubConnectionCallback();
    private final StubSubscriptionCallback mSubscriptionCallback = new StubSubscriptionCallback();
    private final StubItemCallback mItemCallback = new StubItemCallback();

    private MediaBrowser mMediaBrowser;

    @Override
    public void tearDown() {
        if (mMediaBrowser != null) {
            try {
                disconnectMediaBrowser();
            } catch (Throwable t) {
                // Ignore.
            }
            mMediaBrowser = null;
        }
    }

    public void testMediaBrowser() throws Throwable {
        resetCallbacks();
        createMediaBrowser(TEST_BROWSER_SERVICE);
        runOnMainThread(() -> {
            assertEquals(false, mMediaBrowser.isConnected());
        });

        connectMediaBrowserService();
        runOnMainThread(() -> {
            assertEquals(true, mMediaBrowser.isConnected());
        });

        runOnMainThread(() -> {
            assertEquals(TEST_BROWSER_SERVICE, mMediaBrowser.getServiceComponent());
            assertEquals(StubMediaBrowserService.MEDIA_ID_ROOT, mMediaBrowser.getRoot());
            assertEquals(StubMediaBrowserService.EXTRAS_VALUE,
                    mMediaBrowser.getExtras().getString(StubMediaBrowserService.EXTRAS_KEY));
            assertEquals(StubMediaBrowserService.sSession.getSessionToken(),
                    mMediaBrowser.getSessionToken());
        });

        disconnectMediaBrowser();
        runOnMainThread(() -> {
            new PollingCheck(TIME_OUT_MS) {
                @Override
                protected boolean check() {
                    return !mMediaBrowser.isConnected();
                }
            }.run();
        });
    }

    public void testThrowingISEWhileNotConnected() throws Throwable {
        resetCallbacks();
        createMediaBrowser(TEST_BROWSER_SERVICE);
        runOnMainThread(() -> {
            assertEquals(false, mMediaBrowser.isConnected());
        });

        runOnMainThread(() -> {
            try {
                mMediaBrowser.getExtras();
                fail();
            } catch (IllegalStateException e) {
                // Expected
            }

            try {
                mMediaBrowser.getRoot();
                fail();
            } catch (IllegalStateException e) {
                // Expected
            }

            try {
                mMediaBrowser.getServiceComponent();
                fail();
            } catch (IllegalStateException e) {
                // Expected
            }

            try {
                mMediaBrowser.getSessionToken();
                fail();
            } catch (IllegalStateException e) {
                // Expected
            }
        });
    }

    public void testConnectTwice() throws Throwable {
        resetCallbacks();
        createMediaBrowser(TEST_BROWSER_SERVICE);
        connectMediaBrowserService();
        runOnMainThread(() -> {
            try {
                mMediaBrowser.connect();
                fail();
            } catch (IllegalStateException e) {
                // expected
            }
        });
    }

    public void testConnectionFailed() throws Throwable {
        resetCallbacks();
        createMediaBrowser(TEST_INVALID_BROWSER_SERVICE);

        runOnMainThread(() -> {
            mMediaBrowser.connect();
        });

        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return mConnectionCallback.mConnectionFailedCount > 0
                        && mConnectionCallback.mConnectedCount == 0
                        && mConnectionCallback.mConnectionSuspendedCount == 0;
            }
        }.run();
    }

    public void testReconnection() throws Throwable {
        createMediaBrowser(TEST_BROWSER_SERVICE);

        runOnMainThread(() -> {
            // Reconnect before the first connection was established.
            mMediaBrowser.connect();
            mMediaBrowser.disconnect();
        });
        resetCallbacks();
        connectMediaBrowserService();

        // Test subscribe.
        resetCallbacks();
        runOnMainThread(() -> {
            mMediaBrowser.subscribe(StubMediaBrowserService.MEDIA_ID_ROOT, mSubscriptionCallback);
        });
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return mSubscriptionCallback.mChildrenLoadedCount > 0;
            }
        }.run();

        // Test getItem.
        resetCallbacks();
        runOnMainThread(() -> {
            mMediaBrowser.getItem(StubMediaBrowserService.MEDIA_ID_CHILDREN[0], mItemCallback);
        });
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return mItemCallback.mLastMediaItem != null;
            }
        }.run();

        // Reconnect after connection was established.
        disconnectMediaBrowser();
        resetCallbacks();
        connectMediaBrowserService();

        // Test getItem.
        resetCallbacks();
        runOnMainThread(() -> {
            mMediaBrowser.getItem(StubMediaBrowserService.MEDIA_ID_CHILDREN[0], mItemCallback);
        });
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return mItemCallback.mLastMediaItem != null;
            }
        }.run();
    }

    public void testConnectionCallbackNotCalledAfterDisconnect() throws Throwable {
        createMediaBrowser(TEST_BROWSER_SERVICE);
        runOnMainThread(() -> {
            mMediaBrowser.connect();
            mMediaBrowser.disconnect();
        });
        resetCallbacks();
        try {
            Thread.sleep(SLEEP_MS);
        } catch (InterruptedException e) {
            fail("Unexpected InterruptedException occurred.");
        }
        assertEquals(0, mConnectionCallback.mConnectedCount);
        assertEquals(0, mConnectionCallback.mConnectionFailedCount);
        assertEquals(0, mConnectionCallback.mConnectionSuspendedCount);
    }

    public void testSubscribe() throws Throwable {
        resetCallbacks();
        createMediaBrowser(TEST_BROWSER_SERVICE);
        connectMediaBrowserService();
        runOnMainThread(() -> {
            mMediaBrowser.subscribe(StubMediaBrowserService.MEDIA_ID_ROOT, mSubscriptionCallback);
        });
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return mSubscriptionCallback.mChildrenLoadedCount > 0;
            }
        }.run();

        assertEquals(StubMediaBrowserService.MEDIA_ID_ROOT, mSubscriptionCallback.mLastParentId);
        assertEquals(StubMediaBrowserService.MEDIA_ID_CHILDREN.length,
                mSubscriptionCallback.mLastChildMediaItems.size());
        for (int i = 0; i < StubMediaBrowserService.MEDIA_ID_CHILDREN.length; ++i) {
            assertEquals(StubMediaBrowserService.MEDIA_ID_CHILDREN[i],
                    mSubscriptionCallback.mLastChildMediaItems.get(i).getMediaId());
        }

        // Test unsubscribe.
        resetCallbacks();
        runOnMainThread(() -> {
            mMediaBrowser.unsubscribe(StubMediaBrowserService.MEDIA_ID_ROOT);
        });

        // After unsubscribing, make StubMediaBrowserService notify that the children are changed.
        StubMediaBrowserService.sInstance.notifyChildrenChanged(
                StubMediaBrowserService.MEDIA_ID_ROOT);
        try {
            Thread.sleep(SLEEP_MS);
        } catch (InterruptedException e) {
            fail("Unexpected InterruptedException occurred.");
        }
        // onChildrenLoaded should not be called.
        assertEquals(0, mSubscriptionCallback.mChildrenLoadedCount);
    }

    public void testSubscribeWithIllegalArguments() throws Throwable {
        createMediaBrowser(TEST_BROWSER_SERVICE);

        runOnMainThread(() -> {
            try {
                final String nullMediaId = null;
                mMediaBrowser.subscribe(nullMediaId, mSubscriptionCallback);
                fail();
            } catch (IllegalArgumentException e) {
                // Expected
            }

            try {
                final String emptyMediaId = "";
                mMediaBrowser.subscribe(emptyMediaId, mSubscriptionCallback);
                fail();
            } catch (IllegalArgumentException e) {
                // Expected
            }

            try {
                final MediaBrowser.SubscriptionCallback nullCallback = null;
                mMediaBrowser.subscribe(StubMediaBrowserService.MEDIA_ID_ROOT, nullCallback);
                fail();
            } catch (IllegalArgumentException e) {
                // Expected
            }

            try {
                final Bundle nullOptions = null;
                mMediaBrowser.subscribe(StubMediaBrowserService.MEDIA_ID_ROOT, nullOptions,
                        mSubscriptionCallback);
                fail();
            } catch (IllegalArgumentException e) {
                // Expected
            }
        });
    }

    public void testSubscribeWithOptions() throws Throwable {
        createMediaBrowser(TEST_BROWSER_SERVICE);
        connectMediaBrowserService();
        final int pageSize = 3;
        final int lastPage = (StubMediaBrowserService.MEDIA_ID_CHILDREN.length - 1) / pageSize;
        Bundle options = new Bundle();
        options.putInt(MediaBrowser.EXTRA_PAGE_SIZE, pageSize);
        for (int page = 0; page <= lastPage; ++page) {
            resetCallbacks();
            options.putInt(MediaBrowser.EXTRA_PAGE, page);
            runOnMainThread(() -> {
                mMediaBrowser.subscribe(StubMediaBrowserService.MEDIA_ID_ROOT, options,
                        mSubscriptionCallback);
            });
            new PollingCheck(TIME_OUT_MS) {
                @Override
                protected boolean check() {
                    return mSubscriptionCallback.mChildrenLoadedWithOptionCount > 0;
                }
            }.run();
            assertEquals(StubMediaBrowserService.MEDIA_ID_ROOT,
                    mSubscriptionCallback.mLastParentId);
            if (page != lastPage) {
                assertEquals(pageSize, mSubscriptionCallback.mLastChildMediaItems.size());
            } else {
                assertEquals((StubMediaBrowserService.MEDIA_ID_CHILDREN.length - 1) % pageSize + 1,
                        mSubscriptionCallback.mLastChildMediaItems.size());
            }
            // Check whether all the items in the current page are loaded.
            for (int i = 0; i < mSubscriptionCallback.mLastChildMediaItems.size(); ++i) {
                assertEquals(StubMediaBrowserService.MEDIA_ID_CHILDREN[page * pageSize + i],
                        mSubscriptionCallback.mLastChildMediaItems.get(i).getMediaId());
            }
        }

        // Test unsubscribe with callback argument.
        resetCallbacks();
        runOnMainThread(() -> {
            mMediaBrowser.unsubscribe(StubMediaBrowserService.MEDIA_ID_ROOT, mSubscriptionCallback);
        });

        // After unsubscribing, make StubMediaBrowserService notify that the children are changed.
        StubMediaBrowserService.sInstance.notifyChildrenChanged(
                StubMediaBrowserService.MEDIA_ID_ROOT);
        try {
            Thread.sleep(SLEEP_MS);
        } catch (InterruptedException e) {
            fail("Unexpected InterruptedException occurred.");
        }
        // onChildrenLoaded should not be called.
        assertEquals(0, mSubscriptionCallback.mChildrenLoadedCount);
    }

    public void testSubscribeInvalidItem() throws Throwable {
        resetCallbacks();
        createMediaBrowser(TEST_BROWSER_SERVICE);
        connectMediaBrowserService();
        runOnMainThread(() -> {
            mMediaBrowser.subscribe(
                    StubMediaBrowserService.MEDIA_ID_INVALID, mSubscriptionCallback);
        });
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return mSubscriptionCallback.mLastErrorId != null;
            }
        }.run();

        assertEquals(StubMediaBrowserService.MEDIA_ID_INVALID, mSubscriptionCallback.mLastErrorId);
    }

    public void testSubscribeInvalidItemWithOptions() throws Throwable {
        resetCallbacks();
        createMediaBrowser(TEST_BROWSER_SERVICE);
        connectMediaBrowserService();

        final int pageSize = 5;
        final int page = 2;
        Bundle options = new Bundle();
        options.putInt(MediaBrowser.EXTRA_PAGE_SIZE, pageSize);
        options.putInt(MediaBrowser.EXTRA_PAGE, page);
        runOnMainThread(() -> {
            mMediaBrowser.subscribe(StubMediaBrowserService.MEDIA_ID_INVALID, options,
                    mSubscriptionCallback);
        });
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return mSubscriptionCallback.mLastErrorId != null;
            }
        }.run();

        assertEquals(StubMediaBrowserService.MEDIA_ID_INVALID, mSubscriptionCallback.mLastErrorId);
        assertEquals(page, mSubscriptionCallback.mLastOptions.getInt(MediaBrowser.EXTRA_PAGE));
        assertEquals(pageSize,
                mSubscriptionCallback.mLastOptions.getInt(MediaBrowser.EXTRA_PAGE_SIZE));
    }

    public void testSubscriptionCallbackNotCalledAfterDisconnect() throws Throwable {
        createMediaBrowser(TEST_BROWSER_SERVICE);
        connectMediaBrowserService();
        runOnMainThread(() -> {
            mMediaBrowser.subscribe(StubMediaBrowserService.MEDIA_ID_ROOT, mSubscriptionCallback);
            mMediaBrowser.disconnect();
        });
        resetCallbacks();
        StubMediaBrowserService.sInstance.notifyChildrenChanged(
                StubMediaBrowserService.MEDIA_ID_ROOT);
        try {
            Thread.sleep(SLEEP_MS);
        } catch (InterruptedException e) {
            fail("Unexpected InterruptedException occurred.");
        }
        assertEquals(0, mSubscriptionCallback.mChildrenLoadedCount);
        assertEquals(0, mSubscriptionCallback.mChildrenLoadedWithOptionCount);
        assertNull(mSubscriptionCallback.mLastParentId);
    }

    public void testUnsubscribeWithIllegalArguments() throws Throwable {
        createMediaBrowser(TEST_BROWSER_SERVICE);
        runOnMainThread(() -> {
            try {
                final String nullMediaId = null;
                mMediaBrowser.unsubscribe(nullMediaId);
                fail();
            } catch (IllegalArgumentException e) {
                // Expected
            }

            try {
                final String emptyMediaId = "";
                mMediaBrowser.unsubscribe(emptyMediaId);
                fail();
            } catch (IllegalArgumentException e) {
                // Expected
            }

            try {
                final MediaBrowser.SubscriptionCallback nullCallback = null;
                mMediaBrowser.unsubscribe(StubMediaBrowserService.MEDIA_ID_ROOT, nullCallback);
                fail();
            } catch (IllegalArgumentException e) {
                // Expected
            }
        });
    }

    public void testUnsubscribeForMultipleSubscriptions() throws Throwable {
        createMediaBrowser(TEST_BROWSER_SERVICE);
        connectMediaBrowserService();
        final List<StubSubscriptionCallback> subscriptionCallbacks = new ArrayList<>();
        final int pageSize = 1;

        // Subscribe four pages, one item per page.
        for (int page = 0; page < 4; page++) {
            final StubSubscriptionCallback callback = new StubSubscriptionCallback();
            subscriptionCallbacks.add(callback);

            Bundle options = new Bundle();
            options.putInt(MediaBrowser.EXTRA_PAGE, page);
            options.putInt(MediaBrowser.EXTRA_PAGE_SIZE, pageSize);
            runOnMainThread(() -> {
                mMediaBrowser.subscribe(StubMediaBrowserService.MEDIA_ID_ROOT, options, callback);
            });

            // Each onChildrenLoaded() must be called.
            new PollingCheck(TIME_OUT_MS) {
                @Override
                protected boolean check() {
                    return callback.mChildrenLoadedWithOptionCount == 1;
                }
            }.run();
        }

        // Reset callbacks and unsubscribe.
        for (StubSubscriptionCallback callback : subscriptionCallbacks) {
            callback.reset();
        }
        runOnMainThread(() -> {
            mMediaBrowser.unsubscribe(StubMediaBrowserService.MEDIA_ID_ROOT);
        });

        // After unsubscribing, make StubMediaBrowserService notify that the children are changed.
        StubMediaBrowserService.sInstance.notifyChildrenChanged(
                StubMediaBrowserService.MEDIA_ID_ROOT);
        try {
            Thread.sleep(SLEEP_MS);
        } catch (InterruptedException e) {
            fail("Unexpected InterruptedException occurred.");
        }

        // onChildrenLoaded should not be called.
        for (StubSubscriptionCallback callback : subscriptionCallbacks) {
            assertEquals(0, callback.mChildrenLoadedWithOptionCount);
        }
    }

    public void testUnsubscribeWithSubscriptionCallbackForMultipleSubscriptions() throws Throwable {
        createMediaBrowser(TEST_BROWSER_SERVICE);
        connectMediaBrowserService();
        final List<StubSubscriptionCallback> subscriptionCallbacks = new ArrayList<>();
        final int pageSize = 1;

        // Subscribe four pages, one item per page.
        for (int page = 0; page < 4; page++) {
            final StubSubscriptionCallback callback = new StubSubscriptionCallback();
            subscriptionCallbacks.add(callback);

            Bundle options = new Bundle();
            options.putInt(MediaBrowser.EXTRA_PAGE, page);
            options.putInt(MediaBrowser.EXTRA_PAGE_SIZE, pageSize);
            runOnMainThread(() -> {
                mMediaBrowser.subscribe(StubMediaBrowserService.MEDIA_ID_ROOT, options, callback);
            });

            // Each onChildrenLoaded() must be called.
            new PollingCheck(TIME_OUT_MS) {
                @Override
                protected boolean check() {
                    return callback.mChildrenLoadedWithOptionCount == 1;
                }
            }.run();
        }

        // Unsubscribe existing subscriptions one-by-one.
        final int[] orderOfRemovingCallbacks = {2, 0, 3, 1};
        for (int i = 0; i < orderOfRemovingCallbacks.length; i++) {
            // Reset callbacks
            for (StubSubscriptionCallback callback : subscriptionCallbacks) {
                callback.reset();
            }

            final int index = i;
            runOnMainThread(() -> {
                // Remove one subscription
                mMediaBrowser.unsubscribe(StubMediaBrowserService.MEDIA_ID_ROOT,
                        subscriptionCallbacks.get(orderOfRemovingCallbacks[index]));
            });

            // Make StubMediaBrowserService notify that the children are changed.
            StubMediaBrowserService.sInstance.notifyChildrenChanged(
                    StubMediaBrowserService.MEDIA_ID_ROOT);
            try {
                Thread.sleep(SLEEP_MS);
            } catch (InterruptedException e) {
                fail("Unexpected InterruptedException occurred.");
            }

            // Only the remaining subscriptionCallbacks should be called.
            for (int j = 0; j < 4; j++) {
                int childrenLoadedWithOptionsCount = subscriptionCallbacks
                        .get(orderOfRemovingCallbacks[j]).mChildrenLoadedWithOptionCount;
                if (j <= i) {
                    assertEquals(0, childrenLoadedWithOptionsCount);
                } else {
                    assertEquals(1, childrenLoadedWithOptionsCount);
                }
            }
        }
    }

    public void testGetItem() throws Throwable {
        resetCallbacks();
        createMediaBrowser(TEST_BROWSER_SERVICE);
        connectMediaBrowserService();

        runOnMainThread(() -> {
            mMediaBrowser.getItem(StubMediaBrowserService.MEDIA_ID_CHILDREN[0], mItemCallback);
        });
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return mItemCallback.mLastMediaItem != null;
            }
        }.run();

        assertEquals(StubMediaBrowserService.MEDIA_ID_CHILDREN[0],
                mItemCallback.mLastMediaItem.getMediaId());
    }

    public void testGetItemThrowsIAE() throws Throwable {
        resetCallbacks();
        createMediaBrowser(TEST_BROWSER_SERVICE);

        runOnMainThread(() -> {
            try {
                // Calling getItem() with empty mediaId will throw IAE.
                mMediaBrowser.getItem("",  mItemCallback);
                fail();
            } catch (IllegalArgumentException e) {
                // Expected
            }

            try {
                // Calling getItem() with null mediaId will throw IAE.
                mMediaBrowser.getItem(null,  mItemCallback);
                fail();
            } catch (IllegalArgumentException e) {
                // Expected
            }

            try {
                // Calling getItem() with null itemCallback will throw IAE.
                mMediaBrowser.getItem("media_id",  null);
                fail();
            } catch (IllegalArgumentException e) {
                // Expected
            }
        });
    }

    public void testGetItemWhileNotConnected() throws Throwable {
        resetCallbacks();
        createMediaBrowser(TEST_BROWSER_SERVICE);

        final String mediaId = "test_media_id";
        runOnMainThread(() -> {
            mMediaBrowser.getItem(mediaId, mItemCallback);
        });

        // Calling getItem while not connected will invoke ItemCallback.onError().
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return mItemCallback.mLastErrorId != null;
            }
        }.run();

        assertEquals(mItemCallback.mLastErrorId, mediaId);
    }

    public void testGetItemFailure() throws Throwable {
        resetCallbacks();
        createMediaBrowser(TEST_BROWSER_SERVICE);
        connectMediaBrowserService();
        runOnMainThread(() -> {
            mMediaBrowser.getItem(StubMediaBrowserService.MEDIA_ID_INVALID, mItemCallback);
        });
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return mItemCallback.mLastErrorId != null;
            }
        }.run();

        assertEquals(StubMediaBrowserService.MEDIA_ID_INVALID, mItemCallback.mLastErrorId);
    }

    public void testItemCallbackNotCalledAfterDisconnect() throws Throwable {
        createMediaBrowser(TEST_BROWSER_SERVICE);
        connectMediaBrowserService();
        runOnMainThread(() -> {
            mMediaBrowser.getItem(StubMediaBrowserService.MEDIA_ID_CHILDREN[0], mItemCallback);
            mMediaBrowser.disconnect();
        });
        resetCallbacks();
        try {
            Thread.sleep(SLEEP_MS);
        } catch (InterruptedException e) {
            fail("Unexpected InterruptedException occurred.");
        }
        assertNull(mItemCallback.mLastMediaItem);
        assertNull(mItemCallback.mLastErrorId);
    }

    private void createMediaBrowser(final ComponentName component) throws Throwable {
        runOnMainThread(() -> {
            mMediaBrowser = new MediaBrowser(getInstrumentation().getTargetContext(),
                    component, mConnectionCallback, null);
        });
    }

    private void connectMediaBrowserService() throws Throwable {
        runOnMainThread(() -> {
            mMediaBrowser.connect();
        });
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return mConnectionCallback.mConnectedCount > 0;
            }
        }.run();
    }

    private void disconnectMediaBrowser() throws Throwable {
        runOnMainThread(() -> {
            mMediaBrowser.disconnect();
        });
    }

    private void resetCallbacks() {
        mConnectionCallback.reset();
        mSubscriptionCallback.reset();
        mItemCallback.reset();
    }

    private void runOnMainThread(Runnable runnable) throws Throwable {
        AtomicReference<Throwable> throwableRef = new AtomicReference<>();

        getInstrumentation().runOnMainSync(() -> {
            try {
                runnable.run();
            } catch (Throwable t) {
                throwableRef.set(t);
            }
        });

        Throwable t = throwableRef.get();
        if (t != null) {
            throw t;
        }
    }

    private static class StubConnectionCallback extends MediaBrowser.ConnectionCallback {
        volatile int mConnectedCount;
        volatile int mConnectionFailedCount;
        volatile int mConnectionSuspendedCount;

        public void reset() {
            mConnectedCount = 0;
            mConnectionFailedCount = 0;
            mConnectionSuspendedCount = 0;
        }

        @Override
        public void onConnected() {
            mConnectedCount++;
        }

        @Override
        public void onConnectionFailed() {
            mConnectionFailedCount++;
        }

        @Override
        public void onConnectionSuspended() {
            mConnectionSuspendedCount++;
        }
    }

    private static class StubSubscriptionCallback extends MediaBrowser.SubscriptionCallback {
        private volatile int mChildrenLoadedCount;
        private volatile int mChildrenLoadedWithOptionCount;
        private volatile String mLastErrorId;
        private volatile String mLastParentId;
        private volatile Bundle mLastOptions;
        private volatile List<MediaBrowser.MediaItem> mLastChildMediaItems;

        public void reset() {
            mChildrenLoadedCount = 0;
            mChildrenLoadedWithOptionCount = 0;
            mLastErrorId = null;
            mLastParentId = null;
            mLastOptions = null;
            mLastChildMediaItems = null;
        }

        @Override
        public void onChildrenLoaded(String parentId, List<MediaBrowser.MediaItem> children) {
            mChildrenLoadedCount++;
            mLastParentId = parentId;
            mLastChildMediaItems = children;
        }

        @Override
        public void onChildrenLoaded(String parentId, List<MediaBrowser.MediaItem> children,
                Bundle options) {
            mChildrenLoadedWithOptionCount++;
            mLastParentId = parentId;
            mLastOptions = options;
            mLastChildMediaItems = children;
        }

        @Override
        public void onError(String id) {
            mLastErrorId = id;
        }

        @Override
        public void onError(String id, Bundle options) {
            mLastErrorId = id;
            mLastOptions = options;
        }
    }

    private static class StubItemCallback extends MediaBrowser.ItemCallback {
        private volatile MediaBrowser.MediaItem mLastMediaItem;
        private volatile String mLastErrorId;

        public void reset() {
            mLastMediaItem = null;
            mLastErrorId = null;
        }

        @Override
        public void onItemLoaded(MediaItem item) {
            mLastMediaItem = item;
        }

        @Override
        public void onError(String id) {
            mLastErrorId = id;
        }
    }
}
