package de.bluenodes.bluenodescontroller.database.contentprovider;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

import java.util.Arrays;
import java.util.HashSet;

import de.bluenodes.bluenodescontroller.database.model.Structure;

/**
 * Created by User on 17.09.2015.
 */
public class BluenodesContentProvider extends ContentProvider {
    public static final String CONTENT_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE
            + "/units";
    public static final String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE
            + "/units";
    // used for the UriMatcher
    private static final int UNITS = 10;
    private static final int UNITS_ID = 20;
    private static final String AUTHORITY = "de.bluenodes.bluenodescontroller.database.contentprovider";
    public static final String BASE_PATH = "structure";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY
            + "/" + BASE_PATH);
    private static final UriMatcher sURIMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    public static final String NODE_ID = "nodeId";
    public static final String DEVICEADDRESS_ID = "deviceAddressId";
    public static final String NODE_NAME = "nodeName";
    public static final String QUERY_DEVICEADDRESS_ID = "queryDeviceAddressId";
    public static final String QUERY_NODE_NAME = "queryNodeName";
    public static final String DEVICE_ERROR_CODE = "deviceErrorCode";

    static {
        sURIMatcher.addURI(AUTHORITY, BASE_PATH, UNITS);
        sURIMatcher.addURI(AUTHORITY, BASE_PATH + "/#", UNITS_ID);
    }

    // database
    private BluenodesDbHelper database;

    @Override
    public synchronized boolean onCreate() {
        database = new BluenodesDbHelper(getContext());
        return false;
    }

    @Override
    public synchronized Bundle call(String method, String arg, Bundle extras) {
        if (method.equals(QUERY_DEVICEADDRESS_ID)) {
            Bundle result = new Bundle();
            result.putByteArray(DEVICEADDRESS_ID, queryDeviceAddress(extras.getLong(NODE_ID)));
            return result;
        }
        if (method.equals(QUERY_NODE_NAME)) {
            String nodeName = queryNodeName(extras.getByteArray(DEVICEADDRESS_ID));
            extras.putString(NODE_NAME, nodeName);
            return extras;
        }
        return null;
    }

    private synchronized String queryNodeName(byte[] address) {
        String nodeName = "";
        int nodeHash;
        byte[] deviceAddress;
        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
        queryBuilder.setTables(Structure.Unit.TABLE_NAME + " Node");
        SQLiteDatabase db = database.getWritableDatabase();
        String[] projection = {
                "Node." + Structure.Unit.COLUMN_NAME + " as \"name\""
        };
        String selection = "Node." + Structure.Unit.COLUMN_DA_HASH + "=?";
        String selectionArgs[] = new String[1];
        selectionArgs[0] = String.format("%d", Arrays.hashCode(address));

        Cursor cursor = queryBuilder.query(db, projection, selection,
                selectionArgs, null, null, null);
        if (cursor != null) {
            cursor.moveToFirst();
            nodeName = cursor.getString(cursor.getColumnIndexOrThrow("name"));
            cursor.close();
        }
        return nodeName;
    }

    private synchronized byte[] queryDeviceAddress(long nodeId) {
        byte data[] = new byte[10];
        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
        queryBuilder.setTables(Structure.Unit.TABLE_NAME + " Node"
                + " LEFT JOIN "
                + Structure.Unit.TABLE_NAME + " Room"
                + " ON Node." + Structure.Unit.COLUMN_PARENT + " = Room."+ Structure.Unit._ID
                + " LEFT JOIN "
                + Structure.Unit.TABLE_NAME + " Floor"
                + " ON Room." + Structure.Unit.COLUMN_PARENT + " = Floor."+ Structure.Unit._ID
                + " LEFT JOIN "
                + Structure.Unit.TABLE_NAME + " Building"
                + " ON Floor." + Structure.Unit.COLUMN_PARENT + " = Building."+ Structure.Unit._ID);

        SQLiteDatabase db = database.getWritableDatabase();
        String[] projection = {
                "Room."+Structure.Unit._ID+" as \"roomid\"",
                "Floor."+Structure.Unit._ID+" as \"floorid\"",
                "Building."+Structure.Unit._ID+" as \"buildingid\""
        };
        String selection = "Node."+Structure.Unit._ID+"=?";
        String selectionArgs[] = new String[1];
        selectionArgs[0] = String.format("%d", nodeId);

        Cursor cursor = queryBuilder.query(db, projection, selection,
                selectionArgs, null, null, null);
        if (cursor != null) {
            if(cursor.moveToFirst()) {
                long roomId = cursor.getLong(cursor.getColumnIndexOrThrow("roomid"));
                long floorId = cursor.getLong(cursor.getColumnIndexOrThrow("floorid"));
                long buildingId = cursor.getLong(cursor.getColumnIndexOrThrow("buildingid"));
                String s = String.format("0000%04x%04x%04x%04x", buildingId, floorId, roomId, nodeId);
                int len = s.length();
                for (int i = 0; i < len; i += 2) {
                    data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                            + Character.digit(s.charAt(i + 1), 16));
                }
            }
            // always close the cursor
            cursor.close();
        }
        return data;
    }

    @Override
    public synchronized Cursor query(Uri uri, String[] projection, String selection,
                                     String[] selectionArgs, String sortOrder) {

        // Using SQLiteQueryBuilder instead of query() method
        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();

        // check if the caller has requested a column which does not exists
        checkColumns(projection);

        // Set the table
        queryBuilder.setTables(Structure.Unit.TABLE_NAME);

        int uriType = sURIMatcher.match(uri);
        switch (uriType) {
            case UNITS:
                break;
            case UNITS_ID:
                // adding the ID to the original query
                queryBuilder.appendWhere(Structure.Unit._ID + "="
                        + uri.getLastPathSegment());
                break;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }

        SQLiteDatabase db = database.getWritableDatabase();
        Cursor cursor = queryBuilder.query(db, projection, selection,
                selectionArgs, null, null, sortOrder);
        // make sure that potential listeners are getting notified
        cursor.setNotificationUri(getContext().getContentResolver(), uri);

        return cursor;
    }

    @Override
    public synchronized String getType(Uri uri) {
        return null;
    }

    @Override
    public synchronized Uri insert(Uri uri, ContentValues values) {
        int uriType = sURIMatcher.match(uri);
        SQLiteDatabase sqlDB = database.getWritableDatabase();
        int rowsDeleted = 0;
        long id = 0;
        switch (uriType) {
            case UNITS:
                id = sqlDB.insert(Structure.Unit.TABLE_NAME, null, values);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return Uri.parse(BASE_PATH + "/" + id);
    }

    @Override
    public synchronized int delete(Uri uri, String selection, String[] selectionArgs) {
        int uriType = sURIMatcher.match(uri);
        SQLiteDatabase sqlDB = database.getWritableDatabase();
        int rowsDeleted = 0;
        switch (uriType) {
            case UNITS:
                rowsDeleted = sqlDB.delete(Structure.Unit.TABLE_NAME, selection,
                        selectionArgs);
                break;
            case UNITS_ID:
                String id = uri.getLastPathSegment();
                if (TextUtils.isEmpty(selection)) {
                    rowsDeleted = sqlDB.delete(Structure.Unit.TABLE_NAME,
                            Structure.Unit._ID + "=" + id,
                            null);
                } else {
                    rowsDeleted = sqlDB.delete(Structure.Unit.TABLE_NAME,
                            Structure.Unit._ID + "=" + id
                                    + " and " + selection,
                            selectionArgs);
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return rowsDeleted;
    }

    @Override
    public synchronized int update(Uri uri, ContentValues values, String selection,
                                   String[] selectionArgs) {

        int uriType = sURIMatcher.match(uri);
        SQLiteDatabase sqlDB = database.getWritableDatabase();
        int rowsUpdated = 0;
        switch (uriType) {
            case UNITS:
                rowsUpdated = sqlDB.update(Structure.Unit.TABLE_NAME,
                        values,
                        selection,
                        selectionArgs);
                break;
            case UNITS_ID:
                String id = uri.getLastPathSegment();
                if (TextUtils.isEmpty(selection)) {
                    rowsUpdated = sqlDB.update(Structure.Unit.TABLE_NAME,
                            values,
                            Structure.Unit._ID + "=" + id,
                            null);
                } else {
                    rowsUpdated = sqlDB.update(Structure.Unit.TABLE_NAME,
                            values,
                            Structure.Unit._ID + "=" + id
                                    + " and "
                                    + selection,
                            selectionArgs);
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return rowsUpdated;
    }

    private synchronized void checkColumns(String[] projection) {
        String[] available = {
                Structure.Unit._ID,
                Structure.Unit.COLUMN_NAME,
                Structure.Unit.COLUMN_RESOURCE,
                Structure.Unit.COLUMN_TYPE,
                Structure.Unit.COLUMN_PARENT,
                Structure.Unit.COLUMN_CAPABILITY,
                Structure.Unit.COLUMN_DEVICEADDRESS,
                Structure.Unit.COLUMN_DA_HASH};
        if (projection != null) {
            HashSet<String> requestedColumns = new HashSet<String>(Arrays.asList(projection));
            HashSet<String> availableColumns = new HashSet<String>(Arrays.asList(available));
            // check if all columns which are requested are available
            if (!availableColumns.containsAll(requestedColumns)) {
                throw new IllegalArgumentException("Unknown columns in projection");
            }
        }
    }
}
