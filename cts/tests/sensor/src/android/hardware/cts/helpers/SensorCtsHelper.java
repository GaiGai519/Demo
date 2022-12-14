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
package android.hardware.cts.helpers;

import android.hardware.Sensor;
import android.os.Environment;
import android.os.Process;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Set of static helper methods for CTS tests.
 */
//TODO: Refactor this class into several more well defined helper classes, look at StatisticsUtils
public class SensorCtsHelper {

    private static final long NANOS_PER_MILLI = 1000000;

    /**
     * Private constructor for static class.
     */
    private SensorCtsHelper() {}

    /**
     * Get low and high percentiles values of an array
     *
     * @param lowPercentile Lower boundary percentile, range [0, 1]
     * @param highPercentile Higher boundary percentile, range [0, 1]
     *
     * @throws IllegalArgumentException if the collection or percentiles is null or empty.
     */
    public static <TValue extends Comparable<? super TValue>> List<TValue> getPercentileValue(
            Collection<TValue> collection, float lowPecentile, float highPercentile) {
        validateCollection(collection);
        if (lowPecentile > highPercentile || lowPecentile < 0 || highPercentile > 1) {
            throw new IllegalStateException("percentile has to be in range [0, 1], and " +
                    "lowPecentile has to be less than or equal to highPercentile");
        }

        List<TValue> arrayCopy = new ArrayList<TValue>(collection);
        Collections.sort(arrayCopy);

        List<TValue> percentileValues = new ArrayList<TValue>();
        // lower percentile: rounding upwards, index range 1 .. size - 1 for percentile > 0
        // for percentile == 0, index will be 0.
        int lowArrayIndex = Math.min(arrayCopy.size() - 1,
                arrayCopy.size() - (int)(arrayCopy.size() * (1 - lowPecentile)));
        percentileValues.add(arrayCopy.get(lowArrayIndex));

        // upper percentile: rounding downwards, index range 0 .. size - 2 for percentile < 1
        // for percentile == 1, index will be size - 1.
        // Also, lower bound by lowerArrayIndex to avoid low percentile value being higher than
        // high percentile value.
        int highArrayIndex = Math.max(lowArrayIndex, (int)(arrayCopy.size() * highPercentile - 1));
        percentileValues.add(arrayCopy.get(highArrayIndex));
        return percentileValues;
    }

    /**
     * Calculate the mean of a collection.
     *
     * @throws IllegalArgumentException if the collection is null or empty
     */
    public static <TValue extends Number> double getMean(Collection<TValue> collection) {
        validateCollection(collection);

        double sum = 0.0;
        for(TValue value : collection) {
            sum += value.doubleValue();
        }
        return sum / collection.size();
    }

    /**
     * Calculate the bias-corrected sample variance of a collection.
     *
     * @throws IllegalArgumentException if the collection is null or empty
     */
    public static <TValue extends Number> double getVariance(Collection<TValue> collection) {
        validateCollection(collection);

        double mean = getMean(collection);
        ArrayList<Double> squaredDiffs = new ArrayList<Double>();
        for(TValue value : collection) {
            double difference = mean - value.doubleValue();
            squaredDiffs.add(Math.pow(difference, 2));
        }

        double sum = 0.0;
        for (Double value : squaredDiffs) {
            sum += value;
        }
        return sum / (squaredDiffs.size() - 1);
    }

    /**
     * @return The (measured) sampling rate of a collection of {@link TestSensorEvent}.
     */
    public static long getSamplingPeriodNs(List<TestSensorEvent> collection) {
        int collectionSize = collection.size();
        if (collectionSize < 2) {
            return 0;
        }
        TestSensorEvent firstEvent = collection.get(0);
        TestSensorEvent lastEvent = collection.get(collectionSize - 1);
        return (lastEvent.timestamp - firstEvent.timestamp) / (collectionSize - 1);
    }

