/*
 * Copyright (c) 2015, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package de.bluenodes.bluenodescontroller;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;

import de.bluenodes.bluenodescontroller.profile.BleManager;
import de.bluenodes.bluenodescontroller.profile.BleProfileService;
import de.bluenodes.bluenodescontroller.utility.StorageData;
import de.bluenodes.bluenodescontroller.utility.StorageFragment;
import no.nordicsemi.android.log.ILogSession;
import no.nordicsemi.android.log.Logger;

public class BluenodesService extends BleProfileService implements BluenodesManagerCallbacks {
    public static final String BROADCAST_BUTTON_STATE = "de.bluenodes.bluendescontroller.BROADCAST_BUTTON_STATE";
    public static final String EXTRA_BUTTON_STATE = "de.bluenodes.bluendescontroller.EXTRA_BUTTON_STATE";
    public static final String BROADCAST_RECEIVE_BRIGHTNESS_VALUE = "de.bluenodes.bluendescontroller.BROADCAST_RECEIVE_BRIGHTNESS_VALUE";
    public static final String BROADCAST_RECEIVE_DEVICEADDRESS_VALUE = "de.bluenodes.bluendescontroller.BROADCAST_RECEIVE_DEVICEADDRESS_VALUE";
    public static final String BROADCAST_SEND_BRIGHTNESS_VALUE = "de.bluenodes.bluendescontroller.BROADCAST_RECEIVE_SEND_VALUE";
    public static final String BROADCAST_REQUEST_BRIGHTNESS_VALUE = "de.bluenodes.bluendescontroller.BROADCAST_REQUEST_BRIGHTNESS_VALUE";
    public static final String BROADCAST_REQUEST_DEVICEADDRESS_VALUE = "de.bluenodes.bluendescontroller.BROADCAST_REQUEST_DEVICEADDRESS_VALUE";
    public static final String EXTRA_BRIGHTNESS_VALUE = "de.bluenodes.bluendescontroller.EXTRA_BRIGHTNESS_VALUE";
    public static final String EXTRA_DEVICEADDRESS_VALUE = "de.bluenodes.bluendescontroller.EXTRA_DEVICEADDRESS_VALUE";
    public static final String BROADCAST_METADATA = "de.bluenodes.bluendescontroller.BROADCAST_METADATA";
    public static final String EXTRA_ACCESS_ADDRESS = "de.bluenodes.bluendescontroller.EXTRA_ACCESS_ADDRESS";
    public static final String EXTRA_ADVERTISEMENT_INTERVAL = "de.bluenodes.bluendescontroller.EXTRA_ADVERTISEMENT_INTERVAL";
    public static final String EXTRA_VALUE_COUNT = "de.bluenodes.bluendescontroller.EXTRA_VALUE_COUNT";
    public static final String EXTRA_CHANNEL = "de.bluenodes.bluendescontroller.EXTRA_CHANNEL";
    public static final String BROADCAST_DEVICEADDRESS_VALUE = "de.bluenodes.bluendescontroller.BROADCAST_DEVICEADDRESS_VALUE";
    public static final String EXTRA_DEVICEADDRESS = "de.bluenodes.bluendescontroller.EXTRA_DEVICEADDRESS";
    public final static String BROADCAST_GO_DOWN = "de.bluenodes.bluenodescontroller.BROADCAST_GO_DOWN";
    public final static String BROADCAST_ON_OPEN = "de.bluenodes.bluenodescontroller.BROADCAST_ON_OPEN";
    public final static String EXTRA_PARENT_ID = "de.bluenodes.bluenodescontroller.EXTRA_PARENT_ID";
    public final static String BROADCAST_SHOW_DEVICE = "de.bluenodes.bluenodescontroller.BROADCAST_SHOW_DEVICE";
    public final static String EXTRA_NODE_NAME = "de.bluenodes.bluenodescontroller.EXTRA_NODE_NAME";
    public final static String EXTRA_NODE_ID = "de.bluenodes.bluenodescontroller.EXTRA_NODE_ID";
    public final static String EXTRA_CAPABILITY = "de.bluenodes.bluenodescontroller.EXTRA_CAPABILITY";
    public final static String BROADCAST_ERRORNOTIFICATION = "de.bluenodes.bluenodescontroller.BROADCAST_ERRORNOTIFICATION";
    public final static String EXTRA_ERRORCODE = "de.bluenodes.bluenodescontroller.EXTRA_ERRORCODE";
    public static final String BROADCAST_SEND_CLEARSTORAGE = "de.bluenodes.bluendescontroller.BROADCAST_SEND_CLEARSTORAGE";
    public static final String BROADCAST_REQUEST_STORAGE = "de.bluenodes.bluendescontroller.BROADCAST_REQUEST_STORAGE";
    public static final String BROADCAST_RECEIVE_STORAGE = "de.bluenodes.bluendescontroller.BROADCAST_RECEIVE_STORAGE";
    public final static String EXTRA_STORAGEDATA = "de.bluenodes.bluenodescontroller.EXTRA_STORAGEDATA";
    public final static String EXTRA_STORAGELEVEL = "de.bluenodes.bluenodescontroller.EXTRA_STORAGELEVEL";
    public final static String EXTRA_SUBTITLE = "de.bluenodes.bluenodescontroller.EXTRA_SUBTITLE";
    public static final String BROADCAST_DISPLAY_STORAGE = "de.bluenodes.bluendescontroller.BROADCAST_DISPLAY_STORAGE";
    public final static String EXTRA_TABPAGE = "de.bluenodes.bluenodescontroller.EXTRA_TABPAGE";
    public final static String BROADCAST_MESSAGENOTIFICATION = "de.bluenodes.bluenodescontroller.BROADCAST_MESSAGENOTIFICATION";
    public final static String EXTRA_MESSAGE = "de.bluenodes.bluenodescontroller.EXTRA_MESSAGE";
    /**
     * A broadcast message with this action and the message in {@link Intent#EXTRA_TEXT} will be sent t the UART device.
     */
    private final static String ACTION_SEND = "de.bluenodes.bluendescontroller.ACTION_SEND";
    /**
     * A broadcast message with this action is triggered when a message is received from the UART device.
     */
    private final static String ACTION_RECEIVE = "de.bluenodes.bluendescontroller.ACTION_RECEIVE";
    /**
     * Action send when user press the DISCONNECT button on the notification.
     */
    private final static String ACTION_DISCONNECT = "de.bluenodes.bluendescontroller.ACTION_DISCONNECT";

    private final static int NOTIFICATION_ID = 267;
    private final static int OPEN_ACTIVITY_REQ = 0;
    private final static int DISCONNECT_REQ = 1;
    private final LocalBinder mBinder = new BluenodesBinder();
    /**
     * This broadcast receiver listens for {@link #ACTION_DISCONNECT} that may be fired by pressing Disconnect action button on the notification.
     */
    private final BroadcastReceiver mDisconnectActionBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            Logger.i(getLogSession(), "[Notification] Disconnect action pressed");
            if (isConnected())
                getBinder().disconnect();
            else
                stopSelf();
        }
    };
    private BluenodesManager mManager;
    private StorageData storageData = null;
    /**
     * Broadcast receiver that listens for {@link #ACTION_SEND} from other apps. Sends the String or int content of the {@link Intent#EXTRA_TEXT} extra to the remote device.
     * The integer content will be sent as String (65 -> "65", not 65 -> "A").
     */
    private BroadcastReceiver mIntentBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final boolean hasMessage = intent.hasExtra(Intent.EXTRA_TEXT);
            if (hasMessage) {
//				final int intValue = intent.getIntExtra(Intent.EXTRA_TEXT, 0);
//				mManager.sendBrightness(intValue);
                return;
            }
            // No data od incompatible type of EXTRA_TEXT
            if (!hasMessage)
                Logger.i(getLogSession(), "[Broadcast] " + ACTION_SEND + " broadcast received no data.");
            else
                Logger.i(getLogSession(), "[Broadcast] " + ACTION_SEND + " broadcast received incompatible data type. Only String and int are supported.");
        }
    };

    @Override
    protected LocalBinder getBinder() {
        return mBinder;
    }

    @Override
    protected BleManager<BluenodesManagerCallbacks> initializeManager() {
        return mManager = new BluenodesManager(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        registerReceiver(mDisconnectActionBroadcastReceiver, new IntentFilter(ACTION_DISCONNECT));
        registerReceiver(mIntentBroadcastReceiver, new IntentFilter(ACTION_SEND));
    }

    @Override
    public void onDestroy() {
        // when user has disconnected from the sensor, we have to cancel the notification that we've created some milliseconds before using unbindService
        cancelNotification();
        unregisterReceiver(mDisconnectActionBroadcastReceiver);
        unregisterReceiver(mIntentBroadcastReceiver);
        super.onDestroy();
    }

    @Override
    protected void onRebind() {
        // when the activity rebinds to the service, remove the notification
        cancelNotification();
    }

    @Override
    protected void onUnbind() {
        // when the activity closes we need to show the notification that user is connected to the sensor
        createNotification(R.string.notification_connected_message, 0);
    }

    @Override
    protected void onServiceStarted() {
        // logger is now available. Assign it to the manager
        mManager.setLogger(getLogSession());
    }

    @Override
    public void onButtonStateReceived(final int data) {
        final Intent broadcast = new Intent(BROADCAST_BUTTON_STATE);
        broadcast.putExtra(EXTRA_BUTTON_STATE, data);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onAnswerBrightnessReceived(final int answer) {
        final Intent broadcast = new Intent(BROADCAST_RECEIVE_BRIGHTNESS_VALUE);
        broadcast.putExtra(EXTRA_BRIGHTNESS_VALUE, answer);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onAnswerDeviceAddressReceived(final byte[] deviceAddress) {
        final Intent broadcast = new Intent(BROADCAST_RECEIVE_DEVICEADDRESS_VALUE);
        broadcast.putExtra(EXTRA_DEVICEADDRESS_VALUE, deviceAddress);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onBrightnessReceived(final int data) {
        final Intent broadcast = new Intent(BROADCAST_RECEIVE_BRIGHTNESS_VALUE);
        broadcast.putExtra(EXTRA_BRIGHTNESS_VALUE, data);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onMetadataReceived(final int accessAddress, final int advertisementInterval, final int channel) {
        final Intent broadcast = new Intent(BROADCAST_METADATA);
        broadcast.putExtra(EXTRA_ACCESS_ADDRESS, accessAddress);
        broadcast.putExtra(EXTRA_ADVERTISEMENT_INTERVAL, advertisementInterval);
        broadcast.putExtra(EXTRA_CHANNEL, channel);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onErrorNotification(final byte[] deviceError) {
        final Intent broadcast = new Intent(BROADCAST_ERRORNOTIFICATION);
        broadcast.putExtra(EXTRA_ERRORCODE, deviceError);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onMessageNotification(final byte[] message) {
        final Intent broadcast = new Intent(BROADCAST_MESSAGENOTIFICATION);
        broadcast.putExtra(EXTRA_MESSAGE, message);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onAnswerStorageNotification(final byte[] storageDataRaw) {
        final Intent broadcast = new Intent(BROADCAST_RECEIVE_STORAGE);
        StorageFragment storageFragment = new StorageFragment(storageDataRaw);
        switch(storageFragment.getStartStop()) {
            case 1:
                storageData = new StorageData(storageFragment.getLevel());
                storageData.append(storageFragment);
                break;
            case 2:
                storageData.append(storageFragment);
                broadcast.putExtra(EXTRA_STORAGEDATA, storageData);
                LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
                break;
            default:
                storageData.append(storageFragment);
                break;
        }
    }
    /**
     * Creates the notification
     *
     * @param messageResId message resource id. The message must have one String parameter,<br />
     *                     f.e. <code>&lt;string name="name"&gt;%s is connected&lt;/string&gt;</code>
     * @param defaults     signals that will be used to notify the user
     */
    private void createNotification(final int messageResId, final int defaults) {
        final Intent parentIntent = new Intent(this, ControllerActivity.class);
        parentIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final Intent targetIntent = new Intent(this, ControllerActivity.class);

        final Intent disconnect = new Intent(ACTION_DISCONNECT);
        final PendingIntent disconnectAction = PendingIntent.getBroadcast(this, DISCONNECT_REQ, disconnect, PendingIntent.FLAG_UPDATE_CURRENT);

        // both activities above have launchMode="singleTask" in the AndroidManifest.xml file, so if the task is already running, it will be resumed
        final PendingIntent pendingIntent = PendingIntent.getActivities(this, OPEN_ACTIVITY_REQ, new Intent[]{parentIntent, targetIntent}, PendingIntent.FLAG_UPDATE_CURRENT);
        final Notification.Builder builder = new Notification.Builder(this).setContentIntent(pendingIntent);
        builder.setContentTitle(getString(R.string.app_name)).setContentText(getString(messageResId, getDeviceName()));
//		builder.setSmallIcon(R.drawable.ic_stat_notify_hts);
//		builder.setShowWhen(defaults != 0).setDefaults(defaults).setAutoCancel(true).setOngoing(true);
//		builder.addAction(R.drawable.ic_action_bluetooth, getString(R.string.notification_action_disconnect), disconnectAction);

        final Notification notification = builder.build();
        final NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID, notification);
    }

    /**
     * Cancels the existing notification. If there is no active notification this method does nothing
     */
    private void cancelNotification() {
        final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.cancel(NOTIFICATION_ID);
    }

    /**
     * This local binder is an interface for the bonded activity to operate with a BlueNode
     */
    public class BluenodesBinder extends LocalBinder implements BluenodesAction {
        @Override
        public void sendBrightness(final byte[] deviceAddress, final Byte brightness) {
            mManager.sendBrightness(deviceAddress, brightness);
        }

        @Override
        public void sendMetaData(int accessAddress, int advertisementInterval, int channel) {
            mManager.sendMetaData(accessAddress, advertisementInterval, channel);
        }

        @Override
        public void sendRequest(byte[] deviceAddress) {
            mManager.sendRequest(deviceAddress);
        }

        @Override
        public void sendDeviceAddressRequest(byte[] deviceAddress) {
            mManager.sendDeviceAddressRequest(deviceAddress);
        }

        @Override
        public void sendSetTime(byte[] deviceAddress) {
            mManager.sendSetTime(deviceAddress);
        }

        @Override
        public void sendClearStorage(byte[] deviceAddress) {
            mManager.sendClearStorage(deviceAddress);
        }

        @Override
        public void sendRequestStorage(byte[] deviceAddress, byte level) {
            mManager.sendRequestStorage(deviceAddress, level);
        }

        @Override
        public ILogSession getLogSession() {
            return super.getLogSession();

        }

    }
}
