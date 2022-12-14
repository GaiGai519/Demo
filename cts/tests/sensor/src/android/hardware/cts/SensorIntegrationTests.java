/*
 * Copyright (C) 2013 The Android Open Source Project
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
package android.hardware.cts;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.cts.helpers.SensorCtsHelper;
import android.hardware.cts.helpers.SensorNotSupportedException;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.hardware.cts.helpers.sensoroperations.ParallelSensorOperation;
import android.hardware.cts.helpers.sensoroperations.RepeatingSensorOperation;
import android.hardware.cts.helpers.sensoroperations.SequentialSensorOperation;
import android.hardware.cts.helpers.sensoroperations.TestSensorOperation;
import android.hardware.cts.helpers.sensorverification.EventOrderingVerification;
import android.hardware.cts.helpers.sensorverification.FrequencyVerification;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Set of tests that verifies proper interaction of the sensors in the platform.
 *
 * To execute these test cases, the following command can be used:
 *      $ adb shell am instrument -e class android.hardware.cts.SensorIntegrationTests \
 *          -w android.hardware.cts/android.test.InstrumentationCtsTestRunner
 */
public class SensorIntegrationTests extends SensorTestCase {
    private static final String TAG = "SensorIntegrationTests";

    /**
     * This test focuses in the interaction of continuous and batching clients for the same Sensor
     * under test. The verification ensures that sensor clients can interact with the System and
     * not affect other clients in the way.
     *
     * The test verifies for each client that the a set of sampled data arrives in order. However
     * each client in the test has different set of parameters that represent different types of
     * clients in the real world.
     *
     * A test failure might indicate that the HAL implementation does not respect the assumption
     * that the sensors must be independent. Activating one sensor should not cause another sensor
     * to deactivate or to change behavior.
     * It is however, acceptable that when a client is activated at a higher sampling rate, it would
     * cause other clients to receive data at a faster sampling rate. A client causing other clients
     * to receive data at a lower sampling rate is, however, not acceptable.
     *
     * The assertion associated with the test failure provides:
     * - the thread id on which the failure occurred
     * - the sensor type and sensor handle that caused the failure
     * - the event that caused the issue
     * It is important to look at the internals of the Sensor HAL to identify how the interaction
     * of several clients can lead to the failing state.
     */
    public void testSensorsWithSeveralClients() throws Throwable {
        SensorCtsHelper.sleep(3, TimeUnit.SECONDS);
        final int ITERATIONS = 50;
        final int MAX_REPORTING_LATENCY_US = (int) TimeUnit.SECONDS.toMicros(5);
        final Context context = getContext();

        int sensorTypes[] = {
                Sensor.TYPE_ACCELEROMETER,
                Sensor.TYPE_MAGNETIC_FIELD,
                Sensor.TYPE_GYROSCOPE };

        ParallelSensorOperation operation = new ParallelSensorOperation();
        for(int sensorType : sensorTypes) {
            TestSensorEnvironment environment = new TestSensorEnvironment(
                    context,
                    sensorType,
                    shouldEmulateSensorUnderLoad(),
                    SensorManager.SENSOR_DELAY_FASTEST);
            TestSensorOperation continuousOperation =
                    TestSensorOperation.createOperation(environment, 100 /* eventCount */);
            continuousOperation.addVerification(new EventOrderingVerification());
            operation.add(new RepeatingSensorOperation(continuousOperation, ITERATIONS));

            Sensor sensor = TestSensorEnvironment.getSensor(context, sensorType);
            TestSensorEnvironment batchingEnvironment = new TestSensorEnvironment(
                    context,
                    sensorType,
                    shouldEmulateSensorUnderLoad(),
                    true, /* isIntegrationTest */
                    sensor.getMinDelay(),
                    MAX_REPORTING_LATENCY_US);
            TestSensorOperation batchingOperation =
                    TestSensorOperation.createOperation(batchingEnvironment, 100 /* eventCount */);
            batchingOperation.addVerification(new EventOrderingVerification());
            operation.add(new RepeatingSensorOperation(batchingOperation, ITERATIONS));
        }
        operation.execute(getCurrentTestNode());
        operation.getStats().log(TAG);
    }

