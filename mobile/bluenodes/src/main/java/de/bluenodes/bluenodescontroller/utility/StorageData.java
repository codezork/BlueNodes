package de.bluenodes.bluenodescontroller.utility;

import android.os.Parcel;
import android.os.Parcelable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by User on 28.03.2016.
 */
public class StorageData implements Parcelable {
    public enum Level {
        NONE((byte)0xff),
        MINUTE((byte)0x00),
        DAY((byte)0x01),
        YEAR((byte)0x02);
        private byte numVal;
        Level(byte numVal) {
            this.numVal = numVal;
        }
        public byte getValue() {
            return numVal;
        }
        public static Level getLevel(byte lev) {
            for(Level val:values()) {
                if(val.getValue()==lev) {
                    return val;
                }
            }
            return NONE;
        }
    }

    public final Parcelable.Creator<StorageData> CREATOR = new Parcelable.Creator<StorageData>() {
        public StorageData createFromParcel(Parcel in) {
            return new StorageData(in);
        }

        public StorageData[] newArray(int size) {
            return new StorageData[size];
        }
    };

    public StorageData(byte level) {
        this.level = level;
    }

    public StorageData(byte[] data) {
        this.level = data[0];
        this.length = data[1];
        for(int i = 0; i < length; i++) {
            buffer[i] = data[2+i];
        }
    }

    public StorageData(Parcel in) {
        level = in.readByte();
        length = in.readByte();
        in.readByteArray(buffer);
    }

    public int MAX_STORAGE_CONTENT = 256;

    public byte[] getBuffer() {
        return buffer;
    }

    public byte getLength() {
        return (byte)length;
    }

    public byte getLevel() {
        return level;
    }

    private byte level = 0;
    private int length = 0;
    private byte [] buffer = new byte[MAX_STORAGE_CONTENT];

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeByte(level);
        out.writeByte((byte)length);
        out.writeByteArray(buffer);
    }

    public byte[] getBytes() {
        byte[] retVal = new byte[2+MAX_STORAGE_CONTENT];
        retVal[0] = level;
        retVal[1] = (byte)length;
        for(int i= 0; i < length; i++) {
            retVal[2+i] = buffer[i];
        }
        return retVal;
    }

    public void append(StorageFragment fragment) {
        this.level = fragment.getLevel();
        for (int i = 0; i < fragment.getLength(); i++) {
            this.buffer[length + i] = fragment.getBuffer()[i];
        }
        this.length += fragment.getLength();
    }

    public float getFloat(int offset) {
        byte[] f = new byte[4];
        // assumed a float has 4 bytes
        for(int i=0; i<4; i++) {
            f[i] = buffer[i+(offset*4)];
        }
        return ByteBuffer.wrap(f).order(ByteOrder.LITTLE_ENDIAN).getFloat();
    }
};

