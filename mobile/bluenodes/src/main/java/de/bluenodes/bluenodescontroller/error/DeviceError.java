package de.bluenodes.bluenodescontroller.error;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;

import de.bluenodes.bluenodescontroller.R;
import de.bluenodes.bluenodescontroller.database.contentprovider.BluenodesContentProvider;

/**
 * Created by User on 07.03.2016.
 */
public class DeviceError {

    static final int CSERROR_HARDWARE = 1;
    static final int CSERROR_OVERLOAD = 2;
    static final int CSERROR_OVERTEMP = 4;
    static final int CSERROR_COMMUNICATION = 8;
    static final int CSERROR_OVERCURRENT = 16;

    static boolean isShowing = false;

    public static void showError(Context context, final Bundle qryName) {
        if (isShowing) {
            return;
        }
        isShowing = true;

        AlertDialog alertDialog = new AlertDialog.Builder(context).create();
        alertDialog.setTitle(R.string.device_error);
        alertDialog.setMessage(parseDeviceError(context, qryName.getInt(BluenodesContentProvider.DEVICE_ERROR_CODE), qryName.getString(BluenodesContentProvider.NODE_NAME)));
        alertDialog.setIcon(R.drawable.chip);
        alertDialog.setButton(context.getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                isShowing = false;
            }
        });
        alertDialog.show();
    }

    private static String parseDeviceError(Context context, int error, String deviceName) {
        String message = deviceName + " ";
        if ((error & CSERROR_HARDWARE) > 0) {
            message += context.getResources().getString(R.string.hardware_error);
        }
        if ((error & CSERROR_OVERLOAD) > 0) {
            message += context.getResources().getString(R.string.overload_error);
        }
        if ((error & CSERROR_OVERTEMP) > 0) {
            message += context.getResources().getString(R.string.overtemp_error);
        }
        if ((error & CSERROR_COMMUNICATION) > 0) {
            message += context.getResources().getString(R.string.comm_error);
        }
        if ((error & CSERROR_OVERCURRENT) > 0) {
            message += context.getResources().getString(R.string.overcurrent_error);
        }
        return message;
    }
}
