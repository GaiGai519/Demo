/*
 * Copyright (C) 2012 The Android Open Source Project
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

package android.ui.cts;

import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeInstant;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.util.MeasureRun;
import com.android.compatibility.common.util.MeasureTime;
import com.android.compatibility.common.util.Stat;
import com.android.ddmlib.Log;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.metrics.proto.MetricMeasurement.Directionality;
import com.android.tradefed.metrics.proto.MetricMeasurement.DoubleValues;
import com.android.tradefed.metrics.proto.MetricMeasurement.Measurements;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.AbiUtils;

import java.io.File;

/**
 * Test to measure installation time of a APK.
 */
public class InstallTimeTest extends DeviceTestCase implements IAbiReceiver, IBuildReceiver {
    private IBuildInfo mBuild;
    private ITestDevice mDevice;
    private IAbi mAbi;

    private static final String TAG = "InstallTimeTest";
    static final String PACKAGE = "com.replica.replicaisland";
    static final String APK = "com.replica.replicaisland.apk";
    private static final double OUTLIER_THRESHOLD = 0.1;

    @Override
    public void setAbi(IAbi abi) {
        mAbi = abi;
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuild = buildInfo;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDevice = getDevice();
    }


    @Override
    protected void tearDown() throws Exception {
        mDevice.uninstallPackage(PACKAGE);
        super.tearDown();
    }

    @AppModeInstant
    public void testInstallTimeInstant() throws Exception {
        testInstallTime(true);
    }

    @AppModeFull
    public void testInstallTimeFull() throws Exception {
        testInstallTime(false);
    }

    private void testInstallTime(boolean instant) throws Exception {
        final int NUMBER_REPEAT = 10;
        final CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mBuild);
        final ITestDevice device = mDevice;
        double[] result = MeasureTime.measure(NUMBER_REPEAT, new MeasureRun() {
            @Override
            public void prepare(int i) throws Exception {
                device.uninstallPackage(PACKAGE);
            }
            @Override
            public void run(int i) throws Exception {
                File app = buildHelper.getTestFile(APK);
                String[] options =
                        {AbiUtils.createAbiFlag(mAbi.getName()), instant ? "--instant" : ""};
                device.installPackage(app, false, options);
            }
        });
        DoubleValues.Builder valuesBuilder = DoubleValues.newBuilder();
        for (double r : result) {
            valuesBuilder.addDoubleValue(r);
        }
        Metric.Builder metricsBuilder = Metric.newBuilder();
        metricsBuilder.setMeasurements(
                Measurements.newBuilder().setDoubleValues(valuesBuilder))
                .setDirection(Directionality.DOWN_BETTER)
                .setUnit("ms");
        addTestMetric("install_time", metricsBuilder.build());
        Stat.StatResult stat = Stat.getStatWithOutlierRejection(result, OUTLIER_THRESHOLD);
        if (stat.mDataCount != result.length) {
            Log.w(TAG, "rejecting " + (result.length - stat.mDataCount) + " outliers");
        }
        addTestMetric("install_time_average",
                Metric.newBuilder()
                    .setMeasurements(Measurements.newBuilder().setSingleDouble(stat.mAverage))
                    .setDirection(Directionality.DOWN_BETTER)
                    .setUnit("ms")
                    .build());
    }

}
