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

package android.midi.cts;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.midi.MidiDevice;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiDeviceInfo.PortInfo;
import android.media.midi.MidiDeviceStatus;
import android.media.midi.MidiInputPort;
import android.media.midi.MidiManager;
import android.media.midi.MidiOutputPort;
import android.media.midi.MidiReceiver;
import android.os.Bundle;
import android.test.AndroidTestCase;
import android.util.Log;

import com.android.midi.CTSMidiEchoTestService;
import com.android.midi.MidiEchoTestService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Random;

/**
 * Test MIDI using a virtual MIDI device that echos input to output.
 */
public class MidiEchoTest extends AndroidTestCase {
    private static final String TAG = "MidiEchoTest";
    private static final boolean DEBUG = false;

    // I am overloading the timestamp for some tests. It is passed
    // directly through the Echo server unchanged.
    // The high 32-bits has a recognizable value.
    // The low 32-bits can contain data used to identify messages.
    private static final long TIMESTAMP_MARKER = 0x1234567800000000L;
    private static final long TIMESTAMP_MARKER_MASK = 0xFFFFFFFF00000000L;
    private static final long TIMESTAMP_DATA_MASK = 0x00000000FFFFFFFFL;
    private static final long NANOS_PER_MSEC = 1000L * 1000L;

    // On a fast device in 2016, the test fails if timeout is 3 but works if it is 4.
    // So this timeout value is very generous.
    private static final int TIMEOUT_OPEN_MSEC = 1000; // arbitrary
    // On a fast device in 2016, the test fails if timeout is 0 but works if it is 1.
    // So this timeout value is very generous.
    private static final int TIMEOUT_STATUS_MSEC = 500; // arbitrary

    // This is defined in MidiPortImpl.java as the maximum payload that
    // can be sent internally by MidiInputPort in a
    // SOCK_SEQPACKET datagram.
    private static final int MAX_PACKET_DATA_SIZE = 1024 - 9;

    // Store device and ports related to the Echo service.
    static class MidiTestContext {
        MidiDeviceInfo echoInfo;
        MidiDevice echoDevice;
        MidiInputPort echoInputPort;
        MidiOutputPort echoOutputPort;
    }

    // Store complete MIDI message so it can be put in an array.
    static class MidiMessage {
        public final byte[] data;
        public final long timestamp;
        public final long timeReceived;

        MidiMessage(byte[] buffer, int offset, int length, long timestamp) {
            timeReceived = System.nanoTime();
            data = new byte[length];
            System.arraycopy(buffer, offset, data, 0, length);
            this.timestamp = timestamp;
        }
    }

    // Listens for an asynchronous device open and notifies waiting foreground
    // test.
    class MyTestOpenCallback implements MidiManager.OnDeviceOpenedListener {
        MidiDevice mDevice;

        @Override
        public synchronized void onDeviceOpened(MidiDevice device) {
            mDevice = device;
            notifyAll();
        }

        public synchronized MidiDevice waitForOpen(int msec)
                throws InterruptedException {
            long deadline = System.currentTimeMillis() + msec;
            long timeRemaining = msec;
            while (mDevice == null && timeRemaining > 0) {
                wait(timeRemaining);
                timeRemaining = deadline - System.currentTimeMillis();
            }
            return mDevice;
        }
    }

    // Store received messages in an array.
    class MyLoggingReceiver extends MidiReceiver {
        ArrayList<MidiMessage> messages = new ArrayList<MidiMessage>();
        int mByteCount;

        @Override
        public synchronized void onSend(byte[] data, int offset, int count,
                long timestamp) {
            messages.add(new MidiMessage(data, offset, count, timestamp));
            mByteCount += count;
            notifyAll();
        }

        public synchronized int getMessageCount() {
            return messages.size();
        }

        public synchronized int getByteCount() {
            return mByteCount;
        }

        public synchronized MidiMessage getMessage(int index) {
            return messages.get(index);
        }