    /**
     * Calculate the bias-corrected standard deviation of a collection.
     *
     * @throws IllegalArgumentException if the collection is null or empty
     */
    public static <TValue extends Number> double getStandardDeviation(
            Collection<TValue> collection) {
        return Math.sqrt(getVariance(collection));
    }

    /**
     * Convert a period to frequency in Hz.
     */
    public static <TValue extends Number> double getFrequency(TValue period, TimeUnit unit) {
        return 1000000000 / (TimeUnit.NANOSECONDS.convert(1, unit) * period.doubleValue());
    }

    /**
     * Convert a frequency in Hz into a period.
     */
    public static <TValue extends Number> double getPeriod(TValue frequency, TimeUnit unit) {
        return 1000000000 / (TimeUnit.NANOSECONDS.convert(1, unit) * frequency.doubleValue());
    }

    /**
     * If value lies outside the boundary limit, then return the nearer bound value.
     * Otherwise, return the value unchanged.
     */
    public static <TValue extends Number> double clamp(TValue val, TValue min, TValue max) {
        return Math.min(max.doubleValue(), Math.max(min.doubleValue(), val.doubleValue()));
    }

    /**
     * @return The magnitude (norm) represented by the given array of values.
     */
    public static double getMagnitude(float[] values) {
        float sumOfSquares = 0.0f;
        for (float value : values) {
            sumOfSquares += value * value;
        }
        double magnitude = Math.sqrt(sumOfSquares);
        return magnitude;
    }

    /**
     * Helper method to sleep for a given duration.
     */
    public static void sleep(long duration, TimeUnit timeUnit) throws InterruptedException {
        long durationNs = TimeUnit.NANOSECONDS.convert(duration, timeUnit);
        Thread.sleep(durationNs / NANOS_PER_MILLI, (int) (durationNs % NANOS_PER_MILLI));
    }

    /**
     * Format an assertion message.
     *
     * @param label the verification name
     * @param environment the environment of the test
     *
     * @return The formatted string
     */
    public static String formatAssertionMessage(String label, TestSensorEnvironment environment) {
        return formatAssertionMessage(label, environment, "Failed");
    }

    /**
     * Format an assertion message with a custom message.
     *
     * @param label the verification name
     * @param environment the environment of the test
     * @param format the additional format string
     * @param params the additional format params
     *
     * @return The formatted string
     */
    public static String formatAssertionMessage(
            String label,
            TestSensorEnvironment environment,
            String format,
            Object ... params) {
        return formatAssertionMessage(label, environment, String.format(format, params));
    }

    /**
     * Format an assertion message.
     *
     * @param label the verification name
     * @param environment the environment of the test
     * @param extras the additional information for the assertion
     *
     * @return The formatted string
     */
    public static String formatAssertionMessage(
            String label,
            TestSensorEnvironment environment,
            String extras) {
        return String.format(
                "%s | sensor='%s', samplingPeriod=%dus, maxReportLatency=%dus | %s",
                label,
                environment.getSensor().getName(),
                environment.getRequestedSamplingPeriodUs(),
                environment.getMaxReportLatencyUs(),
                extras);
    }

    /**
     * Format an array of floats.
     *
     * @param array the array of floats
     *
     * @return The formatted string
     */
    public static String formatFloatArray(float[] array) {
        StringBuilder sb = new StringBuilder();
        if (array.length > 1) {
            sb.append("(");
        }
        for (int i = 0; i < array.length; i++) {
            sb.append(String.format("%.8f", array[i]));
            if (i != array.length - 1) {
                sb.append(", ");
            }
        }
        if (array.length > 1) {
            sb.append(")");
        }
        return sb.toString();
    }

    /**
     * @return A {@link File} representing a root directory to store sensor tests data.
     */
    public static File getSensorTestDataDirectory() throws IOException {
        File dataDirectory = new File(Environment.getExternalStorageDirectory(), "sensorTests/");
        return createDirectoryStructure(dataDirectory);
    }

