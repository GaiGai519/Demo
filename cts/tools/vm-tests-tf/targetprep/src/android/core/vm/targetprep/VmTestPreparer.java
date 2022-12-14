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
package android.core.vm.targetprep;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetCleaner;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipFile;

/**
 * Configures the device to run VM tests.
 */
@OptionClass(alias="vm-test-preparer")
public class VmTestPreparer implements ITargetCleaner {

    private static final String JAR_FILE = "vm-tests-tf.jar";
    private static final String TEMP_DIR = "/data/local/tmp";
    private static final String VM_TEMP_DIR = TEMP_DIR +"/vm-tests";

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        CompatibilityBuildHelper helper = new CompatibilityBuildHelper(buildInfo);
        try {
            installVmPrereqs(device, helper);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to install vm-tests prereqs on device "
                    + device.getSerialNumber(), ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e)
            throws DeviceNotAvailableException {
        cleanupDeviceFiles(device);
    }

    /**
     * Install pre-requisite jars for running vm-tests, creates temp directories for test.
     *
     * @param device the {@link ITestDevice}
     * @param ctsBuild the {@link CompatibilityBuildHelper}
     * @throws DeviceNotAvailableException if device is unavailable
     * @throws RuntimeException if there is another failure
     */
    private void installVmPrereqs(ITestDevice device, CompatibilityBuildHelper ctsBuild)
            throws DeviceNotAvailableException {
        cleanupDeviceFiles(device);
        // Creates temp directory recursively. We also need to create the dalvik-cache directory
        // which is used by the dalvikvm to optimize things. Without the dalvik-cache, there will be
        // a sigsev thrown by the vm.
        createRemoteDir(device, VM_TEMP_DIR + "/dalvik-cache" );
        try {
            File jarFile = ctsBuild.getTestFile(JAR_FILE);
            if (!jarFile.exists()) {
                throw new RuntimeException("Missing jar file " + jarFile.getPath());
            }

            String jarOnDevice = VM_TEMP_DIR + "/" + JAR_FILE;
            if (!device.pushFile(jarFile, jarOnDevice)) {
                throw new RuntimeException("Failed to push vm test jar " + jarFile + " to "
                        + jarOnDevice);
            }

            // TODO: Only extract tests directory, avoid rm.
            String cmd = "unzip -d " + VM_TEMP_DIR + " " + jarOnDevice
                    + " && rm -rf " + VM_TEMP_DIR + "/dot*"
                    + " && mv " + VM_TEMP_DIR + "/tests/* " + VM_TEMP_DIR + "/"
                    + " && echo Success";

            CommandResult result = device.executeShellV2Command(cmd);

            if (result.getStatus() != CommandStatus.SUCCESS) {
                throw new RuntimeException("Failed to extract and prepare vm tests jar: [" + cmd
                        + "] with stderr: " + result.getStderr());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract jar file " + JAR_FILE
                    + " and sync it to device.", e);
        }
    }

    /**
     * Removes temporary file directory from device
     *
     * @param device
     * @throws DeviceNotAvailableException
     */
    private void cleanupDeviceFiles(ITestDevice device) throws DeviceNotAvailableException {
        if (device.doesFileExist(VM_TEMP_DIR)) {
            device.executeShellCommand(String.format("rm -r %s", VM_TEMP_DIR));
        }
    }

    /**
     * Creates the file directory recursively in the device.
     *
     * @param device the {@link ITestDevice}
     * @param remoteFilePath the absolute path.
     * @throws DeviceNotAvailableException
     */
    private void createRemoteDir(ITestDevice device, String remoteFilePath)
            throws DeviceNotAvailableException {
        if (device.doesFileExist(remoteFilePath)) {
            return;
        }
        if (!(device.doesFileExist(TEMP_DIR))) {
            CLog.e("Error: %s does not exist", TEMP_DIR);
        }
        device.executeShellCommand(String.format("mkdir %s", VM_TEMP_DIR));
        device.executeShellCommand(String.format("mkdir %s", remoteFilePath));
    }
}
