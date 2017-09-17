package de.bluenodes.bluenodescontroller.assignment;

import de.bluenodes.bluenodescontroller.utility.ParserUtils;

public class BluenodesDevice {
    private String name;
    private String address;

    public BluenodesDevice(String name, String address) {
        this.address = address;
        this.name = name;
    }

    public BluenodesDevice(byte[] address) {
        int v1 = address[4];
        if(v1 < 0) v1 += 256;
        int v2 = address[5];
        if(v2 < 0) v2 += 256;
        v1 = v1<<8;
        this.name = String.format("BlueNode %d", v1+v2);
        this.address = ParserUtils.parse(address, true);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
