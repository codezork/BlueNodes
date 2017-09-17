package de.bluenodes.bluenodescontroller.assignment;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import de.bluenodes.bluenodescontroller.BluenodesService;
import de.bluenodes.bluenodescontroller.R;

/**
 * A fragment representing a list of Items.
 * <p/>
 * Large screen devices (such as tablets) are supported by replacing the ListView
 * with a GridView.
 * <p/>
 * Activities containing this fragment MUST implement the {@link OnFragmentInteractionListener}
 * interface.
 */
public class BluenodesDeviceFragment extends DialogFragment {
    private ListView listview;
    private BluenodesDeviceAdapter mAdapter;

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();

            if (BluenodesService.BROADCAST_RECEIVE_DEVICEADDRESS_VALUE.equals(action)) {
                byte[] address = intent.getByteArrayExtra(BluenodesService.EXTRA_DEVICEADDRESS_VALUE);
                mAdapter.addDevice(new BluenodesDevice(address));
            }
        }
    };

    /**
     * The Adapter which will be used to populate the ListView/GridView with
     * Views.
     */
    private OnDeviceSelectedListener mListener;

    public static BluenodesDeviceFragment getInstance() {
        BluenodesDeviceFragment fragment = new BluenodesDeviceFragment();
        return fragment;
    }

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public BluenodesDeviceFragment() {
    }

    /**
     * This will make sure that {@link OnDeviceSelectedListener} interface is implemented by activity.
     */
    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);
        try {
            this.mListener = (OnDeviceSelectedListener) activity;
        } catch (final ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement OnDeviceSelectedListener");
        }
    }

    public void setAdapter(BluenodesDeviceAdapter adapter) {
        mAdapter = adapter;
    }
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        final View dialogView = LayoutInflater.from(getActivity()).inflate(R.layout.fragment_device_list, null);
        listview = (ListView) dialogView.findViewById(android.R.id.list);
        listview.setAdapter(mAdapter);
        listview.setEmptyView(dialogView.findViewById(android.R.id.empty));

        builder.setTitle(R.string.scanner_title);
        final AlertDialog dialog = builder.setView(dialogView).create();
        listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
                dialog.dismiss();
                final BluenodesDevice d = (BluenodesDevice) mAdapter.getItem(position);
                mListener.onDeviceSelected(d.getName(), d.getAddress());
            }
        });

        LocalBroadcastManager.getInstance(getActivity().getBaseContext()).registerReceiver(mBroadcastReceiver, makeIntentFilter());
        mAdapter.clearDevices();
        final Intent intent = new Intent(BluenodesService.BROADCAST_REQUEST_DEVICEADDRESS_VALUE);
        LocalBroadcastManager.getInstance(getActivity().getBaseContext()).sendBroadcast(intent);
        return dialog;
    }

    private static IntentFilter makeIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluenodesService.BROADCAST_RECEIVE_DEVICEADDRESS_VALUE);
        return intentFilter;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    private void addAnsweredDevice(final BluenodesDevice device) {
        if (getActivity() != null)
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mAdapter.addDevice(device);
                }
            });
    }
    /**
     * Interface required to be implemented by activity.
     */
    public static interface OnDeviceSelectedListener {
        public void onDeviceSelected(final String name, final String address);
        public void onDialogCanceled();
    }
}