    /**
     * Creates the directory structure for the given sensor test data sub-directory.
     *
     * @param subdirectory The sub-directory's name.
     */
    public static File getSensorTestDataDirectory(String subdirectory) throws IOException {
        File subdirectoryFile = new File(getSensorTestDataDirectory(), subdirectory);
        return createDirectoryStructure(subdirectoryFile);
    }

    /**
     * Sanitizes a string so it can be used in file names.
     *
     * @param value The string to sanitize.
     * @return The sanitized string.
     *
     * @throws SensorTestPlatformException If the string cannot be sanitized.
     */
    public static String sanitizeStringForFileName(String value)
            throws SensorTestPlatformException {
        String sanitizedValue = value.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        if (sanitizedValue.matches("_*")) {
            throw new SensorTestPlatformException(
                    "Unable to sanitize string '%s' for file name.",
                    value);
        }
        return sanitizedValue;
    }

    /**
     * Ensures that the directory structure represented by the given {@link File} is created.
     */
    private static File createDirectoryStructure(File directoryStructure) throws IOException {
        directoryStructure.mkdirs();
        if (!directoryStructure.isDirectory()) {
            throw new IOException("Unable to create directory structure for "
                    + directoryStructure.getAbsolutePath());
        }
        return directoryStructure;
    }

    /**
     * Validate that a collection is not null or empty.
     *
     * @throws IllegalStateException if collection is null or empty.
     */
    private static <T> void validateCollection(Collection<T> collection) {
        if(collection == null || collection.size() == 0) {
            throw new IllegalStateException("Collection cannot be null or empty");
        }
    }

    public static String getUnitsForSensor(Sensor sensor) {
        switch(sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
            case Sensor.TYPE_ACCELEROMETER_LIMITED_AXES:
            case Sensor.TYPE_ACCELEROMETER_LIMITED_AXES_UNCALIBRATED:
                return "m/s^2";
            case Sensor.TYPE_MAGNETIC_FIELD:
            case Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED:
                return "uT";
            case Sensor.TYPE_GYROSCOPE:
            case Sensor.TYPE_GYROSCOPE_UNCALIBRATED:
            case Sensor.TYPE_GYROSCOPE_LIMITED_AXES:
            case Sensor.TYPE_GYROSCOPE_LIMITED_AXES_UNCALIBRATED:
                return "radians/sec";
            case Sensor.TYPE_PRESSURE:
                return "hPa";
        };
        return "";
    }

    public static boolean hasMaxResolutionRequirement(Sensor sensor, boolean hasHifiSensors) {
        switch (sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
            case Sensor.TYPE_ACCELEROMETER_UNCALIBRATED:
            case Sensor.TYPE_ACCELEROMETER_LIMITED_AXES:
            case Sensor.TYPE_ACCELEROMETER_LIMITED_AXES_UNCALIBRATED:
            case Sensor.TYPE_GYROSCOPE:
            case Sensor.TYPE_GYROSCOPE_UNCALIBRATED:
            case Sensor.TYPE_GYROSCOPE_LIMITED_AXES:
            case Sensor.TYPE_GYROSCOPE_LIMITED_AXES_UNCALIBRATED:
            case Sensor.TYPE_MAGNETIC_FIELD:
            case Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED:
            case Sensor.TYPE_HINGE_ANGLE:
            case Sensor.TYPE_PROXIMITY:
            case Sensor.TYPE_SIGNIFICANT_MOTION:
            case Sensor.TYPE_STEP_DETECTOR:
            case Sensor.TYPE_STEP_COUNTER:
            case Sensor.TYPE_HEART_RATE:
            case Sensor.TYPE_STATIONARY_DETECT:
            case Sensor.TYPE_MOTION_DETECT:
            case Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT:
                return true;

            case Sensor.TYPE_PRESSURE:
                // Pressure sensor only has a resolution requirement when there are HiFi sensors
                return hasHifiSensors;
        }
        return false;
    }

