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

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

import de.bluenodes.bluenodescontroller.profile.BleManager;
import de.bluenodes.bluenodescontroller.utility.ParserUtils;
import no.nordicsemi.android.log.Logger;

public class BluenodesManager extends BleManager<BluenodesManagerCallbacks> {
    /**
     * Bluenodes service
     */
    public final static UUID MESH_SERVICE_UUID              = UUID.fromString("0000fee4-0000-1000-8000-00805f9b34fb");
    public final static UUID MESH_META_CHARACTERISTIC       = UUID.fromString("2A1E0004-FD51-D882-8BA8-B98C0000CD1E");
    public final static UUID MESH_VALUE_CHARACTERISTIC      = UUID.fromString("2A1E0005-FD51-D882-8BA8-B98C0000CD1E");
    public final static int BRIGHTNESS_CHARACTERISTIC_HANDLE                = 1;
    public final static int BUTTON_CHARACTERISTIC_HANDLE					= 2;
    public final static int REQUEST_BRIGHTNESS_CHARACTERISTIC_HANDLE		= 3;
    public final static int REQUEST_DEVICEADDRESS_CHARACTERISTIC_HANDLE		= 4;
    public final static int ANSWER_BRIGHTNESS_CHARACTERISTIC_HANDLE		    = 5;
    public final static int ANSWER_DEVICEADDRESS_CHARACTERISTIC_HANDLE		= 6;
    public final static int NOTIFY_DEVICE_ERROR_CHARACTERISTIC_HANDLE       = 7;
    public final static int SET_TIME_CHARACTERISTIC_HANDLE                  = 8;
    public final static int CLEAR_STORAGE_CHARACTERISTIC_HANDLE             = 9;
    public final static int REQUEST_STORAGE_CHARACTERISTIC_HANDLE           = 10;
    public final static int ANSWER_STORAGE_CHARACTERISTIC_HANDLE            = 11;
    public final static int MESSAGE_CHARACTERISTIC_HANDLE                   = 12;

    public final static byte GATT_ADDRESS_LEN                               = 6;

    public enum OpCode
    {
        MESH_GATT_EVT_OPCODE_DATA((byte)0x00),
        MESH_GATT_EVT_OPCODE_FLAG_SET((byte)0x01),
        MESH_GATT_EVT_OPCODE_FLAG_REQ((byte)0x02),
        MESH_GATT_EVT_OPCODE_CMD_RSP((byte)0x11),
        MESH_GATT_EVT_OPCODE_FLAG_RSP((byte)0x12);
        private byte numVal;
        OpCode(byte numVal) {
            this.numVal = numVal;
        }
        public byte getValue() {
            return numVal;
        }
    };

    public enum GattResult
    {
        MESH_GATT_RESULT_SUCCESS(0x80),
        MESH_GATT_RESULT_ERROR_BUSY(0xF0),
        MESH_GATT_RESULT_ERROR_NOT_FOUND(0xF1),
        MESH_GATT_RESULT_ERROR_INVALID_HANDLE(0xF2),
        MESH_GATT_RESULT_ERROR_UNKNOWN_FLAG(0xF3),
        MESH_GATT_RESULT_ERROR_INVALID_OPCODE(0xF4);
        private int numVal;
        GattResult(int numVal) {
            this.numVal = numVal;
        }
        public int getValue() {
            return numVal;
        }
    };

    public enum EvtFlag
    {
        MESH_GATT_EVT_FLAG_PERSISTENT,
        MESH_GATT_EVT_FLAG_DO_TX
    };

    class MeshValue {
        public OpCode code;
        public int handle;
        public byte length;
        public byte[] data = new byte[20];
    };