    /**
     * This test focuses in the interaction of several sensor Clients. The test characterizes by
     * using clients for different Sensors under Test that vary the sampling rates and report
     * latencies for the requests.
     * The verification ensures that the sensor clients can vary the parameters of their requests
     * without affecting other clients.
     *
     * The test verifies for each client that a set of sampled data arrives in order. However each
     * client in the test has different set of parameters that represent different types of clients
     * in the real world.
     *
     * The test can be susceptible to issues when several clients interacting with the system
     * actually affect the operation of other clients.
     *
     * The assertion associated with the test failure provides:
     * - the thread id on which the failure occurred
     * - the sensor type and sensor handle that caused the failure
     * - the event that caused the issue
     * It is important to look at the internals of the Sensor HAL to identify how the interaction
     * of several clients can lead to the failing state.
     */
    public void testSensorsMovingRates() throws Throwable {
        SensorCtsHelper.sleep(3, TimeUnit.SECONDS);
        // use at least two instances to ensure more than one client of any given sensor is in play
        final int INSTANCES_TO_USE = 5;
        final int ITERATIONS_TO_EXECUTE = 100;

        ParallelSensorOperation operation = new ParallelSensorOperation();
        int sensorTypes[] = {
                Sensor.TYPE_ACCELEROMETER,
                Sensor.TYPE_MAGNETIC_FIELD,
                Sensor.TYPE_GYROSCOPE };

        Context context = getContext();
        for(int sensorType : sensorTypes) {
            for(int instance = 0; instance < INSTANCES_TO_USE; ++instance) {
                SequentialSensorOperation sequentialOperation = new SequentialSensorOperation();
                for(int iteration = 0; iteration < ITERATIONS_TO_EXECUTE; ++iteration) {
                    TestSensorEnvironment environment = new TestSensorEnvironment(
                            context,
                            sensorType,
                            shouldEmulateSensorUnderLoad(),
                            true, /* isIntegrationTest */
                            generateSamplingRateInUs(sensorType),
                            generateReportLatencyInUs());
                    TestSensorOperation sensorOperation =
                            TestSensorOperation.createOperation(environment, 100 /* eventCount */);
                    sensorOperation.addVerification(new EventOrderingVerification());
                    sequentialOperation.add(sensorOperation);
                }
                operation.add(sequentialOperation);
            }
        }

        operation.execute(getCurrentTestNode());
        operation.getStats().log(TAG);
    }

    public void testAccelerometerReconfigureWhileActive() throws Throwable {
        verifySensorReconfigureWhileActive(Sensor.TYPE_ACCELEROMETER);
    }

    public void testUncalibratedAccelerometerReconfigureWhileActive() throws Throwable {
        verifySensorReconfigureWhileActive(Sensor.TYPE_ACCELEROMETER_UNCALIBRATED);
    }

    public void testMagneticFieldReconfigureWhileActive() throws Throwable {
        verifySensorReconfigureWhileActive(Sensor.TYPE_MAGNETIC_FIELD);
    }

    public void testUncalibratedMagneticFieldReconfigureWhileActive() throws Throwable {
        verifySensorReconfigureWhileActive(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED);
    }

    public void testOrientationReconfigureWhileActive() throws Throwable {
        verifySensorReconfigureWhileActive(Sensor.TYPE_ORIENTATION);
    }

    public void testGyroscopeReconfigureWhileActive() throws Throwable {
        verifySensorReconfigureWhileActive(Sensor.TYPE_GYROSCOPE);
    }

    public void testUncalibratedGyroscopeReconfigureWhileActive() throws Throwable {
        verifySensorReconfigureWhileActive(Sensor.TYPE_GYROSCOPE_UNCALIBRATED);
    }

    public void testPressureReconfigureWhileActive() throws Throwable {
        verifySensorReconfigureWhileActive(Sensor.TYPE_PRESSURE);
    }

