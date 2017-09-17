package de.bluenodes.bluenodescontroller.utility;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by User on 28.03.2016.
 */
public class StorageFragment implements Parcelable {
    public enum Level {
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
    }

    public final Creator<StorageFragment> CREATOR = new Creator<StorageFragment>() {
        public StorageFragment createFromParcel(Parcel in) {
            return new StorageFragment(in);
        }

        public StorageFragment[] newArray(int size) {
            return new StorageFragment[size];
        }
    };

    public StorageFragment(byte level) {
        this.level = level;
    }

    public StorageFragment(byte[] data) {
        this.level = data[0];
        this.part = data[1];
        this.length = data[2];
        this.startstop = data[3];
        for(int i = 0; i < length; i++) {
            buffer[i] = data[4+i];
        }
    }

    public StorageFragment(Parcel in) {
        level = in.readByte();
        part = in.readByte();
        length = in.readByte();
        startstop = in.readByte();
        in.readByteArray(buffer);
    }

    public int MAX_STORAGE_CONTENT = 12;

    public byte[] getBuffer() {
        return buffer;
    }

    public byte getLength() {
        return length;
    }

    public byte getLevel() {
        return level;
    }

    public byte getPart() {
        return part;
    }

    public byte getStartStop() {
        return startstop;
    }

    private byte level;
    private byte part;
    private byte length;
    private byte startstop;

    private byte [] buffer = new byte[MAX_STORAGE_CONTENT];

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeByte(level);
        out.writeByte(part);
        out.writeByte(length);
        out.writeByte(startstop);
        out.writeByteArray(buffer);
    }

    public byte[] getBytes() {
        byte[] retVal = new byte[4+MAX_STORAGE_CONTENT];
        retVal[0] = level;
        retVal[1] = part;
        retVal[2] = length;
        retVal[3] = startstop;
        for(int i= 0; i < length; i++) {
            retVal[4+i] = buffer[i];
        }
        return retVal;
    }
};