        /**
         * Wait until count messages have arrived. This is a cumulative total.
         *
         * @param count
         * @param timeoutMs
         * @throws InterruptedException
         */
        public synchronized void waitForMessages(int count, int timeoutMs)
                throws InterruptedException {
            long endTimeMs = System.currentTimeMillis() + timeoutMs + 1;
            long timeToWait = timeoutMs + 1;
            while ((getMessageCount() < count)
                    && (timeToWait > 0)) {
                wait(timeToWait);
                timeToWait = endTimeMs - System.currentTimeMillis();
            }
        }

        /**
         * Wait until count bytes have arrived. This is a cumulative total.
         *
         * @param count
         * @param timeoutMs
         * @throws InterruptedException
         */
        public synchronized void waitForBytes(int count, int timeoutMs)
                throws InterruptedException {
            long endTimeMs = System.currentTimeMillis() + timeoutMs + 1;
            long timeToWait = timeoutMs + 1;
            while ((getByteCount() < count)
                    && (timeToWait > 0)) {
                wait(timeToWait);
                timeToWait = endTimeMs - System.currentTimeMillis();
            }
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    protected MidiTestContext setUpEchoServer() throws Exception {
        if (DEBUG) {
            Log.i(TAG, "setUpEchoServer()");
        }
        MidiManager midiManager = (MidiManager) mContext.getSystemService(
                Context.MIDI_SERVICE);

        MidiDeviceInfo echoInfo = CTSMidiEchoTestService.findEchoDevice(mContext);

        // Open device.
        MyTestOpenCallback callback = new MyTestOpenCallback();
        midiManager.openDevice(echoInfo, callback, null);
        MidiDevice echoDevice = callback.waitForOpen(TIMEOUT_OPEN_MSEC);
        assertTrue("could not open "
                + CTSMidiEchoTestService.getEchoServerName(), echoDevice != null);

        // Query echo service directly to see if it is getting status updates.
        MidiEchoTestService echoService = CTSMidiEchoTestService.getInstance();
        assertEquals("virtual device status, input port before open", false,
                echoService.inputOpened);
        assertEquals("virtual device status, output port before open", 0,
                echoService.outputOpenCount);

        // Open input port.
        MidiInputPort echoInputPort = echoDevice.openInputPort(0);
        assertTrue("could not open input port", echoInputPort != null);
        assertEquals("input port number", 0, echoInputPort.getPortNumber());
        assertEquals("virtual device status, input port after open", true,
                echoService.inputOpened);
        assertEquals("virtual device status, output port before open", 0,
                echoService.outputOpenCount);

        // Open output port.
        MidiOutputPort echoOutputPort = echoDevice.openOutputPort(0);
        assertTrue("could not open output port", echoOutputPort != null);
        assertEquals("output port number", 0, echoOutputPort.getPortNumber());
        assertEquals("virtual device status, input port after open", true,
                echoService.inputOpened);
        assertEquals("virtual device status, output port after open", 1,
                echoService.outputOpenCount);

        MidiTestContext mc = new MidiTestContext();
        mc.echoInfo = echoInfo;
        mc.echoDevice = echoDevice;
        mc.echoInputPort = echoInputPort;
        mc.echoOutputPort = echoOutputPort;
        return mc;
    }

    /**
     * Close ports and check device status.
     *
     * @param mc
     */
    protected void tearDownEchoServer(MidiTestContext mc) throws IOException {
        // Query echo service directly to see if it is getting status updates.
        MidiEchoTestService echoService = CTSMidiEchoTestService.getInstance();
        assertEquals("virtual device status, input port before close", true,
                echoService.inputOpened);
        assertEquals("virtual device status, output port before close", 1,
                echoService.outputOpenCount);

        // Close output port.
        mc.echoOutputPort.close();
        assertEquals("virtual device status, input port before close", true,
                echoService.inputOpened);
        assertEquals("virtual device status, output port after close", 0,
                echoService.outputOpenCount);
        mc.echoOutputPort.close();
        mc.echoOutputPort.close(); // should be safe to close twice

        // Close input port.
        mc.echoInputPort.close();
        assertEquals("virtual device status, input port after close", false,
                echoService.inputOpened);
        assertEquals("virtual device status, output port after close", 0,
                echoService.outputOpenCount);
        mc.echoInputPort.close();
        mc.echoInputPort.close(); // should be safe to close twice

        mc.echoDevice.close();
        mc.echoDevice.close(); // should be safe to close twice
    }

    /**
     * @param mc
     * @param echoInfo
     */
    protected void checkEchoDeviceInfo(MidiTestContext mc,
            MidiDeviceInfo echoInfo) {
        assertEquals("echo input port count wrong", 1,
                echoInfo.getInputPortCount());
        assertEquals("echo output port count wrong", 1,
                echoInfo.getOutputPortCount());

        Bundle properties = echoInfo.getProperties();
        String tags = (String) properties.get("tags");
        assertEquals("attributes from device XML", "echo,test", tags);

        PortInfo[] ports = echoInfo.getPorts();
        assertEquals("port info array size", 2, ports.length);

        boolean foundInput = false;
        boolean foundOutput = false;
        for (PortInfo portInfo : ports) {
            if (portInfo.getType() == PortInfo.TYPE_INPUT) {
                foundInput = true;
                assertEquals("input port name", "input", portInfo.getName());

                assertEquals("info port number", portInfo.getPortNumber(),
                        mc.echoInputPort.getPortNumber());
            } else if (portInfo.getType() == PortInfo.TYPE_OUTPUT) {
                foundOutput = true;
                assertEquals("output port name", "output", portInfo.getName());
                assertEquals("info port number", portInfo.getPortNumber(),
                        mc.echoOutputPort.getPortNumber());
            }
        }
        assertTrue("found input port info", foundInput);
        assertTrue("found output port info", foundOutput);

        assertEquals("MIDI device type", MidiDeviceInfo.TYPE_VIRTUAL,
                echoInfo.getType());
        assertEquals("MIDI default protocol", MidiDeviceInfo.PROTOCOL_UNKNOWN,
                echoInfo.getDefaultProtocol());
    }

    // Is the MidiManager supported?
    public void testMidiManager() throws Exception {
        PackageManager pm = mContext.getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_MIDI)) {
            return; // Not supported so don't test it.
        }

