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

package android.telecom.cts;

import static android.telecom.cts.ThirdPartyCallScreeningServiceTest.EXTRA_NETWORK_IDENTIFIED_EMERGENCY_CALL;

import android.net.Uri;
import android.os.Bundle;
import android.telecom.Conference;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.telecom.RemoteConference;
import android.telecom.RemoteConnection;
import android.telecom.TelecomManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Default implementation of a {@link CtsConnectionService}. This is used for the majority
 * of Telecom CTS tests that simply require that a outgoing call is placed, or incoming call is
 * received.
 */
public class MockConnectionService extends ConnectionService {
    public static final Uri VERSTAT_NOT_VERIFIED_NUMBER = Uri.fromParts("tel", "777", null);
    public static final Uri VERSTAT_PASSED_NUMBER = Uri.fromParts("tel", "555", null);
    public static final Uri VERSTAT_FAILED_NUMBER = Uri.fromParts("tel", "333", null);
    public static final String EXTRA_TEST = "com.android.telecom.extra.TEST";
    public static final String TEST_VALUE = "we've got it";
    public static final int CONNECTION_PRESENTATION =  TelecomManager.PRESENTATION_ALLOWED;

    public static final int EVENT_CONNECTION_SERVICE_FOCUS_GAINED = 0;
    public static final int EVENT_CONNECTION_SERVICE_FOCUS_LOST = 1;
    public static final int EVENT_CONNECTION_SERVICE_CREATE_CONNECTION = 2;
    public static final int EVENT_CONNECTION_SERVICE_CREATE_CONNECTION_FAILED = 3;
    // Update TOTAL_EVENT below with last event.
    private static final int TOTAL_EVENT = EVENT_CONNECTION_SERVICE_CREATE_CONNECTION_FAILED + 1;

    private static final int DEFAULT_EVENT_TIMEOUT_MS = 2000;

    private final Semaphore[] mEventLock = initializeSemaphore(TOTAL_EVENT);

    /**
     * Used to control whether the {@link MockVideoProvider} will be created when connections are
     * created.  Used by {@link VideoCallTest#testVideoCallDelayProvider()} to test scenario where
     * the {@link MockVideoProvider} is not created immediately when the Connection is created.
     */
    private boolean mCreateVideoProvider = true;

    public Semaphore lock = new Semaphore(0);
    public List<MockConnection> outgoingConnections = new ArrayList<MockConnection>();
    public List<MockConnection> incomingConnections = new ArrayList<MockConnection>();
    public List<RemoteConnection> remoteConnections = new ArrayList<RemoteConnection>();
    public List<MockConference> conferences = new ArrayList<MockConference>();
    public List<RemoteConference> remoteConferences = new ArrayList<RemoteConference>();
    public List<MockConnection> failedConnections = new ArrayList<>();
    public ConnectionRequest connectionRequest = null;

    @Override
    public Connection onCreateOutgoingConnection(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        final MockConnection connection = new MockConnection();
        connection.setAddress(request.getAddress(), CONNECTION_PRESENTATION);
        connection.setMockPhoneAccountHandle(connectionManagerPhoneAccount);
        connection.setConnectionCapabilities(Connection.CAPABILITY_SUPPORT_HOLD |
                Connection.CAPABILITY_HOLD
                | Connection.CAPABILITY_SUPPORTS_VT_LOCAL_BIDIRECTIONAL
                | Connection.CAPABILITY_SUPPORTS_VT_REMOTE_BIDIRECTIONAL);
        if (mCreateVideoProvider) {
            connection.createMockVideoProvider();
        } else {
            mCreateVideoProvider = true;
        }
        connection.setVideoState(request.getVideoState());
        connection.setInitializing();
        if (request.isRequestingRtt()) {
            connection.setRttTextStream(request.getRttTextStream());
            connection.setConnectionProperties(connection.getConnectionProperties() |
                    Connection.PROPERTY_IS_RTT);
        }
        Bundle testExtra;
        if (request.getExtras() != null) {
            testExtra = request.getExtras();
        } else {
            testExtra = new Bundle();
        }
        // Emit an extra into the connection.  We'll see if it makes it through.
        testExtra.putString(EXTRA_TEST, TEST_VALUE);
        connection.putExtras(testExtra);
        outgoingConnections.add(connection);
        lock.release();
        mEventLock[EVENT_CONNECTION_SERVICE_CREATE_CONNECTION].release();
        return connection;
    }