    public void testGravityReconfigureWhileActive() throws Throwable {
        verifySensorReconfigureWhileActive(Sensor.TYPE_GRAVITY);
    }

    public void testLinearAccelerationReconfigureWhileActive() throws Throwable {
        verifySensorReconfigureWhileActive(Sensor.TYPE_LINEAR_ACCELERATION);
    }

    public void testRotationVectorReconfigureWhileActive() throws Throwable {
        verifySensorReconfigureWhileActive(Sensor.TYPE_ROTATION_VECTOR);
    }

    public void testGameRotationVectorReconfigureWhileActive() throws Throwable {
        verifySensorReconfigureWhileActive(Sensor.TYPE_GAME_ROTATION_VECTOR);
    }

    public void testGeomagneticRotationVectorReconfigureWhileActive() throws Throwable {
        verifySensorReconfigureWhileActive(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR);
    }

    public void testAccelerometerLimitedAxesReconfigureWhileActive() throws Throwable {
        verifySensorReconfigureWhileActive(Sensor.TYPE_ACCELEROMETER_LIMITED_AXES);
    }

    public void testAccelerometerLimitedAxesUncalibratedReconfigureWhileActive() throws Throwable {
        verifySensorReconfigureWhileActive(Sensor.TYPE_ACCELEROMETER_LIMITED_AXES_UNCALIBRATED);
    }

    public void testGyroscopeLimitedAxesReconfigureWhileActive() throws Throwable {
        verifySensorReconfigureWhileActive(Sensor.TYPE_GYROSCOPE_LIMITED_AXES);
    }

    public void testGyroscopeLimitedAxesUncalibratedReconfigureWhileActive() throws Throwable {
        verifySensorReconfigureWhileActive(Sensor.TYPE_GYROSCOPE_LIMITED_AXES_UNCALIBRATED);
    }