        MidiManager midiManager = (MidiManager) mContext.getSystemService(
                Context.MIDI_SERVICE);
        assertTrue("MidiManager not supported.", midiManager != null);

        // There should be at least one device for the Echo server.
        MidiDeviceInfo[] infos = midiManager.getDevices();
        assertTrue("device list was null", infos != null);
        assertTrue("device list was empty", infos.length >= 1);

        Collection<MidiDeviceInfo> legacyDeviceInfos = midiManager.getDevicesForTransport(
                MidiManager.TRANSPORT_MIDI_BYTE_STREAM);
        assertTrue("Legacy Device list was null.", legacyDeviceInfos != null);
        assertTrue("Legacy Device list was empty", legacyDeviceInfos.size() >= 1);
        Collection<MidiDeviceInfo> universalDeviceInfos = midiManager.getDevicesForTransport(
                MidiManager.TRANSPORT_UNIVERSAL_MIDI_PACKETS);
        assertTrue("Universal Device list was null.", universalDeviceInfos != null);
    }

    public void testDeviceInfo() throws Exception {
        PackageManager pm = mContext.getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_MIDI)) {
            return; // Not supported so don't test it.
        }

        MidiTestContext mc = setUpEchoServer();
        checkEchoDeviceInfo(mc, mc.echoInfo);
        checkEchoDeviceInfo(mc, mc.echoDevice.getInfo());
        assertTrue("device info equal",
                mc.echoInfo.equals(mc.echoDevice.getInfo()));
        tearDownEchoServer(mc);
    }

    public void testEchoSmallMessage() throws Exception {
        checkEchoVariableMessage(3);
    }

    public void testEchoLargeMessage() throws Exception {
        checkEchoVariableMessage(MAX_PACKET_DATA_SIZE);
    }

    // This message will not fit in the internal buffer in MidiInputPort.
    // But it is still a legal size according to the API for
    // MidiReceiver.send(). It may be received in multiple packets.
    public void testEchoOversizeMessage() throws Exception {
        checkEchoVariableMessage(MAX_PACKET_DATA_SIZE + 20);
    }

    // Send a variable sized message. The actual
    // size will be a multiple of 3 because it sends NoteOns.
    public void checkEchoVariableMessage(int messageSize) throws Exception {
        PackageManager pm = mContext.getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_MIDI)) {
            return; // Not supported so don't test it.
        }

        MidiTestContext mc = setUpEchoServer();

        MyLoggingReceiver receiver = new MyLoggingReceiver();
        mc.echoOutputPort.connect(receiver);

        // Send an integral number of notes
        int numNotes = messageSize / 3;
        int noteSize = numNotes * 3;
        final byte[] buffer = new byte[noteSize];
        int index = 0;
        for (int i = 0; i < numNotes; i++) {
                buffer[index++] = (byte) (0x90 + (i & 0x0F)); // NoteOn
                buffer[index++] = (byte) 0x47; // Pitch
                buffer[index++] = (byte) 0x52; // Velocity
        };
        long timestamp = 0x0123765489ABFEDCL;

        mc.echoInputPort.send(buffer, 0, 0, timestamp); // should be a NOOP
        mc.echoInputPort.send(buffer, 0, buffer.length, timestamp);
        mc.echoInputPort.send(buffer, 0, 0, timestamp); // should be a NOOP

        // Wait for message to pass quickly through echo service.
        // Message sent may have been split into multiple received messages.
        // So wait until we receive all the expected bytes.
        final int numBytesExpected = buffer.length;
        final int timeoutMs = 20;
        synchronized (receiver) {
            receiver.waitForBytes(numBytesExpected, timeoutMs);
        }

        // Check total size.
        final int numReceived = receiver.getMessageCount();
        int totalBytesReceived = 0;
        for (int i = 0; i < numReceived; i++) {
            MidiMessage message = receiver.getMessage(i);
            totalBytesReceived += message.data.length;
            assertEquals("timestamp in message", timestamp, message.timestamp);
        }
        assertEquals("byte count of messages", numBytesExpected,
                totalBytesReceived);

        // Make sure the payload was not corrupted.
        int sentIndex = 0;
        for (int i = 0; i < numReceived; i++) {
            MidiMessage message = receiver.getMessage(i);
            for (int k = 0; k < message.data.length; k++) {
                assertEquals("message byte[" + i + "]",
                        buffer[sentIndex++] & 0x0FF,
                        message.data[k] & 0x0FF);
            }
        }

        mc.echoOutputPort.disconnect(receiver);
        tearDownEchoServer(mc);
    }

    public void testEchoLatency() throws Exception {
        PackageManager pm = mContext.getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_MIDI)) {
            return; // Not supported so don't test it.
        }

        MidiTestContext mc = setUpEchoServer();
        MyLoggingReceiver receiver = new MyLoggingReceiver();
        mc.echoOutputPort.connect(receiver);

        final int numMessages = 10;
        final int maxLatencyMs = 15; // generally < 3 msec on N6
        final long maxLatencyNanos = maxLatencyMs * NANOS_PER_MSEC;
        byte[] buffer = {
                (byte) 0x93, 0, 64
        };

        // Send multiple messages in a burst.
        for (int index = 0; index < numMessages; index++) {
            buffer[1] = (byte) (60 + index);
            mc.echoInputPort.send(buffer, 0, buffer.length, System.nanoTime());
        }

        // Wait for messages to pass quickly through echo service.
        final int timeoutMs = (numMessages * maxLatencyMs) + 20;
        synchronized (receiver) {
            receiver.waitForMessages(numMessages, timeoutMs);
        }
        assertEquals("number of messages.", numMessages, receiver.getMessageCount());

        for (int index = 0; index < numMessages; index++) {
            MidiMessage message = receiver.getMessage(index);
            assertEquals("message index", (byte) (60 + index), message.data[1]);
            long elapsedNanos = message.timeReceived - message.timestamp;
            // If this test fails then there may be a problem with the thread scheduler
            // or there may be kernel activity that is blocking execution at the user level.
            assertTrue("MIDI round trip latency[" + index + "] too large, " + elapsedNanos
                    + " nanoseconds",
                    (elapsedNanos < maxLatencyNanos));
        }

        mc.echoOutputPort.disconnect(receiver);
        tearDownEchoServer(mc);
    }

    public void testEchoMultipleMessages() throws Exception {
        PackageManager pm = mContext.getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_MIDI)) {
            return; // Not supported so don't test it.
        }

        MidiTestContext mc = setUpEchoServer();

        MyLoggingReceiver receiver = new MyLoggingReceiver();
        mc.echoOutputPort.connect(receiver);

        final byte[] buffer = new byte[2048];

        final int numMessages = 100;
        Random random = new Random(1972941337);
        int bytesSent = 0;
        byte value = 0;

        // Send various length messages with sequential bytes.
        long timestamp = TIMESTAMP_MARKER;
        for (int messageIndex = 0; messageIndex < numMessages; messageIndex++) {
            // Sweep numData across critical region of
            // MidiPortImpl.MAX_PACKET_DATA_SIZE
            int numData = 1000 + messageIndex;
            for (int dataIndex = 0; dataIndex < numData; dataIndex++) {
                buffer[dataIndex] = value;
                value++;
            }
            // This may get split into multiple sends internally.
            mc.echoInputPort.send(buffer, 0, numData, timestamp);
            bytesSent += numData;
            timestamp++;
        }

        // Check messages. Data must be sequential bytes.
        value = 0;
        int bytesReceived = 0;
        int messageReceivedIndex = 0;
        int messageSentIndex = 0;
        int expectedMessageSentIndex = 0;
        while (bytesReceived < bytesSent) {
            final int timeoutMs = 500;
            // Wait for next message.
            synchronized (receiver) {
                receiver.waitForMessages(messageReceivedIndex + 1, timeoutMs);
            }
            MidiMessage message = receiver.getMessage(messageReceivedIndex++);
            // parse timestamp marker and data
            long timestampMarker = message.timestamp & TIMESTAMP_MARKER_MASK;
            assertEquals("timestamp marker corrupted", TIMESTAMP_MARKER, timestampMarker);
            messageSentIndex = (int) (message.timestamp & TIMESTAMP_DATA_MASK);

            int numData = message.data.length;
            for (int dataIndex = 0; dataIndex < numData; dataIndex++) {
                String msg = String.format("message[%d/%d].data[%d/%d]",
                        messageReceivedIndex, messageSentIndex, dataIndex,
                        numData);
                assertEquals(msg, value, message.data[dataIndex]);
                value++;
            }
            bytesReceived += numData;
            // May not advance if message got split
            if (messageSentIndex > expectedMessageSentIndex) {
                expectedMessageSentIndex++; // only advance by one each message
            }
            assertEquals("timestamp in message", expectedMessageSentIndex,
                    messageSentIndex);
        }

        mc.echoOutputPort.disconnect(receiver);
        tearDownEchoServer(mc);
    }

    // What happens if the app does bad things.
    public void testEchoBadBehavior() throws Exception {
        PackageManager pm = mContext.getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_MIDI)) {
            return; // Not supported so don't test it.
        }
        MidiTestContext mc = setUpEchoServer();

        // This should fail because it is already open.
        MidiInputPort echoInputPort2 = mc.echoDevice.openInputPort(0);
        assertTrue("input port opened twice", echoInputPort2 == null);

        tearDownEchoServer(mc);
    }

    // Store history of status changes.
    private class MyDeviceCallback extends MidiManager.DeviceCallback {
        private volatile MidiDeviceStatus mStatus;
        private MidiDeviceInfo mInfo;

        public MyDeviceCallback(MidiDeviceInfo info) {
            mInfo = info;
        }

        @Override
        public synchronized void onDeviceStatusChanged(MidiDeviceStatus status) {
            super.onDeviceStatusChanged(status);
            // Filter out status reports from unrelated devices.
            if (mInfo.equals(status.getDeviceInfo())) {
                mStatus = status;
                notifyAll();
            }
        }

        // Wait for a timeout or a notify().
        // Return status message or a null if it times out.
        public synchronized MidiDeviceStatus waitForStatus(int msec)
                throws InterruptedException {
            long deadline = System.currentTimeMillis() + msec;
            long timeRemaining = msec;
            while (mStatus == null && timeRemaining > 0) {
                wait(timeRemaining);
                timeRemaining = deadline - System.currentTimeMillis();
            }
            return mStatus;
        }


        public synchronized void clear() {
            mStatus = null;
        }
    }

    // Test callback for onDeviceStatusChanged().
    public void testDeviceCallback() throws Exception {

        PackageManager pm = mContext.getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_MIDI)) {
            return; // Not supported so don't test it.
        }
        MidiManager midiManager = (MidiManager) mContext.getSystemService(
                Context.MIDI_SERVICE);

        MidiDeviceInfo echoInfo = CTSMidiEchoTestService.findEchoDevice(mContext);

        // Open device.
        MyTestOpenCallback callback = new MyTestOpenCallback();
        midiManager.openDevice(echoInfo, callback, null);
        MidiDevice echoDevice = callback.waitForOpen(TIMEOUT_OPEN_MSEC);
        assertTrue("could not open " + CTSMidiEchoTestService.getEchoServerName(), echoDevice != null);
        MyDeviceCallback deviceCallback = new MyDeviceCallback(echoInfo);
        try {

            midiManager.registerDeviceCallback(deviceCallback, null);

            MidiDeviceStatus status = deviceCallback.waitForStatus(TIMEOUT_STATUS_MSEC);
            // The DeviceStatus callback is supposed to be "sticky".
            // That means we expect to get the status of every device that is
            // already available when we register for the callback.
            // If it was not "sticky" then we would only get a callback when there
            // was a change in the available devices.
            // TODO Often this is null. But sometimes not. Why?
            if (status == null) {
                Log.d(TAG, "testDeviceCallback() first status was null!");
            } else {
                // InputPort should be closed because we have not opened it yet.
                assertEquals("input port should be closed before we open it.",
                             false, status.isInputPortOpen(0));
            }

            // Open input port.
            MidiInputPort echoInputPort = echoDevice.openInputPort(0);
            assertTrue("could not open input port", echoInputPort != null);

            status = deviceCallback.waitForStatus(TIMEOUT_STATUS_MSEC);
            assertTrue("should have status by now", null != status);
            assertEquals("input port should be open", true, status.isInputPortOpen(0));

            deviceCallback.clear();
            echoInputPort.close();
            status = deviceCallback.waitForStatus(TIMEOUT_STATUS_MSEC);
            assertTrue("should have status by now", null != status);
            assertEquals("input port should be closed", false, status.isInputPortOpen(0));

            // Make sure we do NOT get called after unregistering.
            midiManager.unregisterDeviceCallback(deviceCallback);
            deviceCallback.clear();
            echoInputPort = echoDevice.openInputPort(0);
            assertTrue("could not open input port", echoInputPort != null);

            status = deviceCallback.waitForStatus(TIMEOUT_STATUS_MSEC);
            assertEquals("should not get status after unregistering", null, status);

            echoInputPort.close();
        } finally {
            // Safe to call twice.
            midiManager.unregisterDeviceCallback(deviceCallback);
            echoDevice.close();
        }
    }
}
