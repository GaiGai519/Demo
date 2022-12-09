/*
 * Copyright 2021 The Android Open Source Project
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
package android.os.cts.process.common;

import android.annotation.Nullable;
import android.content.Context;
import android.os.Parcelable;
import android.os.Process;
import android.os.SystemClock;

import com.android.internal.util.DataClass;

/**
 * codegen ${ANDROID_BUILD_TOP}/cts/tests/process/src/android/os/cts/process/common/Message.java
 */
@DataClass(
        genConstructor = false,
        genSetters = false,
        genToString = true,
        genAidl = false)
public class Message implements Parcelable {
    public Message() {
    }

    public void fillInBasicInfo(Context context) {
        // codegen fails for whatever reason if it's after the fields.
        packageName = context.getPackageName();
        processName = Process.myProcessName();

        applicationContextClassName = context.getApplicationContext().getClass().getCanonicalName();

        nowElapsedRealtime = SystemClock.elapsedRealtime();
        nowUptimeMillis = SystemClock.uptimeMillis();

        startElapsedRealtime = Process.getStartElapsedRealtime();
        startUptimeMillis = Process.getStartUptimeMillis();
        startRequestedElapsedRealtime = Process.getStartRequestedElapsedRealtime();
        startRequestedUptimeMillis = Process.getStartRequestedUptimeMillis();
    }

    @Nullable
    public String packageName;
    @Nullable
    public String applicationClassName;
    @Nullable
    public String receiverClassName;
    @Nullable
    public String applicationContextClassName;
    @Nullable
    public String processName;
    public long startElapsedRealtime;
    public long startUptimeMillis;
    public long startRequestedElapsedRealtime;
    public long startRequestedUptimeMillis;
    public long nowElapsedRealtime;
    public long nowUptimeMillis;



    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/cts/tests/process/src/android/os/cts/process/common/Message.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    @Override
    @DataClass.Generated.Member
    public String toString() {
        // You can override field toString logic by defining methods like:
        // String fieldNameToString() { ... }

        return "Message { " +
                "packageName = " + packageName + ", " +
                "applicationClassName = " + applicationClassName + ", " +
                "receiverClassName = " + receiverClassName + ", " +
                "applicationContextClassName = " + applicationContextClassName + ", " +
                "processName = " + processName + ", " +
                "startElapsedRealtime = " + startElapsedRealtime + ", " +
                "startUptimeMillis = " + startUptimeMillis + ", " +
                "startRequestedElapsedRealtime = " + startRequestedElapsedRealtime + ", " +
                "startRequestedUptimeMillis = " + startRequestedUptimeMillis + ", " +
                "nowElapsedRealtime = " + nowElapsedRealtime + ", " +
                "nowUptimeMillis = " + nowUptimeMillis +
        " }";
    }

    @Override
    @DataClass.Generated.Member
    public void writeToParcel(@android.annotation.NonNull android.os.Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        int flg = 0;
        if (packageName != null) flg |= 0x1;
        if (applicationClassName != null) flg |= 0x2;
        if (receiverClassName != null) flg |= 0x4;
        if (applicationContextClassName != null) flg |= 0x8;
        if (processName != null) flg |= 0x10;
        dest.writeInt(flg);
        if (packageName != null) dest.writeString(packageName);
        if (applicationClassName != null) dest.writeString(applicationClassName);
        if (receiverClassName != null) dest.writeString(receiverClassName);
        if (applicationContextClassName != null) dest.writeString(applicationContextClassName);
        if (processName != null) dest.writeString(processName);
        dest.writeLong(startElapsedRealtime);
        dest.writeLong(startUptimeMillis);
        dest.writeLong(startRequestedElapsedRealtime);
        dest.writeLong(startRequestedUptimeMillis);
        dest.writeLong(nowElapsedRealtime);
        dest.writeLong(nowUptimeMillis);
    }

    @Override
    @DataClass.Generated.Member
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    protected Message(@android.annotation.NonNull android.os.Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        int flg = in.readInt();
        String _packageName = (flg & 0x1) == 0 ? null : in.readString();
        String _applicationClassName = (flg & 0x2) == 0 ? null : in.readString();
        String _receiverClassName = (flg & 0x4) == 0 ? null : in.readString();
        String _applicationContextClassName = (flg & 0x8) == 0 ? null : in.readString();
        String _processName = (flg & 0x10) == 0 ? null : in.readString();
        long _startElapsedRealtime = in.readLong();
        long _startUptimeMillis = in.readLong();
        long _startRequestedElapsedRealtime = in.readLong();
        long _startRequestedUptimeMillis = in.readLong();
        long _nowElapsedRealtime = in.readLong();
        long _nowUptimeMillis = in.readLong();

        this.packageName = _packageName;
        this.applicationClassName = _applicationClassName;
        this.receiverClassName = _receiverClassName;
        this.applicationContextClassName = _applicationContextClassName;
        this.processName = _processName;
        this.startElapsedRealtime = _startElapsedRealtime;
        this.startUptimeMillis = _startUptimeMillis;
        this.startRequestedElapsedRealtime = _startRequestedElapsedRealtime;
        this.startRequestedUptimeMillis = _startRequestedUptimeMillis;
        this.nowElapsedRealtime = _nowElapsedRealtime;
        this.nowUptimeMillis = _nowUptimeMillis;

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public static final @android.annotation.NonNull Parcelable.Creator<Message> CREATOR
            = new Parcelable.Creator<Message>() {
        @Override
        public Message[] newArray(int size) {
            return new Message[size];
        }

        @Override
        public Message createFromParcel(@android.annotation.NonNull android.os.Parcel in) {
            return new Message(in);
        }
    };

    @DataClass.Generated(
            time = 1639154672715L,
            codegenVersion = "1.0.23",
            sourceFile = "cts/tests/process/src/android/os/cts/process/common/Message.java",
            inputSignatures = "public @android.annotation.Nullable java.lang.String packageName\npublic @android.annotation.Nullable java.lang.String applicationClassName\npublic @android.annotation.Nullable java.lang.String receiverClassName\npublic @android.annotation.Nullable java.lang.String applicationContextClassName\npublic @android.annotation.Nullable java.lang.String processName\npublic  long startElapsedRealtime\npublic  long startUptimeMillis\npublic  long startRequestedElapsedRealtime\npublic  long startRequestedUptimeMillis\npublic  long nowElapsedRealtime\npublic  long nowUptimeMillis\npublic  void fillInBasicInfo(android.content.Context)\nclass Message extends java.lang.Object implements [android.os.Parcelable]\n@com.android.internal.util.DataClass(genConstructor=false, genSetters=false, genToString=true, genAidl=false)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