    public static float getRequiredMaxResolutionForSensor(Sensor sensor) {
        switch (sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
            case Sensor.TYPE_ACCELEROMETER_UNCALIBRATED:
            case Sensor.TYPE_ACCELEROMETER_LIMITED_AXES:
            case Sensor.TYPE_ACCELEROMETER_LIMITED_AXES_UNCALIBRATED:
            case Sensor.TYPE_GYROSCOPE:
            case Sensor.TYPE_GYROSCOPE_UNCALIBRATED:
            case Sensor.TYPE_GYROSCOPE_LIMITED_AXES:
            case Sensor.TYPE_GYROSCOPE_LIMITED_AXES_UNCALIBRATED:
                // Accelerometer and gyroscope must have at least 12 bits
                // of resolution. The maximum resolution calculation uses
                // slightly more than twice the maximum range because
                //   1) the sensor must be able to report values from
                //      [-maxRange, maxRange] without saturating
                //   2) to allow for slight rounding errors
                return (float)(2.001f * sensor.getMaximumRange() / Math.pow(2, 12));
            case Sensor.TYPE_MAGNETIC_FIELD:
            case Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED:
                // Magnetometer must have a resolution equal to or denser
                // than 0.6 uT
                return 0.6f;
            case Sensor.TYPE_PRESSURE:
                // Pressure sensor must have at least 80 LSB / hPa which is
                // equivalent to 0.0125 hPa / LSB. Allow for a small margin of
                // error due to rounding errors.
                return 1.01f * (1.0f / 80.0f);
            case Sensor.TYPE_HINGE_ANGLE:
                // Hinge angle sensor must have a resolution the same or smaller
                // than 360 degrees.
                return 360f;
            case Sensor.TYPE_PROXIMITY:
                // Binary prox sensors must have a resolution of 5, but it's not
                // expected / recommended that prox sensors use higher than
                // this.
                return 5f;
        }

        // Any sensor not specified above must use a resolution of 1.
        return 1.0f;
    }

    public static boolean hasMinResolutionRequirement(Sensor sensor) {
        switch (sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
            case Sensor.TYPE_ACCELEROMETER_UNCALIBRATED:
            case Sensor.TYPE_ACCELEROMETER_LIMITED_AXES:
            case Sensor.TYPE_ACCELEROMETER_LIMITED_AXES_UNCALIBRATED:
            case Sensor.TYPE_GYROSCOPE:
            case Sensor.TYPE_GYROSCOPE_UNCALIBRATED:
            case Sensor.TYPE_GYROSCOPE_LIMITED_AXES:
            case Sensor.TYPE_GYROSCOPE_LIMITED_AXES_UNCALIBRATED:
            case Sensor.TYPE_MAGNETIC_FIELD:
            case Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED:
            case Sensor.TYPE_SIGNIFICANT_MOTION:
            case Sensor.TYPE_STEP_DETECTOR:
            case Sensor.TYPE_STEP_COUNTER:
            case Sensor.TYPE_HEART_RATE:
            case Sensor.TYPE_STATIONARY_DETECT:
            case Sensor.TYPE_MOTION_DETECT:
            case Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT:
                return true;
        }
        return false;
    }

    public static float getRequiredMinResolutionForSensor(Sensor sensor) {
        switch (sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
            case Sensor.TYPE_ACCELEROMETER_UNCALIBRATED:
            case Sensor.TYPE_ACCELEROMETER_LIMITED_AXES:
            case Sensor.TYPE_ACCELEROMETER_LIMITED_AXES_UNCALIBRATED:
            case Sensor.TYPE_GYROSCOPE:
            case Sensor.TYPE_GYROSCOPE_UNCALIBRATED:
            case Sensor.TYPE_GYROSCOPE_LIMITED_AXES:
            case Sensor.TYPE_GYROSCOPE_LIMITED_AXES_UNCALIBRATED:
            case Sensor.TYPE_MAGNETIC_FIELD:
            case Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED:
                // Accelerometer, gyroscope, and mag are expected to have at most 24 bits of
                // resolution. The minimum resolution calculation uses slightly more than twice
                // the maximum range because:
                //   1) the sensor must be able to report values from [-maxRange, maxRange] without
                //      saturating
                //   2) to allow for slight rounding errors
                return (float)(2.001f * sensor.getMaximumRange() / Math.pow(2, 24));
        }

        // Any sensor not specified above must use a resolution of 1.
        return 1.0f;
    }

