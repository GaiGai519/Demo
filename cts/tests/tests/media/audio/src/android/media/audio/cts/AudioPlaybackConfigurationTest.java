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

package android.media.audio.cts;

import static android.media.AudioAttributes.ALLOW_CAPTURE_BY_ALL;
import static android.media.AudioAttributes.ALLOW_CAPTURE_BY_NONE;
import static android.media.AudioAttributes.ALLOW_CAPTURE_BY_SYSTEM;

import android.annotation.Nullable;
import android.annotation.RawRes;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioAttributes.CapturePolicy;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.media.audio.cts.R;
import android.media.cts.NonMediaMainlineTest;
import android.media.cts.TestUtils;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Parcel;
import android.util.Log;

import com.android.compatibility.common.util.CtsAndroidTestCase;
import com.android.internal.annotations.GuardedBy;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@NonMediaMainlineTest
public class AudioPlaybackConfigurationTest extends CtsAndroidTestCase {
    private final static String TAG = "AudioPlaybackConfigurationTest";

    private final static int TEST_TIMING_TOLERANCE_MS = 150;
    /** acceptable timeout for the time it takes for a prepared MediaPlayer to have an audio device
     * selected and reported when starting to play */
    private final static int PLAY_ROUTING_TIMING_TOLERANCE_MS = 500;
    private final static int TEST_TIMEOUT_SOUNDPOOL_LOAD_MS = 3000;
    private final static long MEDIAPLAYER_PREPARE_TIMEOUT_MS = 2000;

