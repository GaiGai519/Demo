/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.telephony.ims.cts;

import android.os.Bundle;
import android.telephony.ims.ImsCallProfile;
import android.telephony.ims.ImsStreamMediaProfile;
import android.telephony.ims.RtpHeaderExtensionType;
import android.telephony.ims.feature.CapabilityChangeRequest;
import android.telephony.ims.feature.MmTelFeature;
import android.telephony.ims.stub.ImsCallSessionImplBase;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.util.Log;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

public class TestMmTelFeature extends MmTelFeature {

    private final TestImsService.RemovedListener mRemovedListener;
    private final TestImsService.ReadyListener mReadyListener;
    private final TestImsService.CapabilitiesSetListener mCapSetListener;

    private static final String TAG = "CtsTestImsService";
    public static ConferenceHelper sConferenceHelper = new ConferenceHelper();

    private MmTelCapabilities mCapabilities =
            new MmTelCapabilities(MmTelCapabilities.CAPABILITY_TYPE_SMS);
    private TestImsSmsImpl mSmsImpl;
    private Set<RtpHeaderExtensionType> mOfferedRtpHeaderExtensionTypes;
    private CountDownLatch mOfferedRtpHeaderExtensionLatch = new CountDownLatch(1);
    private TestImsCallSessionImpl mCallSession;

    TestMmTelFeature(TestImsService.ReadyListener readyListener,
            TestImsService.RemovedListener removedListener,
            TestImsService.CapabilitiesSetListener setListener) {
        Log.d(TAG, "TestMmTelFeature with default constructor");
        mReadyListener = readyListener;
        mRemovedListener = removedListener;
        mCapSetListener = setListener;
        mSmsImpl = new TestImsSmsImpl();
        // Must set the state to READY in the constructor - onFeatureReady depends on the state
        // being ready.
        setFeatureState(STATE_READY);
    }

    TestMmTelFeature(TestImsService.ReadyListener readyListener,
            TestImsService.RemovedListener removedListener,
            TestImsService.CapabilitiesSetListener setListener, Executor executor) {
        super(executor);
        Log.d(TAG, "TestMmTelFeature with Executor constructor");
        mReadyListener = readyListener;
        mRemovedListener = removedListener;
        mCapSetListener = setListener;
        mSmsImpl = new TestImsSmsImpl();
        // Must set the state to READY in the constructor - onFeatureReady depends on the state
        // being ready.
        setFeatureState(STATE_READY);
    }

    public TestImsSmsImpl getSmsImplementation() {
        return mSmsImpl;
    }

    @Override
    public boolean queryCapabilityConfiguration(int capability, int radioTech) {
        if (ImsUtils.VDBG) {
            Log.d(TAG, "queryCapabilityConfiguration called with capability: " + capability);
        }
        return mCapabilities.isCapable(capability);
    }

    @Override
    public void changeEnabledCapabilities(CapabilityChangeRequest request,
            CapabilityCallbackProxy c) {
        List<CapabilityChangeRequest.CapabilityPair> pairs = request.getCapabilitiesToEnable();
        for (CapabilityChangeRequest.CapabilityPair pair : pairs) {
            if (pair.getRadioTech() == ImsRegistrationImplBase.REGISTRATION_TECH_LTE) {
                mCapabilities.addCapabilities(pair.getCapability());
            }
        }
        pairs = request.getCapabilitiesToDisable();
        for (CapabilityChangeRequest.CapabilityPair pair : pairs) {
            if (pair.getRadioTech() == ImsRegistrationImplBase.REGISTRATION_TECH_LTE) {
                mCapabilities.removeCapabilities(pair.getCapability());
            }
        }
        mCapSetListener.onSet();
    }

    @Override
    public void onFeatureReady() {
        if (ImsUtils.VDBG) {
            Log.d(TAG, "TestMmTelFeature.onFeatureReady called");
        }
        mReadyListener.onReady();
    }

    @Override
    public void onFeatureRemoved() {
        if (ImsUtils.VDBG) {
            Log.d(TAG, "TestMmTelFeature.onFeatureRemoved called");
        }
        mRemovedListener.onRemoved();
    }

    @Override
    public void changeOfferedRtpHeaderExtensionTypes(Set<RtpHeaderExtensionType> extensionTypes) {
        mOfferedRtpHeaderExtensionTypes = extensionTypes;
        mOfferedRtpHeaderExtensionLatch.countDown();
    }

    @Override
    public ImsCallProfile createCallProfile(int serviceType, int callType) {
        ImsStreamMediaProfile mediaProfile = new ImsStreamMediaProfile(
                ImsStreamMediaProfile.AUDIO_QUALITY_AMR,
                ImsStreamMediaProfile.DIRECTION_INVALID,
                ImsStreamMediaProfile.VIDEO_QUALITY_NONE,
                ImsStreamMediaProfile.DIRECTION_INVALID,
                ImsStreamMediaProfile.RTT_MODE_DISABLED);
        ImsCallProfile profile = new ImsCallProfile(serviceType, callType,
                    new Bundle(), mediaProfile);
        return profile;
    }

    @Override
    public ImsCallSessionImplBase createCallSession(ImsCallProfile profile) {
        ImsCallSessionImplBase s = new TestImsCallSessionImpl(profile);
        mCallSession = (TestImsCallSessionImpl) s;
        onCallCreate(mCallSession);
        return s != null ? s : null;
    }

    public void setCapabilities(MmTelCapabilities capabilities) {
        mCapabilities = capabilities;
    }

    public MmTelCapabilities getCapabilities() {
        return mCapabilities;
    }

    public Set<RtpHeaderExtensionType> getOfferedRtpHeaderExtensionTypes() {
        return mOfferedRtpHeaderExtensionTypes;
    }

    public CountDownLatch getOfferedRtpHeaderExtensionLatch() {
        return mOfferedRtpHeaderExtensionLatch;
    }

    public TestImsCallSessionImpl getImsCallsession() {
        return mCallSession;
    }

    public boolean isCallSessionCreated() {
        return (mCallSession != null);
    }

    public void onIncomingCallReceived(Bundle extras) {
        Log.d(TAG, "onIncomingCallReceived");

        ImsStreamMediaProfile mediaProfile = new ImsStreamMediaProfile(
                ImsStreamMediaProfile.AUDIO_QUALITY_AMR,
                ImsStreamMediaProfile.DIRECTION_SEND_RECEIVE,
                ImsStreamMediaProfile.VIDEO_QUALITY_NONE,
                ImsStreamMediaProfile.DIRECTION_INVALID,
                ImsStreamMediaProfile.RTT_MODE_DISABLED);

        ImsCallProfile callProfile = new ImsCallProfile(ImsCallProfile.SERVICE_TYPE_NORMAL,
                ImsCallProfile.CALL_TYPE_VOICE, new Bundle(), mediaProfile);

        TestImsCallSessionImpl incomingSession = new TestImsCallSessionImpl(callProfile);
        mCallSession = incomingSession;

        Executor executor = incomingSession.getExecutor();
        executor.execute(() -> {
            notifyIncomingCall(incomingSession, extras);
        });
    }

    private void onCallCreate(TestImsCallSessionImpl session) {
        if (sConferenceHelper != null) {
            sConferenceHelper.addSession(session);
            session.setConferenceHelper(sConferenceHelper);
        }
    }

    public ConferenceHelper getConferenceHelper() {
        return sConferenceHelper;
    }
}
