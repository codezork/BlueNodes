package de.bluenodes.bluenodescontroller;


import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import de.bluenodes.bluenodescontroller.utility.ParserUtils;

/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link MetadataFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link MetadataFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class MetadataFragment extends DialogFragment {
    private static final String binder = "binder";
    private static final String accessAddress = "accessAddress";
    private static final String advertisementInterval = "advertisementInterval";
    private static final String channel = "channel";
    private static final String deviceAddress = "deviceAddress";

    private BluenodesService.BluenodesBinder mBinder;
    private int mAccessAddress;
    private int mAdvertisementInterval;
    private int mChannel;
    private byte[] mDeviceAddress;
    private EditText mTextAccessAddress;
    private EditText mTextAdvertisementInterval;
    private EditText mTextChannel;
    private EditText mTextDeviceAddress;

    public MetadataFragment() {
        // Required empty public constructor
    }

    public static MetadataFragment getInstance(final BluenodesService.BluenodesBinder binder, final int acccessAddress, final int advertisementInterval, final int channel, final byte[] deviceAddress) {
        MetadataFragment fragment = new MetadataFragment();
        Bundle args = new Bundle();
        args.putBinder(MetadataFragment.binder, binder);
        args.putInt(MetadataFragment.accessAddress, acccessAddress);
        args.putInt(MetadataFragment.advertisementInterval, advertisementInterval);
        args.putInt(MetadataFragment.channel, channel);
        args.putByteArray(MetadataFragment.deviceAddress, deviceAddress);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mBinder = (BluenodesService.BluenodesBinder) getArguments().getBinder(binder);
            mAccessAddress = getArguments().getInt(accessAddress);
            mAdvertisementInterval = getArguments().getInt(advertisementInterval);
            mChannel = getArguments().getInt(channel);
            mDeviceAddress = getArguments().getByteArray(deviceAddress);
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        final View dialogView = LayoutInflater.from(getActivity()).inflate(R.layout.fragment_metadata, null);
        builder.setTitle(R.string.metadata_title);
        builder.setCancelable(true);
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(R.string.ok, null);
        mTextAccessAddress = (EditText) dialogView.findViewById(R.id.accessAddress);
        mTextAccessAddress.setText(String.format("%08X", mAccessAddress));
        mTextAdvertisementInterval = (EditText) dialogView.findViewById(R.id.advertisementInterval);
        mTextAdvertisementInterval.setText(String.format("%d", mAdvertisementInterval));
        mTextChannel = (EditText) dialogView.findViewById(R.id.channel);
        mTextChannel.setText(String.format("%d", mChannel));
        mTextDeviceAddress = (EditText) dialogView.findViewById(R.id.deviceAddress);
        mTextDeviceAddress.setText(ParserUtils.parse(mDeviceAddress));
        final AlertDialog dialog = builder.setView(dialogView).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(final DialogInterface dialog) {
                Button ok = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE);
                ok.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(final View view) {
                        int errCnt = 0;
                        mAccessAddress = (int) Long.parseLong(mTextAccessAddress.getText().toString(), 16);
                        mAdvertisementInterval = (int) Long.parseLong(mTextAdvertisementInterval.getText().toString());
                        if (mAdvertisementInterval < 5 || mAdvertisementInterval > 60000) {
                            Toast.makeText(((AlertDialog) dialog).getContext(), R.string.advertisementIntervalError, Toast.LENGTH_SHORT).show();
                            errCnt++;
                        }
                        mChannel = Integer.parseInt(mTextChannel.getText().toString());
                        if (mChannel < 1 || mChannel > 39) {
                            Toast.makeText(((AlertDialog) dialog).getContext(), R.string.channelError, Toast.LENGTH_SHORT).show();
                            errCnt++;
                        }
                        mDeviceAddress = ParserUtils.parse(mTextDeviceAddress.getText().toString(), false);
                        if (errCnt == 0) {
                            if (mBinder != null) {
                                mBinder.sendMetaData(mAccessAddress, mAdvertisementInterval, mChannel);
                            }
                            dismiss();
                        }
                    }
                });
            }
        });
        return dialog;
    }

}
