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

package com.android.cts.launcherapps.simpleapp;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

public class SimpleService3 extends Service {
    private final static String TAG = SimpleService3.class.getSimpleName();

    static final String ACTION_START_THEN_FG = "com.android.test.action.START_THEN_FG";
    static final String ACTION_STOP_FOREGROUND = "com.android.test.action.STOP_FOREGROUND";
    static final String ACTION_STOP_SERVICE = "com.android.test.action.STOP";

    public static String ACTION_SIMPLE_ACTIVITY_START_FG_SERVICE_RESULT =
            SimpleActivityStartFgService.ACTION_SIMPLE_ACTIVITY_START_FG_SERVICE_RESULT;

    static final String CHANNEL_NAME = "SimpleService3";

    final Binder mBinder = new Binder() {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            switch (code) {
                case FIRST_CALL_TRANSACTION:
                    Process.killProcess(Process.myPid());
                    return true;
            }
            return super.onTransact(code, data, reply, flags);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate called");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Starting: " + intent);
        if (ACTION_START_THEN_FG.equals(intent.getAction())) {
            // Set the info for the views that show in the notification panel.
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm.getNotificationChannel(CHANNEL_NAME) == null) {
                nm.createNotificationChannel(new NotificationChannel(CHANNEL_NAME, CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_DEFAULT));
            }
            Notification notification = new Notification.Builder(this, CHANNEL_NAME)
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)  // the status icon
                    .setWhen(System.currentTimeMillis())  // the time stamp
                    .setContentTitle("This is a test notification")  // the label
                    .setContentText("This is a test notification")  // the contents of the entry
                    .build();
            startForeground(100, notification);

            // And send the broadcast reporting success
            Intent reply = new Intent(ACTION_SIMPLE_ACTIVITY_START_FG_SERVICE_RESULT);
            reply.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            reply.putExtra("result", Activity.RESULT_FIRST_USER);
            sendBroadcast(reply);
        } else if (ACTION_STOP_FOREGROUND.equals(intent.getAction())) {
            stopForeground(true);
        } else if (ACTION_STOP_SERVICE.equals(intent.getAction())) {
            stopSelf();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind called");
        return mBinder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy called");
    }
}