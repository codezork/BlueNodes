package de.bluenodes.bluenodescontroller;

import android.content.ContentValues;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.HorizontalBarChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.utils.ColorTemplate;

import java.util.ArrayList;
import java.util.Arrays;

import de.bluenodes.bluenodescontroller.assignment.BluenodesDeviceAdapter;
import de.bluenodes.bluenodescontroller.assignment.BluenodesDeviceFragment;
import de.bluenodes.bluenodescontroller.database.contentprovider.BluenodesContentProvider;
import de.bluenodes.bluenodescontroller.database.model.Structure;
import de.bluenodes.bluenodescontroller.icon.AssetAdapter;
import de.bluenodes.bluenodescontroller.utility.ParserUtils;

public class StructureActivity extends AppCompatActivity implements
        BluenodesDeviceFragment.OnDeviceSelectedListener,AdapterView.OnItemSelectedListener {
    private long mId;
    private String mName;
    private Structure.UnitType mType;
    private long mParentId;
    private Structure.NodeCapability mCapability;
    private TextView mTitle;
    private EditText mTextName;
    private Spinner mIconSpinner;
    private String mSelectedIconPath;
    private AssetAdapter adapter;
    private Button mAssignButton;
    private long mNodeId;
    private BluenodesDeviceAdapter mAdapter;
    private String mNodeName;

    private Uri structureUri;

    private byte[] mDeviceAddress;
    private byte[] mNewDeviceAddress;
    @Override
    protected void onCreate(final Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_structure);
        mTitle = (TextView) findViewById(R.id.structure_title);
        mTextName = (EditText) findViewById(R.id.structureName);
        mTextName.requestFocus();
        mIconSpinner = (Spinner) findViewById(R.id.assetIcon);
        adapter = new AssetAdapter(getApplicationContext(), R.layout.asset_row);
        mIconSpinner.setAdapter(adapter);
        mIconSpinner.setOnItemSelectedListener(this);
        Button saveButton = (Button) findViewById(R.id.save_button);
        Button powerButton = (Button) findViewById(R.id.power_statistics);
        mAssignButton = (Button) findViewById(R.id.assign);

        mAdapter = new BluenodesDeviceAdapter(StructureActivity.this);
        Bundle extras = getIntent().getExtras();
        // check from the saved Instance
        structureUri = (bundle == null) ? null : (Uri) bundle.getParcelable(BluenodesContentProvider.CONTENT_ITEM_TYPE);

        // Or passed from the other activity
        if (extras != null) {
            structureUri = extras.getParcelable(BluenodesContentProvider.CONTENT_ITEM_TYPE);
            mType = Structure.getUnitTypeFromOrdinal(extras.getInt(Structure.UnitTypeExtra));
            Resources res = getResources();
            mTitle.setText(res.getString(res.getIdentifier(mType.name(), "string", getPackageName())));
            mParentId = extras.getLong(Structure.ParentIdExtra);
            if (structureUri != null) {
                fillData(structureUri);
            }
        }
        if (mType == Structure.UnitType.NODE) {
            mAssignButton.setVisibility(View.VISIBLE);
        }

        saveButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                if (TextUtils.isEmpty(mTextName.getText().toString())) {
                    makeToast();
                } else {
                    saveState();
                    setResult(RESULT_OK);
                    finish();
                }
            }
        });

        mAssignButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                showDeviceSelectionDialog();
            }
        });

        powerButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                Intent i = new Intent(StructureActivity.this, PowerActivity.class);
                i.putExtra(BluenodesService.EXTRA_DEVICEADDRESS, mDeviceAddress);
                i.putExtra(BluenodesService.EXTRA_NODE_NAME, mNodeName);
                startActivity(i);
            }
        });
    }

    /**
     * Shows the scanner fragment.
     *
     * @param filter               the UUID filter used to filter out available devices. The fragment will always show all bonded devices as there is no information about their
     *                             services
     * @param discoverableRequired <code>true</code> if devices must have GENERAL_DISCOVERABLE or LIMITED_DISCOVERABLE flags set in their advertisement packet
     * @see #getFilterUUID()
     */
    protected void showDeviceSelectionDialog() {
        final BluenodesDeviceFragment dialog = BluenodesDeviceFragment.getInstance();
        dialog.show(getFragmentManager(), "device_fragment");
        dialog.setAdapter(mAdapter);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }


    private void fillData(Uri uri) {
        String[] projection = {
                Structure.Unit._ID,
                Structure.Unit.COLUMN_NAME,
                Structure.Unit.COLUMN_RESOURCE,
                Structure.Unit.COLUMN_PARENT,
                Structure.Unit.COLUMN_TYPE,
                Structure.Unit.COLUMN_CAPABILITY,
                Structure.Unit.COLUMN_DEVICEADDRESS};
        String selection = Structure.Unit.COLUMN_TYPE+"=? AND "+Structure.Unit.COLUMN_PARENT+"=?";
        String selectionArgs[] = new String[2];
        selectionArgs[0] = String.format("%d", mType.ordinal());
        selectionArgs[1] = String.format("%d", mParentId);
        Cursor cursor = getContentResolver().query(uri, projection, selection, selectionArgs, null);
        if (cursor != null) {
            if(cursor.moveToFirst()) {
                mNodeId = cursor.getLong(cursor.getColumnIndexOrThrow(Structure.Unit._ID));
                mNodeName = cursor.getString(cursor.getColumnIndexOrThrow(Structure.Unit.COLUMN_NAME));
                mTextName.setText(mNodeName);
                mDeviceAddress = cursor.getBlob(cursor.getColumnIndexOrThrow(Structure.Unit.COLUMN_DEVICEADDRESS));
                if (cursor.getString(cursor.getColumnIndexOrThrow(Structure.Unit.COLUMN_RESOURCE)) != null) {
                    int spinnerPosition = adapter.getPosition(cursor.getString(cursor.getColumnIndexOrThrow(Structure.Unit.COLUMN_RESOURCE)));
                    mIconSpinner.setSelection(spinnerPosition);
                }
            }
            // always close the cursor
            cursor.close();
        }
    }

    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(BluenodesContentProvider.CONTENT_ITEM_TYPE, structureUri);
    }

