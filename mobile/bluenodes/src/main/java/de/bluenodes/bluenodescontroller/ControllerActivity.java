package de.bluenodes.bluenodescontroller;

import android.app.LoaderManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.fortysevendeg.swipelistview.SwipeListView;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.UUID;

import de.bluenodes.bluenodescontroller.database.contentprovider.BluenodesContentProvider;
import de.bluenodes.bluenodescontroller.database.contentprovider.BluenodesCursorAdapter;
import de.bluenodes.bluenodescontroller.database.model.Structure;
import de.bluenodes.bluenodescontroller.error.DeviceError;
import de.bluenodes.bluenodescontroller.profile.BleProfileDirectServiceReadyActivity;
import de.bluenodes.bluenodescontroller.profile.BleProfileService;
import de.bluenodes.bluenodescontroller.swipe.ExtendedSwipeListView;
import de.bluenodes.bluenodescontroller.utility.ParserUtils;

public class ControllerActivity extends BleProfileDirectServiceReadyActivity<BluenodesService.BluenodesBinder>
        implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final int REQUEST_CODE_SETTINGS = 0;
    private static final String REQUEST_BACK = "de.bluenodes.bluenodescontroller.REQUEST_BACK";
    private BluenodesService.BluenodesBinder mServiceBinder;

    private ExtendedSwipeListView mStructureList;
    private TextView mNoStructure;
    private TextView mStructureTitle;
    private TextView mBattery;
    private TextView mMessage;
    private int mAccessAddress = 0;
    private int mAdvertisementInterval = 0;
    private int mChannel = 0;
    private byte[] mConnectedDeviceAddress;
    private Structure.UnitType typeMode;
    private long parentId;
    private BluenodesCursorAdapter adapter;
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();

            if (BluenodesService.BROADCAST_GO_DOWN.equals(action)) {
                if (intent.hasExtra(BluenodesService.EXTRA_PARENT_ID)) {
                    setParentId(intent.getLongExtra(BluenodesService.EXTRA_PARENT_ID, 0));
                }
                doDismiss();
            } else if (BluenodesService.BROADCAST_ON_OPEN.equals(action)) {
                setParentId(intent.getLongExtra(BluenodesService.EXTRA_PARENT_ID, 0));
            } else if (BluenodesService.BROADCAST_SHOW_DEVICE.equals(action)) {
                Intent i = new Intent(context, DimmerActivity.class);
                i.putExtra(BluenodesService.EXTRA_NODE_NAME, intent.getStringExtra(BluenodesService.EXTRA_NODE_NAME));
                i.putExtra(BluenodesService.EXTRA_DEVICEADDRESS, intent.getByteArrayExtra(BluenodesService.EXTRA_DEVICEADDRESS));
                i.putExtra(BluenodesService.EXTRA_CAPABILITY, intent.getStringExtra(BluenodesService.EXTRA_CAPABILITY));
                startActivity(i);
            } else if (BluenodesService.BROADCAST_SEND_BRIGHTNESS_VALUE.equals(action)) {
                if (mServiceBinder != null) {
                    mServiceBinder.sendBrightness(intent.getByteArrayExtra(BluenodesService.EXTRA_DEVICEADDRESS), intent.getByteExtra(BluenodesService.EXTRA_BRIGHTNESS_VALUE, (byte) 0));
                }
            } else if (BluenodesService.BROADCAST_REQUEST_BRIGHTNESS_VALUE.equals(action)) {
                if (mServiceBinder != null) {
                    mServiceBinder.sendRequest(intent.getByteArrayExtra(BluenodesService.EXTRA_DEVICEADDRESS));
                }
            } else if (BluenodesService.BROADCAST_REQUEST_DEVICEADDRESS_VALUE.equals(action)) {
                if (mServiceBinder != null) {
                    mServiceBinder.sendDeviceAddressRequest(mConnectedDeviceAddress);
                }
            } else if (BluenodesService.BROADCAST_SEND_CLEARSTORAGE.equals(action)) {
                if (mServiceBinder != null) {
                    mServiceBinder.sendClearStorage(mConnectedDeviceAddress);
                }
            } else if (BluenodesService.BROADCAST_REQUEST_STORAGE.equals(action)) {
                if (mServiceBinder != null) {
                    mServiceBinder.sendRequestStorage(intent.getByteArrayExtra(BluenodesService.EXTRA_DEVICEADDRESS), intent.getByteExtra(BluenodesService.EXTRA_STORAGELEVEL, (byte)0));
                }
            } else if (BluenodesService.BROADCAST_BUTTON_STATE.equals(action)) {
                final int value = intent.getIntExtra(BluenodesService.EXTRA_BUTTON_STATE, 0);
                // Update GUI
//                mButton1.setChecked((value & 1) != 0);
//                mButton2.setChecked((value & 2) != 0);
//                mButton3.setChecked((value & 4) != 0);
//                mButton4.setChecked((value & 8) != 0);
            } else if (BluenodesService.BROADCAST_METADATA.equals(action)) {
                mAccessAddress = intent.getIntExtra(BluenodesService.EXTRA_ACCESS_ADDRESS, 0);
                mAdvertisementInterval = intent.getIntExtra(BluenodesService.EXTRA_ADVERTISEMENT_INTERVAL, 0);
                mChannel = intent.getIntExtra(BluenodesService.EXTRA_CHANNEL, 0);
            } else if (BluenodesService.BROADCAST_ERRORNOTIFICATION.equals(action)) {
                byte[] deviceAddress = ParserUtils.reverseArray(Arrays.copyOfRange(intent.getByteArrayExtra(BluenodesService.EXTRA_ERRORCODE), 0, 6));
                int errorCode = intent.getByteArrayExtra(BluenodesService.EXTRA_ERRORCODE)[6];
                Bundle qryName = new Bundle();
                qryName.putByteArray(BluenodesContentProvider.DEVICEADDRESS_ID, deviceAddress);
                qryName.putInt(BluenodesContentProvider.DEVICE_ERROR_CODE, errorCode);
                DeviceError.showError(ControllerActivity.this, getContentResolver().call(BluenodesContentProvider.CONTENT_URI, BluenodesContentProvider.QUERY_NODE_NAME, null, qryName));
            } else if (BluenodesService.BROADCAST_MESSAGENOTIFICATION.equals(action)) {
                byte[] deviceAddress = ParserUtils.reverseArray(Arrays.copyOfRange(intent.getByteArrayExtra(BluenodesService.EXTRA_MESSAGE), 0, 6));
                byte[] bytes = Arrays.copyOfRange(intent.getByteArrayExtra(BluenodesService.EXTRA_MESSAGE), 6, 25);
//                String msg =  ParserUtils.parse(deviceAddress)+" "+String.format("%8s", Integer.toBinaryString(bytes[0] & 0xFF)).replace(' ', '0')+" "+String.format("%8s", Integer.toBinaryString(bytes[1] & 0xFF)).replace(' ', '0');
                Float val = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getFloat();
//                Integer val = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
//                String msg = String.format("%16s", Integer.toBinaryString(val)).replace(' ', '0');
                String msg = val.toString();
                mMessage.setText(msg);
            }
        }
    };

    private static IntentFilter makeIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluenodesService.BROADCAST_GO_DOWN);
        intentFilter.addAction(BluenodesService.BROADCAST_ON_OPEN);
        intentFilter.addAction(BluenodesService.BROADCAST_SHOW_DEVICE);
        intentFilter.addAction(BluenodesService.BROADCAST_SEND_BRIGHTNESS_VALUE);
        intentFilter.addAction(BluenodesService.BROADCAST_REQUEST_BRIGHTNESS_VALUE);
        intentFilter.addAction(BluenodesService.BROADCAST_REQUEST_DEVICEADDRESS_VALUE);
        intentFilter.addAction(BluenodesService.BROADCAST_BUTTON_STATE);
        intentFilter.addAction(BluenodesService.BROADCAST_METADATA);
        intentFilter.addAction(BluenodesService.BROADCAST_ERRORNOTIFICATION);
        intentFilter.addAction(BluenodesService.BROADCAST_SEND_CLEARSTORAGE);
        intentFilter.addAction(BluenodesService.BROADCAST_REQUEST_STORAGE);
        intentFilter.addAction(BluenodesService.BROADCAST_MESSAGENOTIFICATION);
        return intentFilter;
    }

    public Structure.UnitType getTypeMode() {
        return typeMode;
    }

    public void setTypeMode(Structure.UnitType typeMode) {
        this.typeMode = typeMode;
        mStructureTitle.setText(getStructureTitle());
    }

    public long getParentId() {
        return parentId;
    }

    public void setParentId(long parentId) {
        this.parentId = parentId;
    }

    @Override
    protected void onCreateView(Bundle bundle) {
        setContentView(R.layout.activity_controller);
        mStructureList = (ExtendedSwipeListView) findViewById(R.id.structureList);
        mStructureList.setClickable(true);
        mStructureTitle = (TextView) findViewById(R.id.structureTitle);
        mNoStructure = (TextView) findViewById(R.id.empty);
        mBattery = (TextView) findViewById(R.id.battery);
        mMessage = (TextView) findViewById(R.id.message);
        setTypeMode(Structure.UnitType.BUILDING);
        setParentId(0L);
        fillData();
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, makeIntentFilter());
    }

    public String getStructureTitle() {
        Resources res = getResources();
        return res.getString(res.getIdentifier(getTypeMode().name(), "string", getPackageName()));
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.menu_bluenodes, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_about:
                final AppHelpFragment fragment = AppHelpFragment.getInstance(R.string.about_text, true);
                fragment.show(getSupportFragmentManager(), null);
                break;
            case R.id.metadata_settings:
                final MetadataFragment dialog = MetadataFragment.getInstance(mServiceBinder, mAccessAddress, mAdvertisementInterval, mChannel, mConnectedDeviceAddress);
                dialog.show(getSupportFragmentManager(), null);
                break;
            case R.id.insert:
                createStructure();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_CODE_SETTINGS:
                break;
            default:
                break;
        }
    }

    private void createStructure() {
        Intent i = new Intent(this, StructureActivity.class);
        i.putExtra(Structure.UnitTypeExtra, getTypeMode().ordinal());
        i.putExtra(Structure.ParentIdExtra, getParentId());
        startActivity(i);
    }

    private void doDismiss() {
        if(getTypeMode() == Structure.UnitType.ROOM) {
            mStructureList.setSwipeMode(SwipeListView.SWIPE_MODE_RIGHT);
        }
        setTypeMode(Structure.getUnitTypeFromOrdinal(getTypeMode().ordinal() + 1));
        mStructureTitle.setText(getStructureTitle());
        getLoaderManager().restartLoader(0, null, this);
    }

    public void onBackButton(View view) {
        mStructureList.setSwipeMode(SwipeListView.SWIPE_MODE_BOTH);
        if(getTypeMode() == Structure.UnitType.BUILDING) {
            return;
        }
        setTypeMode(Structure.getUnitTypeFromOrdinal(getTypeMode().ordinal() - 1));
        mStructureTitle.setText(getStructureTitle());
        Bundle bundle = new Bundle();
        bundle.putLong(REQUEST_BACK, getParentId());
        getLoaderManager().restartLoader(0, bundle, this);
    }

    public void onHomeButton(View view) {
        mStructureList.setSwipeMode(SwipeListView.SWIPE_MODE_BOTH);
        setTypeMode(Structure.UnitType.BUILDING);
        mStructureTitle.setText(getStructureTitle());
        setParentId(0);
        getLoaderManager().restartLoader(0, null, this);
    }

    public void onSetTime(View view) {
        if (mServiceBinder != null) {
            mServiceBinder.sendSetTime(mConnectedDeviceAddress);
        }
    }

    private void fillData() {
        getLoaderManager().initLoader(0, null, this);
        adapter = new BluenodesCursorAdapter(this, R.layout.structure_row, null, 0);
        adapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                super.onChanged();
                updateHint();
            }

            @Override
            public void onInvalidated() {
                super.onInvalidated();
                updateHint();
            }
        });
        mStructureList.setAdapter(adapter);
    }

    private void updateHint() {
        if(mStructureList.getAdapter().isEmpty()) {
            mNoStructure.setText(getResources().getString(R.string.no_structures) + " " + getStructureTitle());
        }
        else {
            mNoStructure.setText("");
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int loaderId, Bundle args) {
        String[] projection = {
                Structure.Unit._ID,
                Structure.Unit.COLUMN_NAME,
                Structure.Unit.COLUMN_RESOURCE,
                Structure.Unit.COLUMN_PARENT,
                Structure.Unit.COLUMN_TYPE,
                Structure.Unit.COLUMN_CAPABILITY,
                Structure.Unit.COLUMN_DEVICEADDRESS,
                Structure.Unit.COLUMN_DA_HASH};
        if (args != null) {     // back was requested
            Bundle bundle = (Bundle)args;
            long id = bundle.getLong(REQUEST_BACK);
            Uri uri = Uri.parse(BluenodesContentProvider.CONTENT_URI + "/" + id);
            Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
            if (cursor.moveToFirst()) {
                setParentId(cursor.getLong(cursor.getColumnIndex(Structure.Unit.COLUMN_PARENT)));
            }
        }
        String selectionArgs[] = new String[2];
        selectionArgs[0] = String.format("%d", getTypeMode().ordinal());
        selectionArgs[1] = String.format("%d", getParentId());
        String selection = Structure.Unit.COLUMN_TYPE + "=? AND " + Structure.Unit.COLUMN_PARENT + "=?";
        return new CursorLoader(this,
                BluenodesContentProvider.CONTENT_URI, projection, selection, selectionArgs, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        adapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // data is not available anymore, delete reference
        adapter.swapCursor(null);
    }

    @Override
    protected void onServiceBinded(final BluenodesService.BluenodesBinder binder) {
        mServiceBinder = binder;
        mConnectedDeviceAddress = ParserUtils.parse(binder.getDeviceAddress(), false);
    }

    @Override
    protected void onServiceUnbinded() {
        mServiceBinder = null;
        mConnectedDeviceAddress = null;
    }

    @Override
    protected int getLoggerProfileTitle() {
        return R.string.bluenodes_feature_title;
    }

    @Override
    protected boolean isDiscoverableRequired() {
        return false;
    }

    @Override
    protected int getDefaultDeviceName() {
        return R.string.bluenodes_default_name;
    }

    @Override
    protected int getAboutTextId() {
        return R.string.about_text;
    }

    @Override
    protected UUID getFilterUUID() {
        return BluenodesManager.MESH_SERVICE_UUID;
    }

    @Override
    protected void setDefaultUI() {
    }

    @Override
    protected Class<? extends BleProfileService> getServiceClass() {
        return BluenodesService.class;
    }

    @Override
    public void onServicesDiscovered(final boolean optionalServicesFound) {
        // this may notify user or show some views
    }

    @Override
    public void onDeviceReady() {
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    public void onBatteryValueReceived(final int value) {
        mBattery.setText(getString(R.string.battery, value));
    }
}
