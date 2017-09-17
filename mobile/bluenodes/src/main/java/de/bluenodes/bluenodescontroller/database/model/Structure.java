package de.bluenodes.bluenodescontroller.database.model;

import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;

import java.util.Date;

/**
 * Created by User on 13.09.2015.
 */

public class Structure {
    public static final String UnitTypeExtra = "UNITTYPE";
    public static final String ParentIdExtra = "PARENTID";
    public static final String SQL_DELETE_ENTRIES = "DROP TABLE IF EXISTS " + Structure.Unit.TABLE_NAME;
    private static final String TEXT_TYPE = " TEXT";
    private static final String INT_TYPE = " INTEGER";
    private static final String LONG_TYPE = " INTEGER";
    private static final String DOUBLE_TYPE = " REAL";
    private static final String BLOB_TYPE = " BLOB";
    private static final String DATETIME_TYPE = " DATETIME";
    private static final String COMMA_SEP = ",";
    public static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + Structure.Unit.TABLE_NAME + " (" +
                    Structure.Unit._ID + " INTEGER PRIMARY KEY," +
                    Structure.Unit.COLUMN_NAME + TEXT_TYPE + COMMA_SEP +
                    Structure.Unit.COLUMN_RESOURCE + TEXT_TYPE + COMMA_SEP +
                    Structure.Unit.COLUMN_TYPE + INT_TYPE + COMMA_SEP +
                    Structure.Unit.COLUMN_PARENT + LONG_TYPE + COMMA_SEP +
                    Structure.Unit.COLUMN_CAPABILITY + INT_TYPE + COMMA_SEP +
                    Structure.Unit.COLUMN_DEVICEADDRESS + BLOB_TYPE + COMMA_SEP +
                    Structure.Unit.COLUMN_DA_HASH + INT_TYPE + COMMA_SEP +
                    Structure.Unit.COLUMN_STORAGE_READING + DATETIME_TYPE + COMMA_SEP +
                    Structure.Unit.COLUMN_STORAGE_DATA + BLOB_TYPE +
                    " );";
    private long id;
    private int structureId;
    private String name;
    private String resource;
    private UnitType type;
    private long parentId;
    private NodeCapability capability;
    private byte[] deviceAddress;
    private int daHash;
    private Date storageReading;
    private byte[] storageData;

    public Structure() {
    }

    public static UnitType getUnitTypeFromOrdinal(int type) {
        for (UnitType u : UnitType.values()) {
            if (u.ordinal() == type) {
                return u;
            }
        }
        return null;
    }

    public static NodeCapability getNodeCapabilityFromOrdinal(int capability) {
        for (NodeCapability n : NodeCapability.values()) {
            if (n.ordinal() == capability) {
                return n;
            }
        }
        return null;
    }

    public static void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_ENTRIES);
    }

    public static void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL(SQL_DELETE_ENTRIES);
        onCreate(db);
    }

    public static void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getParentId() {
        return parentId;
    }

    public void setParentId(long parentId) {
        this.parentId = parentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    public UnitType getUnitType() {
        return type;
    }

    public void setUnitType(UnitType type) {
        this.type = type;
    }

    public void setUnitType(int type) {
        this.type = Structure.getUnitTypeFromOrdinal(type);
    }

    public int getOrdinalUnitType() {
        return type.ordinal();
    }

    public NodeCapability getNodeCapability() {
        return capability;
    }

    public void setNodeCapability(NodeCapability capability) {
        this.capability = capability;
    }

    public void setNodeCapability(int capability) {
        this.capability = Structure.getNodeCapabilityFromOrdinal(capability);
    }

    public int getOrdinalNodeCapability() {
        return capability.ordinal();
    }

    public byte[] getDeviceAddress() {
        return deviceAddress;
    }

    public void setDeviceAddress(byte[] deviceAddress) {
        this.deviceAddress = deviceAddress;
    }

    public int getDaHash() {
        return daHash;
    }

    public void setDaHash(int daHash) {
        this.daHash = daHash;
    }

    public byte[] getStorageData() {
        return storageData;
    }

    public void setStorageData(byte[] storageData) {
        this.storageData = storageData;
    }

    public Date getStorageReading() {
        return storageReading;
    }

    public void setStorageReading(Date storageReading) {
        this.storageReading = storageReading;
    }

    public enum UnitType {
        BUILDING,
        FLOOR,
        ROOM,
        NODE
    }

    public enum NodeCapability {
        SWITCH, DIM, MOVE
    }

    /* Inner class that defines the table contents */
    public static abstract class Unit implements BaseColumns {
        public static final String TABLE_NAME = "structure";
        public static final String COLUMN_NAME = "name";
        public static final String COLUMN_RESOURCE = "resourceId";
        public static final String COLUMN_TYPE = "type";
        public static final String COLUMN_PARENT = "parent";
        public static final String COLUMN_CAPABILITY = "capability";
        public static final String COLUMN_DEVICEADDRESS = "deviceAddress";
        public static final String COLUMN_DA_HASH = "daHash";
        public static final String COLUMN_STORAGE_READING = "storageReading";
        public static final String COLUMN_STORAGE_DATA = "storageData";
    }

}
