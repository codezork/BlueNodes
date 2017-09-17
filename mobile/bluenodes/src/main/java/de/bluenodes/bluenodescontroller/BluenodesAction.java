package de.bluenodes.bluenodescontroller;

/**
 * Created by User on 26.07.2015.
 */
public interface BluenodesAction {
    void sendBrightness(byte[] deviceAddress, Byte value);

    void sendMetaData(int accessAddress, int advertisementInterval, int channel);

    void sendRequest(byte[] deviceAddress);

    void sendDeviceAddressRequest(byte[] deviceAddress);

    void sendSetTime(byte[] deviceAddress);

    void sendClearStorage(byte[] deviceAddress);

    void sendRequestStorage(byte[] deviceAddress, byte level);
}
