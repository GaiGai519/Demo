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

package android.mediav2.cts;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;
import android.util.Pair;
import android.view.Surface;

import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static android.mediav2.cts.CodecTestBase.hasSupportForColorFormat;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public class CodecEncoderSurfaceTest {
    private static final String LOG_TAG = CodecEncoderSurfaceTest.class.getSimpleName();
    private static final String mInpPrefix = WorkDir.getMediaDirString();
    private static final boolean ENABLE_LOGS = false;

    private final String mCompName;
    private final String mMime;
    private final String mTestFile;
    private final int mBitrate;
    private final int mFrameRate;
    private final int mMaxBFrames;
    private int mLatency;
    private boolean mReviseLatency;
    private MediaFormat mEncoderFormat;

    private MediaExtractor mExtractor;
    private MediaCodec mEncoder;
    private CodecAsyncHandler mAsyncHandleEncoder;
    private String mDecoderName;
    private MediaCodec mDecoder;
    private MediaFormat mDecoderFormat;
    private CodecAsyncHandler mAsyncHandleDecoder;
    private boolean mIsCodecInAsyncMode;
    private boolean mSignalEOSWithLastFrame;
    private boolean mSawDecInputEOS;
    private boolean mSawDecOutputEOS;
    private boolean mSawEncOutputEOS;
    private int mDecInputCount;
    private int mDecOutputCount;
    private int mEncOutputCount;

    private boolean mSaveToMem;
    private OutputManager mOutputBuff;

    private Surface mSurface;

    private MediaMuxer mMuxer;
    private int mTrackID = -1;

    static {
        android.os.Bundle args = InstrumentationRegistry.getArguments();
        CodecTestBase.mimeSelKeys = args.getString(CodecTestBase.MIME_SEL_KEY);
    }

    public CodecEncoderSurfaceTest(String encoder, String mime, String testFile, int bitrate,
            int frameRate) {
        mCompName = encoder;
        mMime = mime;
        mTestFile = testFile;
        mBitrate = bitrate;
        mFrameRate = frameRate;
        mMaxBFrames = 0;
        mLatency = mMaxBFrames;
        mReviseLatency = false;
        mAsyncHandleDecoder = new CodecAsyncHandler();
        mAsyncHandleEncoder = new CodecAsyncHandler();
    }

    @Before
    public void setUp() throws IOException {
        if (mCompName.startsWith(CodecTestBase.INVALID_CODEC)) {
            fail("no valid component available for current test ");
        }
        mDecoderFormat = setUpSource(mTestFile);
        ArrayList<MediaFormat> decoderFormatList = new ArrayList<>();
        decoderFormatList.add(mDecoderFormat);
        String decoderMediaType = mDecoderFormat.getString(MediaFormat.KEY_MIME);
        if (CodecTestBase.doesAnyFormatHaveHDRProfile(decoderMediaType, decoderFormatList) ||
                mTestFile.contains("10bit")) {
            // Check if encoder is capable of supporting HDR profiles.
            // Previous check doesn't verify this as profile isn't set in the format
            Assume.assumeTrue(mCompName + " doesn't support HDR encoding",
                    CodecTestBase.doesCodecSupportHDRProfile(mCompName, mMime));
        }

        MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        mDecoderName = codecList.findDecoderForFormat(mDecoderFormat);
        Assume.assumeNotNull(mDecoderFormat.toString() + " not supported by any decoder.",
                mDecoderName);
        // findDecoderForFormat() ignores color-format and decoder returned may not be
        // supporting the color format set in mDecoderFormat. Following check will
        // skip the test if decoder doesn't support the color format that is set.
        int decoderColorFormat = mDecoderFormat.getInteger(MediaFormat.KEY_COLOR_FORMAT);
        Assume.assumeTrue(mDecoderName + " doesn't support P010 output.",
                hasSupportForColorFormat(mDecoderName, decoderMediaType, decoderColorFormat));

        mEncoderFormat = setUpEncoderFormat(mDecoderFormat);
    }

    @Parameterized.Parameters(name = "{index}({0}_{1})")
    public static Collection<Object[]> input() {
        final boolean isEncoder = true;
        final boolean needAudio = false;
        final boolean needVideo = true;
        final List<Object[]> exhaustiveArgsList = new ArrayList<>(Arrays.asList(new Object[][]{
                // Video - CodecMime, test file, bit rate, frame rate
                {MediaFormat.MIMETYPE_VIDEO_H263, "bbb_176x144_128kbps_15fps_h263.3gp", 128000, 15},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, "bbb_128x96_64kbps_12fps_mpeg4.mp4", 64000, 12},
                {MediaFormat.MIMETYPE_VIDEO_AVC, "bbb_cif_768kbps_30fps_avc.mp4", 512000, 30},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, "bbb_cif_768kbps_30fps_avc.mp4", 512000, 30},
                {MediaFormat.MIMETYPE_VIDEO_VP8, "bbb_cif_768kbps_30fps_avc.mp4", 512000, 30},
                {MediaFormat.MIMETYPE_VIDEO_VP9, "bbb_cif_768kbps_30fps_avc.mp4", 512000, 30},
                {MediaFormat.MIMETYPE_VIDEO_AV1, "bbb_cif_768kbps_30fps_avc.mp4", 512000, 30},
        }));
        // P010 support was added in Android T, hence limit the following tests to Android T and
        // above
        if (CodecTestBase.IS_AT_LEAST_T) {
            exhaustiveArgsList.addAll(Arrays.asList(new Object[][]{
                {MediaFormat.MIMETYPE_VIDEO_AVC, "cosmat_520x390_24fps_crf22_avc_10bit.mkv",
                        512000, 30},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, "cosmat_520x390_24fps_crf22_hevc_10bit.mkv",
                        512000, 30},
                {MediaFormat.MIMETYPE_VIDEO_VP9, "cosmat_520x390_24fps_crf22_vp9_10bit.mkv",
                        512000, 30},
                {MediaFormat.MIMETYPE_VIDEO_AV1, "cosmat_520x390_24fps_768kbps_av1_10bit.mkv",
                        512000, 30},
            }));
        }
        return CodecTestBase.prepareParamList(exhaustiveArgsList, isEncoder, needAudio, needVideo,
                true);
    }

    private boolean hasSeenError() {
        return mAsyncHandleDecoder.hasSeenError() || mAsyncHandleEncoder.hasSeenError();
    }

    private MediaFormat setUpSource(String srcFile) throws IOException {
        mExtractor = new MediaExtractor();
        mExtractor.setDataSource(mInpPrefix + srcFile);
        for (int trackID = 0; trackID < mExtractor.getTrackCount(); trackID++) {
            MediaFormat format = mExtractor.getTrackFormat(trackID);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                mExtractor.selectTrack(trackID);
                ArrayList<MediaFormat> formatList = new ArrayList<>();
                formatList.add(format);
                boolean selectHBD = CodecTestBase.doesAnyFormatHaveHDRProfile(mime, formatList) ||
                        srcFile.contains("10bit");
                if (selectHBD) {
                    format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUVP010);
                } else {
                    format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
                }
                return format;
            }
        }
        mExtractor.release();
        fail("No video track found in file: " + srcFile);
        return null;
    }

    private void resetContext(boolean isAsync, boolean signalEOSWithLastFrame) {
        mAsyncHandleDecoder.resetContext();
        mAsyncHandleEncoder.resetContext();
        mIsCodecInAsyncMode = isAsync;
        mSignalEOSWithLastFrame = signalEOSWithLastFrame;
        mSawDecInputEOS = false;
        mSawDecOutputEOS = false;
        mSawEncOutputEOS = false;
        mDecInputCount = 0;
        mDecOutputCount = 0;
        mEncOutputCount = 0;
    }

    private void configureCodec(MediaFormat decFormat, MediaFormat encFormat, boolean isAsync,
            boolean signalEOSWithLastFrame) {
        resetContext(isAsync, signalEOSWithLastFrame);
        mAsyncHandleEncoder.setCallBack(mEncoder, isAsync);
        mEncoder.configure(encFormat, null, MediaCodec.CONFIGURE_FLAG_ENCODE, null);
        if (mEncoder.getInputFormat().containsKey(MediaFormat.KEY_LATENCY)) {
            mReviseLatency = true;
            mLatency = mEncoder.getInputFormat().getInteger(MediaFormat.KEY_LATENCY);
        }
        mSurface = mEncoder.createInputSurface();
        assertTrue("Surface is not valid", mSurface.isValid());
        mAsyncHandleDecoder.setCallBack(mDecoder, isAsync);
        mDecoder.configure(decFormat, mSurface, null, 0);
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "codec configured");
        }
    }

    private void enqueueDecoderEOS(int bufferIndex) {
        if (!mSawDecInputEOS) {
            mDecoder.queueInputBuffer(bufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            mSawDecInputEOS = true;
            if (ENABLE_LOGS) {
                Log.v(LOG_TAG, "Queued End of Stream");
            }
        }
    }

    private void enqueueDecoderInput(int bufferIndex) {
        if (mExtractor.getSampleSize() < 0) {
            enqueueDecoderEOS(bufferIndex);
        } else {
            ByteBuffer inputBuffer = mDecoder.getInputBuffer(bufferIndex);
            mExtractor.readSampleData(inputBuffer, 0);
            int size = (int) mExtractor.getSampleSize();
            long pts = mExtractor.getSampleTime();
            int extractorFlags = mExtractor.getSampleFlags();
            int codecFlags = 0;
            if ((extractorFlags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                codecFlags |= MediaCodec.BUFFER_FLAG_KEY_FRAME;
            }
            if ((extractorFlags & MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
                codecFlags |= MediaCodec.BUFFER_FLAG_PARTIAL_FRAME;
            }
            if (!mExtractor.advance() && mSignalEOSWithLastFrame) {
                codecFlags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                mSawDecInputEOS = true;
            }
            if (ENABLE_LOGS) {
                Log.v(LOG_TAG, "input: id: " + bufferIndex + " size: " + size + " pts: " + pts +
                        " flags: " + codecFlags);
            }
            mDecoder.queueInputBuffer(bufferIndex, 0, size, pts, codecFlags);
            if (size > 0 && (codecFlags & (MediaCodec.BUFFER_FLAG_CODEC_CONFIG |
                    MediaCodec.BUFFER_FLAG_PARTIAL_FRAME)) == 0) {
                mOutputBuff.saveInPTS(pts);
                mDecInputCount++;
            }
        }
    }

    private void dequeueDecoderOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mSawDecOutputEOS = true;
        }
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "output: id: " + bufferIndex + " flags: " + info.flags + " size: " +
                    info.size + " timestamp: " + info.presentationTimeUs);
        }
        if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
            mDecOutputCount++;
        }
        mDecoder.releaseOutputBuffer(bufferIndex, mSurface != null);
    }

    private void dequeueEncoderOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "encoder output: id: " + bufferIndex + " flags: " + info.flags +
                    " size: " + info.size + " timestamp: " + info.presentationTimeUs);
        }
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mSawEncOutputEOS = true;
        }
        if (info.size > 0) {
            ByteBuffer buf = mEncoder.getOutputBuffer(bufferIndex);
            if (mSaveToMem) {
                mOutputBuff.saveToMemory(buf, info);
            }
            if (mMuxer != null) {
                if (mTrackID == -1) {
                    mTrackID = mMuxer.addTrack(mEncoder.getOutputFormat());
                    mMuxer.start();
                }
                mMuxer.writeSampleData(mTrackID, buf, info);
            }
            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                mOutputBuff.saveOutPTS(info.presentationTimeUs);
                mEncOutputCount++;
            }
        }
        mEncoder.releaseOutputBuffer(bufferIndex, false);
    }

    private void tryEncoderOutput(long timeOutUs) throws InterruptedException {
        if (mIsCodecInAsyncMode) {
            if (!hasSeenError() && !mSawEncOutputEOS) {
                int retry = 0;
                while (mReviseLatency) {
                    if (mAsyncHandleEncoder.hasOutputFormatChanged()) {
                        mReviseLatency = false;
                        int actualLatency = mAsyncHandleEncoder.getOutputFormat()
                                .getInteger(MediaFormat.KEY_LATENCY, mLatency);
                        if (mLatency < actualLatency) {
                            mLatency = actualLatency;
                            return;
                        }
                    } else {
                        if (retry > CodecTestBase.RETRY_LIMIT) throw new InterruptedException(
                                "did not receive output format changed for encoder after " +
                                        CodecTestBase.Q_DEQ_TIMEOUT_US * CodecTestBase.RETRY_LIMIT +
                                        " us");
                        Thread.sleep(CodecTestBase.Q_DEQ_TIMEOUT_US / 1000);
                        retry ++;
                    }
                }
                Pair<Integer, MediaCodec.BufferInfo> element = mAsyncHandleEncoder.getOutput();
                if (element != null) {
                    dequeueEncoderOutput(element.first, element.second);
                }
            }
        } else {
            MediaCodec.BufferInfo outInfo = new MediaCodec.BufferInfo();
            if (!mSawEncOutputEOS) {
                int outputBufferId = mEncoder.dequeueOutputBuffer(outInfo, timeOutUs);
                if (outputBufferId >= 0) {
                    dequeueEncoderOutput(outputBufferId, outInfo);
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    mLatency = mEncoder.getOutputFormat()
                            .getInteger(MediaFormat.KEY_LATENCY, mLatency);
                }
            }
        }
    }

    private void waitForAllEncoderOutputs() throws InterruptedException {
        if (mIsCodecInAsyncMode) {
            while (!hasSeenError() && !mSawEncOutputEOS) {
                tryEncoderOutput(CodecTestBase.Q_DEQ_TIMEOUT_US);
            }
        } else {
            while (!mSawEncOutputEOS) {
                tryEncoderOutput(CodecTestBase.Q_DEQ_TIMEOUT_US);
            }
        }
    }

    private void queueEOS() throws InterruptedException {
        if (mIsCodecInAsyncMode) {
            while (!mAsyncHandleDecoder.hasSeenError() && !mSawDecInputEOS) {
                Pair<Integer, MediaCodec.BufferInfo> element = mAsyncHandleDecoder.getWork();
                if (element != null) {
                    int bufferID = element.first;
                    MediaCodec.BufferInfo info = element.second;
                    if (info != null) {
                        dequeueDecoderOutput(bufferID, info);
                    } else {
                        enqueueDecoderEOS(element.first);
                    }
                }
            }
        } else {
            MediaCodec.BufferInfo outInfo = new MediaCodec.BufferInfo();
            while (!mSawDecInputEOS) {
                int outputBufferId =
                        mDecoder.dequeueOutputBuffer(outInfo, CodecTestBase.Q_DEQ_TIMEOUT_US);
                if (outputBufferId >= 0) {
                    dequeueDecoderOutput(outputBufferId, outInfo);
                }
                int inputBufferId = mDecoder.dequeueInputBuffer(CodecTestBase.Q_DEQ_TIMEOUT_US);
                if (inputBufferId != -1) {
                    enqueueDecoderEOS(inputBufferId);
                }
            }
        }
        if (mIsCodecInAsyncMode) {
            while (!hasSeenError() && !mSawDecOutputEOS) {
                Pair<Integer, MediaCodec.BufferInfo> decOp = mAsyncHandleDecoder.getOutput();
                if (decOp != null) dequeueDecoderOutput(decOp.first, decOp.second);
                if (mSawDecOutputEOS) mEncoder.signalEndOfInputStream();
                if (mDecOutputCount - mEncOutputCount > mLatency) {
                    tryEncoderOutput(-1);
                }
            }
        } else {
            MediaCodec.BufferInfo outInfo = new MediaCodec.BufferInfo();
            while (!mSawDecOutputEOS) {
                int outputBufferId =
                        mDecoder.dequeueOutputBuffer(outInfo, CodecTestBase.Q_DEQ_TIMEOUT_US);
                if (outputBufferId >= 0) {
                    dequeueDecoderOutput(outputBufferId, outInfo);
                }
                if (mSawDecOutputEOS) mEncoder.signalEndOfInputStream();
                if (mDecOutputCount - mEncOutputCount > mLatency) {
                    tryEncoderOutput(-1);
                }
            }
        }
    }

    private void doWork(int frameLimit) throws InterruptedException {
        int frameCnt = 0;
        if (mIsCodecInAsyncMode) {
            // dequeue output after inputEOS is expected to be done in waitForAllOutputs()
            while (!hasSeenError() && !mSawDecInputEOS && frameCnt < frameLimit) {
                Pair<Integer, MediaCodec.BufferInfo> element = mAsyncHandleDecoder.getWork();
                if (element != null) {
                    int bufferID = element.first;
                    MediaCodec.BufferInfo info = element.second;
                    if (info != null) {
                        // <id, info> corresponds to output callback. Handle it accordingly
                        dequeueDecoderOutput(bufferID, info);
                    } else {
                        // <id, null> corresponds to input callback. Handle it accordingly
                        enqueueDecoderInput(bufferID);
                        frameCnt++;
                    }
                }
                // check decoder EOS
                if (mSawDecOutputEOS) mEncoder.signalEndOfInputStream();
                // encoder output
                if (mDecOutputCount - mEncOutputCount > mLatency) {
                    tryEncoderOutput(-1);
                }
            }
        } else {
            MediaCodec.BufferInfo outInfo = new MediaCodec.BufferInfo();
            while (!mSawDecInputEOS && frameCnt < frameLimit) {
                // decoder input
                int inputBufferId = mDecoder.dequeueInputBuffer(CodecTestBase.Q_DEQ_TIMEOUT_US);
                if (inputBufferId != -1) {
                    enqueueDecoderInput(inputBufferId);
                    frameCnt++;
                }
                // decoder output
                int outputBufferId =
                        mDecoder.dequeueOutputBuffer(outInfo, CodecTestBase.Q_DEQ_TIMEOUT_US);
                if (outputBufferId >= 0) {
                    dequeueDecoderOutput(outputBufferId, outInfo);
                }
                // check decoder EOS
                if (mSawDecOutputEOS) mEncoder.signalEndOfInputStream();
                // encoder output
                if (mDecOutputCount - mEncOutputCount > mLatency) {
                    tryEncoderOutput(-1);
                }
            }
        }
    }

    private MediaFormat setUpEncoderFormat(MediaFormat decoderFormat) {
        MediaFormat encoderFormat = new MediaFormat();
        encoderFormat.setString(MediaFormat.KEY_MIME, mMime);
        encoderFormat.setInteger(MediaFormat.KEY_WIDTH,
                decoderFormat.getInteger(MediaFormat.KEY_WIDTH));
        encoderFormat.setInteger(MediaFormat.KEY_HEIGHT,
                decoderFormat.getInteger(MediaFormat.KEY_HEIGHT));
        encoderFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mFrameRate);
        encoderFormat.setInteger(MediaFormat.KEY_BIT_RATE, mBitrate);
        encoderFormat.setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, 1.0f);
        encoderFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        encoderFormat.setInteger(MediaFormat.KEY_MAX_B_FRAMES, mMaxBFrames);
        return encoderFormat;
    }

    /**
     * Tests listed encoder components for sync and async mode in surface mode.The output has to
     * be consistent (not flaky) in all runs.
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testSimpleEncodeFromSurface() throws IOException, InterruptedException {
        mDecoder = MediaCodec.createByCodecName(mDecoderName);
        boolean muxOutput = true;
        {
            mEncoder = MediaCodec.createByCodecName(mCompName);
            /* TODO(b/149027258) */
            mSaveToMem = false;
            OutputManager ref = new OutputManager();
            OutputManager test = new OutputManager();
            int loopCounter = 0;
            boolean[] boolStates = {true, false};
            for (boolean isAsync : boolStates) {
                mExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                mOutputBuff = loopCounter == 0 ? ref : test;
                mOutputBuff.reset();
                if (muxOutput && loopCounter == 0) {
                    String tmpPath;
                    int muxerFormat;
                    if (mMime.equals(MediaFormat.MIMETYPE_VIDEO_VP8) ||
                            mMime.equals(MediaFormat.MIMETYPE_VIDEO_VP9)) {
                        muxerFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM;
                        tmpPath = File.createTempFile("tmp", ".webm").getAbsolutePath();
                    } else {
                        muxerFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4;
                        tmpPath = File.createTempFile("tmp", ".mp4").getAbsolutePath();
                    }
                    mMuxer = new MediaMuxer(tmpPath, muxerFormat);
                }
                configureCodec(mDecoderFormat, mEncoderFormat, isAsync, false);
                mEncoder.start();
                mDecoder.start();
                doWork(Integer.MAX_VALUE);
                queueEOS();
                waitForAllEncoderOutputs();
                if (muxOutput) {
                    if (mTrackID != -1) {
                        mMuxer.stop();
                        mTrackID = -1;
                    }
                    if (mMuxer != null) {
                        mMuxer.release();
                        mMuxer = null;
                    }
                }
                /* TODO(b/147348711) */
                if (false) mDecoder.stop();
                else mDecoder.reset();
                /* TODO(b/147348711) */
                if (false) mEncoder.stop();
                else mEncoder.reset();
                String log = String.format(
                        "format: %s \n codec: %s, file: %s, mode: %s:: ",
                        mEncoderFormat, mCompName, mTestFile, (isAsync ? "async" : "sync"));
                assertTrue(log + " unexpected error", !hasSeenError());
                assertTrue(log + "no input sent", 0 != mDecInputCount);
                assertTrue(log + "no decoder output received", 0 != mDecOutputCount);
                assertTrue(log + "no encoder output received", 0 != mEncOutputCount);
                assertTrue(log + "decoder input count != output count, act/exp: " +
                        mDecOutputCount +
                        " / " + mDecInputCount, mDecInputCount == mDecOutputCount);
                /* TODO(b/153127506)
                 *  Currently disabling all encoder output checks. Added checks only for encoder
                 *  timeStamp is in increasing order or not.
                 *  Once issue is fixed remove increasing timestamp check and enable encoder checks.
                 */
                /*assertTrue(log + "encoder output count != decoder output count, act/exp: " +
                                mEncOutputCount + " / " + mDecOutputCount,
                        mEncOutputCount == mDecOutputCount);
                if (loopCounter != 0) {
                    assertTrue(log + "encoder output is flaky", ref.equals(test));
                } else {
                    assertTrue(log + " input pts list and output pts list are not identical",
                            ref.isOutPtsListIdenticalToInpPtsList((false)));
                }*/
                if (loopCounter != 0) {
                    assertTrue("test output pts is not strictly increasing",
                            test.isPtsStrictlyIncreasing(Long.MIN_VALUE));
                } else {
                    assertTrue("ref output pts is not strictly increasing",
                            ref.isPtsStrictlyIncreasing(Long.MIN_VALUE));
                }
                loopCounter++;
                mSurface.release();
                mSurface = null;
            }
            mEncoder.release();
        }
        mDecoder.release();
        mExtractor.release();
    }

    private native boolean nativeTestSimpleEncode(String encoder, String decoder, String mime,
            String testFile, String muxFile, int bitrate, int framerate, int colorFormat);

    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testSimpleEncodeFromSurfaceNative() throws IOException {
        {
            String tmpPath;
            if (mMime.equals(MediaFormat.MIMETYPE_VIDEO_VP8) ||
                    mMime.equals(MediaFormat.MIMETYPE_VIDEO_VP9)) {
                tmpPath = File.createTempFile("tmp", ".webm").getAbsolutePath();
            } else {
                tmpPath = File.createTempFile("tmp", ".mp4").getAbsolutePath();
            }
            int colorFormat = mDecoderFormat.getInteger(MediaFormat.KEY_COLOR_FORMAT, -1);
            assertTrue(nativeTestSimpleEncode(mCompName, mDecoderName, mMime,
                    mInpPrefix + mTestFile, tmpPath, mBitrate, mFrameRate, colorFormat));
        }
    }
}
