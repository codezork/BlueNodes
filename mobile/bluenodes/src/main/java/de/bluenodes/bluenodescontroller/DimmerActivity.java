package de.bluenodes.bluenodescontroller;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.triggertrap.seekarc.SeekArc;

import de.bluenodes.bluenodescontroller.database.model.Structure;

public class DimmerActivity extends AppCompatActivity {
    private String mNodeName;
    private Structure.NodeCapability mCapability;
    private byte[] mDeviceAddress;
    private TextView mTitle;
    private SeekArc mSeekArc;
    private TextView mSeekArcProgress;
    private int oldBrightness = -1;
    private boolean isCallbackTriggered = false;
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();

            if (BluenodesService.BROADCAST_RECEIVE_BRIGHTNESS_VALUE.equals(action)) {
                isCallbackTriggered = true;
                oldBrightness = intent.getIntExtra(BluenodesService.EXTRA_BRIGHTNESS_VALUE, 11);
                mSeekArc.setProgress(oldBrightness);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dimmer);
        Bundle extras = getIntent().getExtras();
        // Or passed from the other activity
        if (extras != null) {
            mNodeName = extras.getString(BluenodesService.EXTRA_NODE_NAME);
            mDeviceAddress = extras.getByteArray(BluenodesService.EXTRA_DEVICEADDRESS);
            mCapability = Structure.getNodeCapabilityFromOrdinal(extras.getInt(BluenodesService.EXTRA_CAPABILITY));
        }
        mTitle = (TextView) findViewById(R.id.dimmerTitle);
        mTitle.setText(mNodeName);
        mSeekArcProgress = (TextView) findViewById(R.id.brightnessProgress);
        mSeekArc = (SeekArc) findViewById(R.id.brightness);
        mSeekArc.setOnSeekArcChangeListener(new SeekArc.OnSeekArcChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekArc seekArc) {
            }

            @Override
            public void onStartTrackingTouch(SeekArc seekArc) {
            }

            @Override
            public void onProgressChanged(SeekArc seekArc, int progress,
                                          boolean fromUser) {
                mSeekArcProgress.setText(String.valueOf(progress));
                if(!isCallbackTriggered) {
                    if(oldBrightness != progress) {
                        final Intent intent = new Intent(BluenodesService.BROADCAST_SEND_BRIGHTNESS_VALUE);
                        intent.putExtra(BluenodesService.EXTRA_BRIGHTNESS_VALUE, (byte)progress);
                        intent.putExtra(BluenodesService.EXTRA_DEVICEADDRESS, mDeviceAddress);
                        LocalBroadcastManager.getInstance(DimmerActivity.this).sendBroadcast(intent);
                        oldBrightness = progress;
                    }
                } else {
                    isCallbackTriggered = false;
                }
            }
        });
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, makeIntentFilter());
        // request brightness value
        final Intent intent = new Intent(BluenodesService.BROADCAST_REQUEST_BRIGHTNESS_VALUE);
        intent.putExtra(BluenodesService.EXTRA_DEVICEADDRESS, mDeviceAddress);
        LocalBroadcastManager.getInstance(DimmerActivity.this).sendBroadcast(intent);
    }

    private static IntentFilter makeIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluenodesService.BROADCAST_RECEIVE_BRIGHTNESS_VALUE);
        return intentFilter;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_dimmer, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