    // not declared inside test so it can be released in case of failure
    private MediaPlayer mMp;
    private SoundPool mSp;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        // try/catch for every method in case the tests left the objects in various states
        if (mMp != null) {
            try { mMp.stop(); } catch (Exception e) {}
            try { mMp.release(); } catch (Exception e) {}
            mMp = null;
        }
        if (mSp != null) {
            try { mSp.release(); } catch (Exception e) {}
            mSp = null;
        }
    }

    private final static int TEST_USAGE = AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_DELAYED;
    private final static int TEST_CONTENT = AudioAttributes.CONTENT_TYPE_SPEECH;

    // test marshalling/unmarshalling of an AudioPlaybackConfiguration instance. Since we can't
    // create an AudioPlaybackConfiguration directly, we first need to play something to get one.
    public void testParcelableWriteToParcel() throws Exception {
        if (!isValidPlatform("testParcelableWriteToParcel")) return;

        // create a player, make it play so we can get an AudioPlaybackConfiguration instance
        AudioManager am = new AudioManager(getContext());
        assertNotNull("Could not create AudioManager", am);
        final AudioAttributes aa = (new AudioAttributes.Builder())
                .setUsage(TEST_USAGE)
                .setContentType(TEST_CONTENT)
                .setAllowedCapturePolicy(ALLOW_CAPTURE_BY_NONE)
                .build();
        mMp = createPreparedMediaPlayer(R.raw.sine1khzs40dblong, aa, am.generateAudioSessionId());
        mMp.start();
        Thread.sleep(TEST_TIMING_TOLERANCE_MS);// waiting for playback to start
        List<AudioPlaybackConfiguration> configs = am.getActivePlaybackConfigurations();
        mMp.stop();
        assertTrue("No playback reported", configs.size() > 0);
        AudioPlaybackConfiguration configToMarshall = null;
        for (AudioPlaybackConfiguration config : configs) {
            if (config.getAudioAttributes().equals(aa)) {
                configToMarshall = config;
                break;
            }
        }

        assertNotNull("Configuration not found during playback", configToMarshall);
        assertEquals(0, configToMarshall.describeContents());

        final Parcel srcParcel = Parcel.obtain();
        final Parcel dstParcel = Parcel.obtain();
        final byte[] mbytes;

        configToMarshall.writeToParcel(srcParcel, 0 /*no public flags for marshalling*/);
        mbytes = srcParcel.marshall();
        dstParcel.unmarshall(mbytes, 0, mbytes.length);
        dstParcel.setDataPosition(0);
        final AudioPlaybackConfiguration restoredConfig =
                AudioPlaybackConfiguration.CREATOR.createFromParcel(dstParcel);

        assertEquals("Marshalled/restored AudioAttributes don't match",
                configToMarshall.getAudioAttributes(), restoredConfig.getAudioAttributes());
    }

    public void testGetterMediaPlayer() throws Exception {
        if (!isValidPlatform("testGetterMediaPlayer")) return;

        AudioManager am = new AudioManager(getContext());
        assertNotNull("Could not create AudioManager", am);

        final AudioAttributes aa = (new AudioAttributes.Builder())
                .setUsage(TEST_USAGE)
                .setContentType(TEST_CONTENT)
                .setAllowedCapturePolicy(ALLOW_CAPTURE_BY_ALL)
                .build();

        List<AudioPlaybackConfiguration> configs = am.getActivePlaybackConfigurations();
        final int nbActivePlayersBeforeStart = configs.size();

        mMp = createPreparedMediaPlayer(R.raw.sine1khzs40dblong, aa, am.generateAudioSessionId());
        configs = am.getActivePlaybackConfigurations();
        assertEquals("inactive MediaPlayer, number of configs shouldn't have changed",
                nbActivePlayersBeforeStart /*expected*/, configs.size());

        mMp.start();
        Thread.sleep(TEST_TIMING_TOLERANCE_MS);// waiting for playback to start
        configs = am.getActivePlaybackConfigurations();
        assertEquals("active MediaPlayer, number of configs should have increased",
                nbActivePlayersBeforeStart + 1 /*expected*/,
                configs.size());
        assertTrue("Active player, attributes not found", hasAttr(configs, aa));

        // verify "privileged" fields aren't available through reflection
        final AudioPlaybackConfiguration config = configs.get(0);
        final Class<?> confClass = config.getClass();
        final Method getClientUidMethod = confClass.getDeclaredMethod("getClientUid");
        final Method getClientPidMethod = confClass.getDeclaredMethod("getClientPid");
        final Method getPlayerTypeMethod = confClass.getDeclaredMethod("getPlayerType");
        final Method getSessionIdMethod = confClass.getDeclaredMethod("getSessionId");
        try {
            Integer uid = (Integer) getClientUidMethod.invoke(config, (Object[]) null);
            assertEquals("uid isn't protected", -1 /*expected*/, uid.intValue());
            Integer pid = (Integer) getClientPidMethod.invoke(config, (Object[]) null);
            assertEquals("pid isn't protected", -1 /*expected*/, pid.intValue());
            Integer type = (Integer) getPlayerTypeMethod.invoke(config, (Object[]) null);
            assertEquals("player type isn't protected", -1 /*expected*/, type.intValue());
            Integer sessionId = (Integer) getSessionIdMethod.invoke(config, (Object[]) null);
            assertEquals("session ID isn't protected", 0 /*expected*/, sessionId.intValue());
        } catch (Exception e) {
            fail("Exception thrown during reflection on config privileged fields"+ e);
        }
    }

    public void testCallbackMediaPlayer() throws Exception {
        if (!isValidPlatform("testCallbackMediaPlayer")) return;
        doTestCallbackMediaPlayer(false /* no custom Handler for callback */);
    }

    public void testCallbackMediaPlayerHandler() throws Exception {
        if (!isValidPlatform("testCallbackMediaPlayerHandler")) return;
        doTestCallbackMediaPlayer(true /* use custom Handler for callback */);
    }

    private void doTestCallbackMediaPlayer(boolean useHandlerInCallback) throws Exception {
        final Handler h;
        if (useHandlerInCallback) {
            HandlerThread handlerThread = new HandlerThread(TAG);
            handlerThread.start();
            h = new Handler(handlerThread.getLooper());
        } else {
            h = null;
        }

        AudioManager am = new AudioManager(getContext());
        assertNotNull("Could not create AudioManager", am);

        MyAudioPlaybackCallback callback = new MyAudioPlaybackCallback();

        MyAudioPlaybackCallback registeredCallback = null;

        final AudioAttributes aa = (new AudioAttributes.Builder())
                .setUsage(TEST_USAGE)
                .setContentType(TEST_CONTENT)
                .build();

        try {
            mMp =  createPreparedMediaPlayer(R.raw.sine1khzs40dblong, aa,
                    am.generateAudioSessionId());

            am.registerAudioPlaybackCallback(callback, h /*handler*/);
            registeredCallback = callback;

            // query how many active players before starting the MediaPlayer
            List<AudioPlaybackConfiguration> configs = am.getActivePlaybackConfigurations();
            final int nbActivePlayersBeforeStart = configs.size();

            assertPlayerStartAndCallbackWithPlayerAttributes(mMp, callback,
                    nbActivePlayersBeforeStart + 1, aa);


            // stopping playback: callback is called with no match
            callback.reset();
            mMp.pause();
            Thread.sleep(TEST_TIMING_TOLERANCE_MS);

            assertEquals("onPlaybackConfigChanged call count not expected after pause",
                    1/*expected*/, callback.getCbInvocationNumber());//only 1 pause call since reset
            assertEquals("number of active players not expected after pause",
                    nbActivePlayersBeforeStart/*expected*/, callback.getNbConfigs());

            // unregister callback and start playback again
            am.unregisterAudioPlaybackCallback(callback);
            registeredCallback = null;
            Thread.sleep(TEST_TIMING_TOLERANCE_MS);
            callback.reset();
            mMp.start();
            Thread.sleep(TEST_TIMING_TOLERANCE_MS);
            assertEquals("onPlaybackConfigChanged call count not expected after unregister",
                    0/*expected*/, callback.getCbInvocationNumber()); //callback is unregistered

            // just call the callback once directly so it's marked as tested
            final AudioManager.AudioPlaybackCallback apc =
                    (AudioManager.AudioPlaybackCallback) callback;
            apc.onPlaybackConfigChanged(new ArrayList<AudioPlaybackConfiguration>());
        } finally {
            if (registeredCallback != null) {
                am.unregisterAudioPlaybackCallback(registeredCallback);
            }
            if (h != null) {
                h.getLooper().quit();
            }
        }
    }

    public void testCallbackMediaPlayerRelease() throws Exception {
        final HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        final Handler h = new Handler(handlerThread.getLooper());

        AudioManager am = new AudioManager(getContext());
        assertNotNull("Could not create AudioManager", am);

        MyAudioPlaybackCallback callback = new MyAudioPlaybackCallback();

        final AudioAttributes aa = (new AudioAttributes.Builder())
                .setUsage(TEST_USAGE)
                .setContentType(TEST_CONTENT)
                .build();

        try {
            mMp = createPreparedMediaPlayer(R.raw.sine1khzs40dblong, aa,
                    am.generateAudioSessionId());

            am.registerAudioPlaybackCallback(callback, h /*handler*/);

            // query how many active players before starting the MediaPlayer
            List<AudioPlaybackConfiguration> configs =
                    am.getActivePlaybackConfigurations();
            final int nbActivePlayersBeforeStart = configs.size();

            assertPlayerStartAndCallbackWithPlayerAttributes(mMp, callback,
                    nbActivePlayersBeforeStart + 1, aa);

            // release the player without stopping or pausing it first
            callback.reset();
            mMp.release();
            Thread.sleep(TEST_TIMING_TOLERANCE_MS);

            assertEquals("onPlaybackConfigChanged call count not expected after release",
                    1/*expected*/, callback.getCbInvocationNumber());//only release call since reset
            assertEquals("number of active players not expected after release",
                    nbActivePlayersBeforeStart/*expected*/, callback.getNbConfigs());

        } finally {
            am.unregisterAudioPlaybackCallback(callback);
            if (h != null) {
                h.getLooper().quit();
            }
        }
    }

    public void testGetterSoundPool() throws Exception {
        if (!isValidPlatform("testSoundPool")) return;

        AudioManager am = new AudioManager(getContext());
        assertNotNull("Could not create AudioManager", am);

        MyAudioPlaybackCallback callback = new MyAudioPlaybackCallback();
        am.registerAudioPlaybackCallback(callback, null /*handler*/);

        // query how many active players before starting the SoundPool
        List<AudioPlaybackConfiguration> configs = am.getActivePlaybackConfigurations();
        int nbActivePlayersBeforeStart = 0;
        for (AudioPlaybackConfiguration apc : configs) {
            if (apc.getPlayerState() == AudioPlaybackConfiguration.PLAYER_STATE_STARTED) {
                nbActivePlayersBeforeStart++;
            }
        }

        final AudioAttributes aa = (new AudioAttributes.Builder())
                .setUsage(TEST_USAGE)
                .setContentType(TEST_CONTENT)
                .setAllowedCapturePolicy(ALLOW_CAPTURE_BY_SYSTEM)
                .build();

        mSp = new SoundPool.Builder()
                .setAudioAttributes(aa)
                .setMaxStreams(1)
                .build();
        final Object loadLock = new Object();
        final SoundPool zepool = mSp;
        // load a sound and play it once load completion is reported
        mSp.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                assertEquals("Receiving load completion for wrong SoundPool", zepool, mSp);
                assertEquals("Load completion error", 0 /*success expected*/, status);
                synchronized (loadLock) {
                    loadLock.notify();
                }
            }
        });
        final int loadId = mSp.load(getContext(), R.raw.sine1320hz5sec, 1/*priority*/);
        synchronized (loadLock) {
            loadLock.wait(TEST_TIMEOUT_SOUNDPOOL_LOAD_MS);
        }
        int res = mSp.play(loadId, 1.0f /*leftVolume*/, 1.0f /*rightVolume*/, 1 /*priority*/,
                0 /*loop*/, 1.0f/*rate*/);
        // FIXME SoundPool activity is not reported yet, but exercise creation/release with
        //       an AudioPlaybackCallback registered
        assertTrue("Error playing sound through SoundPool", res > 0);
        Thread.sleep(TEST_TIMING_TOLERANCE_MS);

        mSp.autoPause();
        Thread.sleep(TEST_TIMING_TOLERANCE_MS);
        // query how many active players after pausing
        configs = am.getActivePlaybackConfigurations();
        int nbActivePlayersAfterPause = 0;
        for (AudioPlaybackConfiguration apc : configs) {
            if (apc.getPlayerState() == AudioPlaybackConfiguration.PLAYER_STATE_STARTED) {
                nbActivePlayersAfterPause++;
            }
        }
        assertEquals("Number of active players changed after pausing SoundPool",
                nbActivePlayersBeforeStart, nbActivePlayersAfterPause);
    }

    public void testGetAudioDeviceInfoMediaPlayerStart() throws Exception {
        if (!isValidPlatform("testGetAudioDeviceInfoMediaPlayerStart")) return;

        final HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        final Handler h = new Handler(handlerThread.getLooper());

        AudioManager am = new AudioManager(getContext());
        assertNotNull("Could not create AudioManager", am);

        MyAudioPlaybackCallback callback = new MyAudioPlaybackCallback();

        final AudioAttributes aa = (new AudioAttributes.Builder())
                .setUsage(TEST_USAGE)
                .setContentType(TEST_CONTENT)
                .build();

        try {
            mMp = createPreparedMediaPlayer(R.raw.sine1khzs40dblong, aa,
                    am.generateAudioSessionId());

            am.registerAudioPlaybackCallback(callback, h /*handler*/);

            // query how many active players before starting the MediaPlayer
            List<AudioPlaybackConfiguration> configs =
                    am.getActivePlaybackConfigurations();
            final int nbActivePlayersBeforeStart = configs.size();

            assertPlayerStartAndCallbackWithPlayerAttributes(mMp, callback,
                    nbActivePlayersBeforeStart + 1, aa);

            assertTrue("Active player, device not found",
                    hasDevice(callback.getConfigs(), aa));

        } finally {
            am.unregisterAudioPlaybackCallback(callback);
            if (h != null) {
                h.getLooper().quit();
            }
        }
    }

    private @Nullable MediaPlayer createPreparedMediaPlayer(
            @RawRes int resID, AudioAttributes aa, int session) throws Exception {
        final TestUtils.Monitor onPreparedCalled = new TestUtils.Monitor();
        final MediaPlayer mp = createPlayer(resID, aa, session);
        mp.setOnPreparedListener(mp1 -> onPreparedCalled.signal());
        mp.prepare();
        onPreparedCalled.waitForSignal(MEDIAPLAYER_PREPARE_TIMEOUT_MS);
        assertTrue(
                "MediaPlayer wasn't prepared in under " + MEDIAPLAYER_PREPARE_TIMEOUT_MS + " ms",
                onPreparedCalled.isSignalled());
        return mp;
    }

    private MediaPlayer createPlayer(
            @RawRes int resID, AudioAttributes aa, int session) throws IOException {
        MediaPlayer mp = new MediaPlayer();
        mp.setAudioAttributes(aa);
        mp.setAudioSessionId(session);
        AssetFileDescriptor afd = getContext().getResources().openRawResourceFd(resID);
        try {
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
        } finally {
            afd.close();
        }
        return mp;
    }

    private void assertPlayerStartAndCallbackWithPlayerAttributes(
            MediaPlayer mp, MyAudioPlaybackCallback callback,
            int activePlayerCount, AudioAttributes aa) throws Exception{
        mp.start();

        assertTrue("onPlaybackConfigChanged play and device called expected "
                , callback.waitForCallbacks(2,
                        TEST_TIMING_TOLERANCE_MS + PLAY_ROUTING_TIMING_TOLERANCE_MS));
        assertEquals("number of active players not expected",
                // one more player active
                activePlayerCount/*expected*/, callback.getNbConfigs());
        assertTrue("Active player, attributes not found", hasAttr(callback.getConfigs(), aa));
    }

    private static class MyAudioPlaybackCallback extends AudioManager.AudioPlaybackCallback {
        private final Object mCbLock = new Object();
        @GuardedBy("mCbLock")
        private int mCalled;
        @GuardedBy("mCbLock")
        private List<AudioPlaybackConfiguration> mConfigs;

        final TestUtils.Monitor mOnCalledMonitor = new TestUtils.Monitor();

        void reset() {
            synchronized (mCbLock) {
                mCalled = 0;
                mConfigs = new ArrayList<AudioPlaybackConfiguration>();
            }
        }

        int getCbInvocationNumber() {
            synchronized (mCbLock) {
                return mCalled;
            }
        }

        int getNbConfigs() {
            return getConfigs().size();
        }

        List<AudioPlaybackConfiguration> getConfigs() {
            synchronized (mCbLock) {
                return mConfigs;
            }
        }

        MyAudioPlaybackCallback() {
            reset();
        }

        @Override
        public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs) {
            synchronized (mCbLock) {
                mCalled++;
                mConfigs = configs;
            }
            mOnCalledMonitor.signal();
        }

        public boolean waitForCallbacks(int calledCount, long timeoutMs)
                throws InterruptedException {
            int signalsCounted =
                    mOnCalledMonitor.waitForCountedSignals(calledCount, timeoutMs);
            return (signalsCounted == calledCount);
        }
    }

    private static boolean hasAttr(List<AudioPlaybackConfiguration> configs, AudioAttributes aa) {
        for (AudioPlaybackConfiguration apc : configs) {
            if (apc.getAudioAttributes().getContentType() == aa.getContentType()
                && apc.getAudioAttributes().getUsage() == aa.getUsage()
                && apc.getAudioAttributes().getFlags() == aa.getFlags()
                && anonymizeCapturePolicy(apc.getAudioAttributes().getAllowedCapturePolicy())
                    == aa.getAllowedCapturePolicy()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasDevice(List<AudioPlaybackConfiguration> configs, AudioAttributes aa) {
        for (AudioPlaybackConfiguration apc : configs) {
            if (apc.getAudioAttributes().getContentType() == aa.getContentType()
                    && apc.getAudioAttributes().getUsage() == aa.getUsage()
                    && apc.getAudioAttributes().getFlags() == aa.getFlags()
                    && anonymizeCapturePolicy(apc.getAudioAttributes().getAllowedCapturePolicy())
                            == aa.getAllowedCapturePolicy()
                    && apc.getAudioDeviceInfo() != null) {
                return true;
            }
        }
        return false;
    }

    /** ALLOW_CAPTURE_BY_SYSTEM is anonymized to ALLOW_CAPTURE_BY_NONE. */
    @CapturePolicy
    private static int anonymizeCapturePolicy(@CapturePolicy int policy) {
        if (policy == ALLOW_CAPTURE_BY_SYSTEM) {
            return ALLOW_CAPTURE_BY_NONE;
        }
        return policy;
    }

    private boolean isValidPlatform(String testName) {
        if (!getContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)) {
            Log.w(TAG,"AUDIO_OUTPUT feature not found. This system might not have a valid "
                    + "audio output HAL, skipping test " + testName);
            return false;
        }
        return true;
    }
}