    private BluetoothGattCharacteristic mMetaCharacteristic, mValueCharacteristic;
    /**
     * BluetoothGatt callbacks for connection/disconnection, service discovery, receiving notification, etc
     */
    public final BleManagerGattCallback mGattCallback = new BleManagerGattCallback() {

        @Override
        protected Queue<Request> initGatt(final BluetoothGatt gatt) {
            final LinkedList<Request> requests = new LinkedList<>();
            requests.push(Request.newReadRequest(mMetaCharacteristic));
            requests.push(Request.newEnableNotificationsRequest(mValueCharacteristic));
            requests.push(Request.newEnableNotificationsRequest(mMetaCharacteristic));
            byte[] deviceAddress = ParserUtils.parse(gatt.getDevice().getAddress(), true);
            byte[] meshMsg = meshEncode(OpCode.MESH_GATT_EVT_OPCODE_DATA, SET_TIME_CHARACTERISTIC_HANDLE, deviceAddress, getDateTime());
            requests.push(Request.newWriteRequest(mValueCharacteristic, meshMsg));
            return requests;
        }

        @Override
        protected boolean isRequiredServiceSupported(final BluetoothGatt gatt) {
//            List<BluetoothGattService> list = gatt.getServices();
//            for(BluetoothGattService s:list) {
//                UUID uuid = s.getUuid();
//                List<BluetoothGattCharacteristic> chars = s.getCharacteristics();
//                for(BluetoothGattCharacteristic c:chars) {
//                    UUID cuuid = c.getUuid();
//                    int p = c.getProperties();
//                }
//
//            }
            BluetoothGattService service = gatt.getService(MESH_SERVICE_UUID);
            if (service != null) {
                mMetaCharacteristic = service.getCharacteristic(MESH_META_CHARACTERISTIC);
                mValueCharacteristic = service.getCharacteristic(MESH_VALUE_CHARACTERISTIC);
            }
            return service != null && mMetaCharacteristic != null && mValueCharacteristic != null;
        }

        @Override
        protected void onDeviceDisconnected() {
            mMetaCharacteristic = null;
            mValueCharacteristic = null;
        }

        @Override
        protected void onCharacteristicNotified(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            // characteristic read
            if (mLogSession != null)
                Logger.a(mLogSession, "characteristicNotified");

            if (characteristic == mValueCharacteristic) {
                MeshValue mv = meshDecode(characteristic.getValue());
                switch(mv.code) {
                    case MESH_GATT_EVT_OPCODE_CMD_RSP:
                        break;
                    case MESH_GATT_EVT_OPCODE_DATA:
                        switch(mv.handle) {
                            case ANSWER_BRIGHTNESS_CHARACTERISTIC_HANDLE:
                                mCallbacks.onAnswerBrightnessReceived(mv.data[0]);
                                break;
                            case ANSWER_DEVICEADDRESS_CHARACTERISTIC_HANDLE:
                                mCallbacks.onAnswerDeviceAddressReceived(mv.data);
                                break;
                            case BUTTON_CHARACTERISTIC_HANDLE:
                                mCallbacks.onButtonStateReceived(mv.data[0]);
                                break;
                            case NOTIFY_DEVICE_ERROR_CHARACTERISTIC_HANDLE:
                                mCallbacks.onErrorNotification(mv.data);
                                break;
                            case ANSWER_STORAGE_CHARACTERISTIC_HANDLE:
                                mCallbacks.onAnswerStorageNotification(mv.data);
                                break;
                            case MESSAGE_CHARACTERISTIC_HANDLE:
                                mCallbacks.onMessageNotification(mv.data);
                                break;
                            default:
                                break;
                        }
                        break;
                    default:
                        break;
                }
            } else if (characteristic == mMetaCharacteristic) {
                int offset = 0;
                final int accessAddress = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, offset); // 4 byte
                offset += 4;
                final int advertisementInterval = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, offset); // 4 byte
                offset += 4;
                final int channel = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset); // 1byte
                mCallbacks.onMetadataReceived(accessAddress, advertisementInterval, channel);
            }
        }

        @Override
        protected void onCharacteristicRead(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            // characteristic read
            if (mLogSession != null)
                Logger.a(mLogSession, "characteristicRead");

            if (characteristic == mMetaCharacteristic) {
                int offset = 0;
                final int accessAddress = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, offset); // 4 byte
                offset += 4;
                final int advertisementInterval = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, offset); // 4 byte
                offset += 4;
                final int channel = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset); // 1byte
                mCallbacks.onMetadataReceived(accessAddress, advertisementInterval, channel);
            }
        }

        @Override
        protected void onCharacteristicIndicated(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            // characteristic read
            if (mLogSession != null)
                Logger.a(mLogSession, "characteristicIndicated");
        }
    };

    public BluenodesManager(final Context context) {
        super(context);
    }

    @Override
    protected BleManagerGattCallback getGattCallback() {
        return mGattCallback;
    }

    @Override
    protected boolean shouldAutoConnect() {
        // We want the connection to be kept
        return true;
    }

    private byte[] getDateTime() {
        Calendar c = Calendar.getInstance();
        SimpleDateFormat df = new SimpleDateFormat("yyMMddHHmm");   // must be that short due to limitation of mesh value
        return ParserUtils.parseDecimal(df.format(c.getTime()));
    }

    private void meshPrefix(OpCode code, int handle, byte[] data) {
        // 1 byte opcode
        data[0] = (byte)code.getValue();
        // 2 bytes handle little endian
        data[1] = (byte) (handle & 0xff);
        data[2] = (byte)((handle>>8)&0xff);
    }

    private void meshLength(byte length, byte[] data) {
        data[3] = length;
    }

    private void meshAddress(byte[] deviceAddress, byte[] data) {
        for (int i = GATT_ADDRESS_LEN-1; i >= 0; i--) {
            data[4 + i] = deviceAddress[GATT_ADDRESS_LEN - 1 - i];
        }
    }

    private byte[] meshEncode(OpCode code, int handle, byte[] deviceAddress) {
        byte[] data = new byte[4 + GATT_ADDRESS_LEN + 1];
        meshPrefix(code, handle, data);
        meshLength((byte) GATT_ADDRESS_LEN, data);
        meshAddress(deviceAddress, data);
        return data;
    }

    private byte[] meshEncode(OpCode code, int handle, byte[] deviceAddress, Byte value) {
        byte[] data = new byte[4 + GATT_ADDRESS_LEN + 1];
        meshPrefix(code, handle, data);
        meshLength((byte) (GATT_ADDRESS_LEN + 1), data);
        meshAddress(deviceAddress, data);
        data[4 + GATT_ADDRESS_LEN] = value;
        return data;
    }

    private byte[] meshEncode(OpCode code, int handle, byte[] deviceAddress, byte[] value) {
        byte[] data = new byte[4 + GATT_ADDRESS_LEN + value.length];
        meshPrefix(code, handle, data);
        meshLength((byte) (GATT_ADDRESS_LEN + value.length), data);
        meshAddress(deviceAddress, data);
        for (int i = 0; i < value.length; i++) {
            data[4 + GATT_ADDRESS_LEN + i] = value[i];
        }
        return data;
    }

    private MeshValue meshDecode(byte[] data) {
        MeshValue mv = new MeshValue();
        for(OpCode oc:OpCode.values()) {
            if(oc.getValue()==data[0]) {
                mv.code = oc;
                break;
            }
        }
        mv.handle = data[1] | (data[2]<<8);
        if(data.length > 3) {
            mv.length = data[3];
            if (mv.length > 0) {
                mv.data = new byte[mv.length];
                for (int i = 0; i < mv.length; i++) {
                    mv.data[i] = data[4 + i];
                }
            }
        }
        return mv;
    }

    public void sendBrightness(byte[] deviceAddress, Byte brightness) {
        if (mValueCharacteristic != null) {
            mValueCharacteristic.setValue(meshEncode(OpCode.MESH_GATT_EVT_OPCODE_DATA, BRIGHTNESS_CHARACTERISTIC_HANDLE, deviceAddress, brightness));
            writeCharacteristic(mValueCharacteristic);
        }
    }

    public void sendMetaData(int accessAddress, int advertisementInterval, int channel) {
        if (mMetaCharacteristic != null) {
            int offset = 0;
            mMetaCharacteristic.setValue(accessAddress, BluetoothGattCharacteristic.FORMAT_UINT32, offset);
            offset += 4;
            mMetaCharacteristic.setValue(advertisementInterval, BluetoothGattCharacteristic.FORMAT_UINT32, offset);
            offset += 4;
            mMetaCharacteristic.setValue(channel, BluetoothGattCharacteristic.FORMAT_UINT8, offset);
            writeCharacteristic(mMetaCharacteristic);
        }
    }

    public void sendRequest(byte[] deviceAddress) {
        if (mValueCharacteristic != null) {
            mValueCharacteristic.setValue(meshEncode(OpCode.MESH_GATT_EVT_OPCODE_DATA, REQUEST_BRIGHTNESS_CHARACTERISTIC_HANDLE, deviceAddress));
            writeCharacteristic(mValueCharacteristic);
        }
    }

    public void sendDeviceAddressRequest(byte[] deviceAddress) {
        if (mValueCharacteristic != null) {
            mValueCharacteristic.setValue(meshEncode(OpCode.MESH_GATT_EVT_OPCODE_DATA, REQUEST_DEVICEADDRESS_CHARACTERISTIC_HANDLE, deviceAddress));
            writeCharacteristic(mValueCharacteristic);
        }
    }

    public void sendSetTime(byte[] deviceAddress) {
        if (mValueCharacteristic != null) {
            mValueCharacteristic.setValue(meshEncode(OpCode.MESH_GATT_EVT_OPCODE_DATA, SET_TIME_CHARACTERISTIC_HANDLE, deviceAddress, getDateTime()));
            writeCharacteristic(mValueCharacteristic);
        }
    }

    public void sendClearStorage(byte[] deviceAddress) {
        if (mValueCharacteristic != null) {
            mValueCharacteristic.setValue(meshEncode(OpCode.MESH_GATT_EVT_OPCODE_DATA, CLEAR_STORAGE_CHARACTERISTIC_HANDLE, deviceAddress));
            writeCharacteristic(mValueCharacteristic);
        }
    }

    public void sendRequestStorage(byte[] deviceAddress, byte level) {
        if (mValueCharacteristic != null) {
            mValueCharacteristic.setValue(meshEncode(OpCode.MESH_GATT_EVT_OPCODE_DATA, REQUEST_STORAGE_CHARACTERISTIC_HANDLE, deviceAddress, level));
            writeCharacteristic(mValueCharacteristic);
        }
    }
}