    public static String sensorTypeShortString(int type) {
        switch (type) {
            case Sensor.TYPE_ACCELEROMETER:
                return "Accel";
            case Sensor.TYPE_GYROSCOPE:
                return "Gyro";
            case Sensor.TYPE_MAGNETIC_FIELD:
                return "Mag";
            case Sensor.TYPE_ACCELEROMETER_UNCALIBRATED:
                return "UncalAccel";
            case Sensor.TYPE_GYROSCOPE_UNCALIBRATED:
                return "UncalGyro";
            case Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED:
                return "UncalMag";
            case Sensor.TYPE_ACCELEROMETER_LIMITED_AXES:
                return "LimitedAccel";
            case Sensor.TYPE_ACCELEROMETER_LIMITED_AXES_UNCALIBRATED:
                return "UncalLimitedAccel";
            case Sensor.TYPE_GYROSCOPE_LIMITED_AXES:
                return "LimitedGyro";
            case Sensor.TYPE_GYROSCOPE_LIMITED_AXES_UNCALIBRATED:
                return "UncalLimitedGyro";
            default:
                return "Type_" + type;
        }
    }

    public static class TestResultCollector {
        private List<AssertionError> mErrorList = new ArrayList<>();
        private List<String> mErrorStringList = new ArrayList<>();
        private String mTestName;
        private String mTag;

        public TestResultCollector() {
            this("Test");
        }

        public TestResultCollector(String test) {
            this(test, "SensorCtsTest");
        }

        public TestResultCollector(String test, String tag) {
            mTestName = test;
            mTag = tag;
        }

        public void perform(Runnable r) {
            perform(r, "");
        }

        public void perform(Runnable r, String s) {
            try {
                Log.d(mTag, mTestName + " running " + (s.isEmpty() ? "..." : s));
                r.run();
            } catch (AssertionError e) {
                mErrorList.add(e);
                mErrorStringList.add(s);
                Log.e(mTag, mTestName + " error: " + e.getMessage());
            }
        }

        public void judge() throws AssertionError {
            if (mErrorList.isEmpty() && mErrorStringList.isEmpty()) {
                return;
            }

            if (mErrorList.size() != mErrorStringList.size()) {
                throw new IllegalStateException("Mismatch error and error message");
            }

            StringBuffer buf = new StringBuffer();
            for (int i = 0; i < mErrorList.size(); ++i) {
                buf.append("Test (").append(mErrorStringList.get(i)).append(") - Error: ")
                    .append(mErrorList.get(i).getMessage()).append("; ");
            }
            throw new AssertionError(buf.toString());
        }
    }

    public static String bytesToHex(byte[] bytes, int offset, int length) {
        if (offset == -1) {
            offset = 0;
        }

        if (length == -1) {
            length = bytes.length;
        }

        final char[] hexArray = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
        char[] hexChars = new char[length * 3];
        int v;
        for (int i = 0; i < length; i++) {
            v = bytes[offset + i] & 0xFF;
            hexChars[i * 3] = hexArray[v >>> 4];
            hexChars[i * 3 + 1] = hexArray[v & 0x0F];
            hexChars[i * 3 + 2] = ' ';
        }
        return new String(hexChars);
    }

    public static void makeMyPackageActive() throws IOException {
        final String command = "cmd sensorservice reset-uid-state "
                +  InstrumentationRegistry.getTargetContext().getPackageName()
                + " --user " + Process.myUserHandle().getIdentifier();
        SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(), command);
    }

    public static void makeMyPackageIdle() throws IOException {
        final String command = "cmd sensorservice set-uid-state "
                + InstrumentationRegistry.getTargetContext().getPackageName() + " idle"
                + " --user " + Process.myUserHandle().getIdentifier();
        SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(), command);
    }
}