//    @Override
//    protected void onPause() {
//        super.onPause();
//    }

    private void saveState() {
        String name = mTextName.getText().toString();

        // only save if either one
        // is available

        if (name.length() == 0) {
            return;
        }

        ContentValues values = new ContentValues();
        values.put(Structure.Unit.COLUMN_NAME, name);
        values.put(Structure.Unit.COLUMN_RESOURCE, mSelectedIconPath);
        values.put(Structure.Unit.COLUMN_TYPE, mType.ordinal());
        values.put(Structure.Unit.COLUMN_PARENT, mParentId);
        if (mNewDeviceAddress != null) {
            values.put(Structure.Unit.COLUMN_DEVICEADDRESS, mNewDeviceAddress);
            values.put(Structure.Unit.COLUMN_DA_HASH, Arrays.hashCode(mNewDeviceAddress));
        } else {
            values.put(Structure.Unit.COLUMN_DEVICEADDRESS, mDeviceAddress);
            values.put(Structure.Unit.COLUMN_DA_HASH, Arrays.hashCode(mDeviceAddress));
        }

        if (structureUri == null) {
            // New structure
            structureUri = getContentResolver().insert(BluenodesContentProvider.CONTENT_URI, values);
        } else {
            // Update structure
            getContentResolver().update(structureUri, values, null, null);
        }
        // in case deviceAddress was overwritten, the other reference must be discarded
        if (mNewDeviceAddress != null) {
            String[] projection = {Structure.Unit._ID};
            String selectionArgs[] = new String[2];
            selectionArgs[0] = String.format("%d", Arrays.hashCode(mDeviceAddress));
            selectionArgs[1] = structureUri.getLastPathSegment();
            String selection = Structure.Unit.COLUMN_DA_HASH + "=? AND " + Structure.Unit._ID + "<>?";
            Cursor cursor = getContentResolver().query(BluenodesContentProvider.CONTENT_URI, projection, selection, selectionArgs, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    // we found one
                    long id = cursor.getLong(cursor.getColumnIndexOrThrow(Structure.Unit._ID));
                    Uri updateUri = Uri.parse(BluenodesContentProvider.CONTENT_URI + "/" + id);
                    ContentValues updates = new ContentValues();
                    updates.putNull(Structure.Unit.COLUMN_DEVICEADDRESS);
                    updates.putNull(Structure.Unit.COLUMN_DA_HASH);
                    getContentResolver().update(updateUri, updates, null, null);
                }
            }
        }
        mDeviceAddress = mNewDeviceAddress;
    }

    private void makeToast() {
        Toast.makeText(StructureActivity.this, R.string.name_empty, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        mSelectedIconPath = (String)parent.getAdapter().getItem(position);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        mSelectedIconPath = null;
    }

    @Override
    public void onDeviceSelected(String name, String address) {
        mNewDeviceAddress = ParserUtils.parse(address, false);
    }

    @Override
    public void onDialogCanceled() {

    }
}