    /**
     * This test focuses on ensuring that an active sensor is able to be reconfigured when a new
     * client requests a different sampling rate.
     *
     * The test verifies that if a sensor is active with a slow sampling rate and a new client
     * requests a faster sampling rate, the sensor begins returning data at the faster sampling
     * rate.
     *
     * The assertion associated with the test failure provides:
     * - the thread id on which the failure occurred
     * - the sensor type and sensor handle that caused the failure
     * - the event that caused the issue
     * It is important to look at the internals of the Sensor HAL to identify how the interaction
     * of several clients can lead to the failing state.
     */
    public void verifySensorReconfigureWhileActive(int sensorType) throws Throwable {
        SensorCtsHelper.sleep(3, TimeUnit.SECONDS);

        final int DELAY_BEFORE_CHANGING_RATE_SEC = 2;
        final int EVENTS_FOR_VERIFICATION = 200;
        Context context = getContext();
        SensorManager sensorManager =
                (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        assertNotNull("SensorService is not present in the system", sensorManager);

        Sensor sensor = sensorManager.getDefaultSensor(sensorType);
        if(sensor == null) {
            throw new SensorNotSupportedException(sensorType);
        }

        // Request for the sensor rate to be set to the slowest rate.
        ParallelSensorOperation operation = new ParallelSensorOperation();
        TestSensorEnvironment environmentSlow = new TestSensorEnvironment(
                context,
                sensor,
                shouldEmulateSensorUnderLoad(),
                true, /* isIntegrationTest */
                sensor.getMaxDelay(),
                (int)TimeUnit.SECONDS.toMicros(20));
        TestSensorOperation sensorOperationSlow = TestSensorOperation.createOperation(
                environmentSlow, 2 * DELAY_BEFORE_CHANGING_RATE_SEC, TimeUnit.SECONDS);
        operation.add(sensorOperationSlow);

        // Create a second operation that will run in parallel and request the fastest rate after
        // an initial delay. The delay is to ensure that the first operation has enabled the sensor.
        // The sensor should begin reporting at the newly requested rate. Execute a flush prior to
        // the reconfiguration to ensure that the lower frequency events are not received after the
        // reconfiguration of the sensor.
        SequentialSensorOperation sequentialSensorOperation = new SequentialSensorOperation();
        TestSensorEnvironment environmentFast = new TestSensorEnvironment(
                context,
                sensor,
                shouldEmulateSensorUnderLoad(),
                true, /* isIntegrationTest */
                sensor.getMinDelay(),
                0 /* max reporting latency */);

        // Create the flush operation with a delay to ensure the low frequency configuration was
        // handled and executed. Use the original environment since the flush operation will
        // register a new listener and reconfigure the sensor.
        TestSensorOperation flushOperation = TestSensorOperation.createFlushOperation(
                environmentSlow, DELAY_BEFORE_CHANGING_RATE_SEC, TimeUnit.SECONDS);
        sequentialSensorOperation.add(flushOperation);

        // Create the reconfiguration request and add it after the flush
        TestSensorOperation sensorOperationFast = TestSensorOperation.createOperation(
                environmentFast, EVENTS_FOR_VERIFICATION);
        sensorOperationFast.addVerification(FrequencyVerification.getDefault(environmentFast));
        sequentialSensorOperation.add(sensorOperationFast);

        // Add the sequential operation containing the flush and high frequency request to the
        // existing parallel operation that already contains the low frequency request.
        operation.add(sequentialSensorOperation);
        operation.execute(getCurrentTestNode());
        operation.getStats().log(TAG);
    }

    /**
     * Regress:
     * - b/10641388
     */

    public void testAccelerometerAccelerometerStopping()  throws Throwable {
        verifySensorStoppingInteraction(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_ACCELEROMETER);
    }

    public void testAccelerometerGyroscopeStopping()  throws Throwable {
        verifySensorStoppingInteraction(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE);
    }

    public void testAccelerometerMagneticFieldStopping()  throws Throwable {
        verifySensorStoppingInteraction(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_MAGNETIC_FIELD);
    }

    public void testGyroscopeAccelerometerStopping()  throws Throwable {
        verifySensorStoppingInteraction(Sensor.TYPE_GYROSCOPE, Sensor.TYPE_ACCELEROMETER);
    }

    public void testGyroscopeGyroscopeStopping()  throws Throwable {
        verifySensorStoppingInteraction(Sensor.TYPE_GYROSCOPE, Sensor.TYPE_GYROSCOPE);
    }

    public void testGyroscopeMagneticFieldStopping()  throws Throwable {
        verifySensorStoppingInteraction(Sensor.TYPE_GYROSCOPE, Sensor.TYPE_MAGNETIC_FIELD);
    }

    public void testMagneticFieldAccelerometerStopping()  throws Throwable {
        verifySensorStoppingInteraction(Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_ACCELEROMETER);
    }

    public void testMagneticFieldGyroscopeStopping()  throws Throwable {
        verifySensorStoppingInteraction(Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_GYROSCOPE);
    }

    public void testMagneticFieldMagneticFieldStopping()  throws Throwable {
        verifySensorStoppingInteraction(Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_MAGNETIC_FIELD);
    }

    public void testAccelerometerLimitedAxesAccelerometerLimitedAxesStopping()  throws Throwable {
        verifySensorStoppingInteraction(Sensor.TYPE_ACCELEROMETER_LIMITED_AXES,
                Sensor.TYPE_ACCELEROMETER_LIMITED_AXES);
    }

    public void testAccelerometerLimitedAxesGyroscopeLimitedAxesStopping()  throws Throwable {
        verifySensorStoppingInteraction(Sensor.TYPE_ACCELEROMETER_LIMITED_AXES,
                Sensor.TYPE_GYROSCOPE_LIMITED_AXES);
    }

    public void testAccelerometerLimitedAxesMagneticFieldStopping()  throws Throwable {
        verifySensorStoppingInteraction(Sensor.TYPE_ACCELEROMETER_LIMITED_AXES,
                Sensor.TYPE_MAGNETIC_FIELD);
    }

    public void testGyroscopeLimitedAxesAccelerometerLimitedAxesStopping()  throws Throwable {
        verifySensorStoppingInteraction(Sensor.TYPE_GYROSCOPE_LIMITED_AXES,
                Sensor.TYPE_ACCELEROMETER_LIMITED_AXES);
    }

    public void testGyroscopeLimitedAxesGyroscopeLimitedAxesStopping()  throws Throwable {
        verifySensorStoppingInteraction(Sensor.TYPE_GYROSCOPE_LIMITED_AXES,
                Sensor.TYPE_GYROSCOPE_LIMITED_AXES);
    }

    public void testGyroscopeLimitedAxesMagneticFieldStopping()  throws Throwable {
        verifySensorStoppingInteraction(Sensor.TYPE_GYROSCOPE_LIMITED_AXES,
                Sensor.TYPE_MAGNETIC_FIELD);
    }

    public void testMagneticFieldAccelerometerLimitedAxesStopping()  throws Throwable {
        verifySensorStoppingInteraction(Sensor.TYPE_MAGNETIC_FIELD,
                Sensor.TYPE_ACCELEROMETER_LIMITED_AXES);
    }

    public void testMagneticFieldGyroscopeLimitedAxesStopping()  throws Throwable {
        verifySensorStoppingInteraction(Sensor.TYPE_MAGNETIC_FIELD,
                Sensor.TYPE_GYROSCOPE_LIMITED_AXES);
    }

    /**
     * This test verifies that starting/stopping a particular Sensor client in the System does not
     * affect other sensor clients.
     * the test is used to validate that starting/stopping operations are independent on several
     * sensor clients.
     *
     * The test verifies for each client that the a set of sampled data arrives in order. However
     * each client in the test has different set of parameters that represent different types of
     * clients in the real world.
     *
     * The test can be susceptible to issues when several clients interacting with the system
     * actually affect the operation of other clients.
     *
     * The assertion associated with the test failure provides:
     * - the thread id on which the failure occurred
     * - the sensor type and sensor handle that caused the failure
     * - the event that caused the issue
     * It is important to look at the internals of the Sensor HAL to identify how the interaction
     * of several clients can lead to the failing state.
     */
    public void verifySensorStoppingInteraction(
            int sensorTypeTestee,
            int sensorTypeTester) throws Throwable {
        SensorCtsHelper.sleep(3, TimeUnit.SECONDS);
        Context context = getContext();

        TestSensorEnvironment testerEnvironment = new TestSensorEnvironment(
                context,
                sensorTypeTester,
                shouldEmulateSensorUnderLoad(),
                SensorManager.SENSOR_DELAY_FASTEST);
        TestSensorOperation tester =
                TestSensorOperation.createOperation(testerEnvironment, 100 /* event count */);
        tester.addVerification(new EventOrderingVerification());

        TestSensorEnvironment testeeEnvironment = new TestSensorEnvironment(
                context,
                sensorTypeTestee,
                shouldEmulateSensorUnderLoad(),
                SensorManager.SENSOR_DELAY_FASTEST);
        TestSensorOperation testee =
                TestSensorOperation.createOperation(testeeEnvironment, 100 /* event count */);
        testee.addVerification(new EventOrderingVerification());

        ParallelSensorOperation operation = new ParallelSensorOperation();
        operation.add(tester, testee);
        operation.execute(getCurrentTestNode());
        operation.getStats().log(TAG);

        testee = testee.clone();
        testee.execute(getCurrentTestNode());
        testee.getStats().log(TAG);
    }

    /**
     * Private helpers.
     */
    private final Random mGenerator = new Random();

    private int generateSamplingRateInUs(int sensorType) {
        int rate;
        switch(mGenerator.nextInt(5)) {
            case 0:
                rate = SensorManager.SENSOR_DELAY_FASTEST;
                break;
            default:
                Sensor sensor = TestSensorEnvironment.getSensor(getContext(), sensorType);
                int maxSamplingRate = sensor.getMinDelay();
                rate = maxSamplingRate * mGenerator.nextInt(10);
        }
        return rate;
    }

    private int generateReportLatencyInUs() {
        long reportLatencyUs = TimeUnit.SECONDS.toMicros(mGenerator.nextInt(5) + 1);
        return (int) reportLatencyUs;
    }
}