    @Override
    public Connection onCreateIncomingConnection(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        final MockConnection connection = new MockConnection();
        connection.setAddress(request.getAddress(), CONNECTION_PRESENTATION);
        connection.setConnectionCapabilities(connection.getConnectionCapabilities()
                | Connection.CAPABILITY_CAN_SEND_RESPONSE_VIA_CONNECTION
                | Connection.CAPABILITY_SUPPORT_HOLD
                | Connection.CAPABILITY_HOLD);
        connection.createMockVideoProvider();
        ((Connection) connection).setVideoState(request.getVideoState());
        if (request.isRequestingRtt()) {
            connection.setRttTextStream(request.getRttTextStream());
            connection.setConnectionProperties(connection.getConnectionProperties() |
                    Connection.PROPERTY_IS_RTT);
        }
        connection.setRinging();
        // Emit an extra into the connection.  We'll see if it makes it through.
        Bundle testExtra = new Bundle();
        testExtra.putString(EXTRA_TEST, TEST_VALUE);
        connection.putExtras(testExtra);
        if (VERSTAT_NOT_VERIFIED_NUMBER.equals(request.getAddress())) {
            connection.setCallerNumberVerificationStatus(
                    Connection.VERIFICATION_STATUS_NOT_VERIFIED);
        } else if (VERSTAT_PASSED_NUMBER.equals(request.getAddress())) {
            connection.setCallerNumberVerificationStatus(
                    Connection.VERIFICATION_STATUS_PASSED);
        } else if (VERSTAT_FAILED_NUMBER.equals(request.getAddress())) {
            connection.setCallerNumberVerificationStatus(
                    Connection.VERIFICATION_STATUS_FAILED);
        }

        Bundle requestExtra = request.getExtras();
        if (requestExtra.getBoolean(EXTRA_NETWORK_IDENTIFIED_EMERGENCY_CALL, false)) {
            connection.setConnectionProperties(
                    Connection.PROPERTY_NETWORK_IDENTIFIED_EMERGENCY_CALL);
        }
        incomingConnections.add(connection);
        lock.release();
        mEventLock[EVENT_CONNECTION_SERVICE_CREATE_CONNECTION].release();
        return connection;
    }

    @Override
    public void onCreateIncomingConnectionFailed(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        final MockConnection connection = new MockConnection();
        connection.setAddress(request.getAddress(), CONNECTION_PRESENTATION);
        connection.setPhoneAccountHandle(connectionManagerPhoneAccount);
        failedConnections.add(connection);
        lock.release();
        mEventLock[EVENT_CONNECTION_SERVICE_CREATE_CONNECTION_FAILED].release();
    }

    @Override
    public void onConference(Connection connection1, Connection connection2) {
        // Make sure that these connections are already not conferenced.
        if (connection1.getConference() == null && connection2.getConference() == null) {
            MockConference conference = new MockConference(
                    (MockConnection)connection1, (MockConnection)connection2);
            CtsConnectionService.addConferenceToTelecom(conference);
            conferences.add(conference);

            if (connection1.getState() == Connection.STATE_HOLDING){
                connection1.setActive();
            }
            if(connection2.getState() == Connection.STATE_HOLDING){
                connection2.setActive();
            }

            lock.release();
        }
    }
    @Override
    public Conference onCreateOutgoingConference(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        final Connection connection = onCreateOutgoingConnection(connectionManagerPhoneAccount,
                request);
        final MockConference conference = new MockConference(connectionManagerPhoneAccount);
        conference.addConnection(connection);
        conferences.add(conference);
        connectionRequest = request;
        lock.release();
        return conference;
    }

    @Override
    public void onCreateOutgoingConferenceFailed(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        final MockConference conference = new MockConference(connectionManagerPhoneAccount);
        conference.setDisconnected(new DisconnectCause(DisconnectCause.CANCELED));
        conferences.add(conference);
        connectionRequest = request;
        lock.release(2);
    }

    @Override
    public Conference onCreateIncomingConference(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        final Connection connection = onCreateIncomingConnection(connectionManagerPhoneAccount,
                request);
        final MockConference conference = new MockConference(connectionManagerPhoneAccount);
        conference.addConnection(connection);
        connectionRequest = request;
        conferences.add(conference);
        lock.release();
        return conference;
    }

    @Override
    public void onCreateIncomingConferenceFailed(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        final MockConference conference = new MockConference(connectionManagerPhoneAccount);
        conference.setDisconnected(new DisconnectCause(DisconnectCause.CANCELED));
        conferences.add(conference);
        connectionRequest = request;
        lock.release(2);
    }

    @Override
    public void onRemoteExistingConnectionAdded(RemoteConnection connection) {
        // Keep track of the remote connections added to the service
        remoteConnections.add(connection);
    }

    @Override
    public void onRemoteConferenceAdded(RemoteConference conference) {
        // Keep track of the remote connections added to the service
        remoteConferences.add(conference);
    }

    @Override
    public void onConnectionServiceFocusGained() {
        mEventLock[EVENT_CONNECTION_SERVICE_FOCUS_GAINED].release();
    }

    @Override
    public void onConnectionServiceFocusLost() {
        mEventLock[EVENT_CONNECTION_SERVICE_FOCUS_LOST].release();
        connectionServiceFocusReleased();
    }

    public void setCreateVideoProvider(boolean createVideoProvider) {
        mCreateVideoProvider = createVideoProvider;
    }

    /** Returns true if the given {@code event} is happened before the default timeout. */
    public boolean waitForEvent(int event) {
        if (event < 0 || event >= mEventLock.length) {
            return false;
        }

        try {
            return mEventLock[event].tryAcquire(DEFAULT_EVENT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // No interaction for the given event within the given timeout.
            return false;
        }
    }

    private static final Semaphore[] initializeSemaphore(int total) {
        Semaphore[] locks = new Semaphore[total];
        for (int i = 0; i < total; i++) {
            locks[i] = new Semaphore(0);
        }
        return locks;
    }
}
