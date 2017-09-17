package de.bluenodes.bluenodescontroller.database.contentprovider;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;

import de.bluenodes.bluenodescontroller.ControllerActivity;
import de.bluenodes.bluenodescontroller.R;
import de.bluenodes.bluenodescontroller.StructureActivity;
import de.bluenodes.bluenodescontroller.database.model.Structure;
import de.bluenodes.bluenodescontroller.icon.AssetAdapter;

/**
 * Created by User on 19.09.2015.
 */
public class BluenodesCursorAdapter extends ResourceCursorAdapter {
    public static final String BROADCAST_FILL_DATA = "de.bluenodes.bluenodescontroller.BROADCAST_FILL_DATA";
    private LayoutInflater mInflater;
    private AssetManager assetManager;
    private Context context;

    public BluenodesCursorAdapter(Context context, int layout, Cursor c, int flags) {
        super(context, layout, c, flags);
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        assetManager = context.getAssets();
        this.context = context;
    }

    public Context getContext() {
        return context;
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return mInflater.inflate(R.layout.structure_row, parent, false);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        final Context ctx = context;
        final long id = cursor.getLong(cursor.getColumnIndex(Structure.Unit._ID));
        ViewHolder holder;
        if (view.getTag() == null) {
            holder = new ViewHolder();
            holder.ivImage = (ImageView) view.findViewById(R.id.icon);
            holder.tvLabel = (TextView) view.findViewById(R.id.label);
            holder.ivIsAssigned = (ImageView) view.findViewById(R.id.isAssigned);
            holder.editButton = (ImageButton) view.findViewById(R.id.edit_button);
            holder.deleteButton = (ImageButton) view.findViewById(R.id.delete_button);
            view.setTag(holder);
        } else {
            holder = (ViewHolder) view.getTag();
        }

        holder.tvLabel.setText(cursor.getString(cursor.getColumnIndex(Structure.Unit.COLUMN_NAME)));
        if (cursor.getBlob(cursor.getColumnIndex(Structure.Unit.COLUMN_DEVICEADDRESS)) != null) {
            holder.ivIsAssigned.setVisibility(View.VISIBLE);
            holder.ivIsAssigned.setImageResource(R.drawable.assigned);
        } else {
            holder.ivIsAssigned.setVisibility(View.INVISIBLE);
        }

        String resourceName = cursor.getString(cursor.getColumnIndex(Structure.Unit.COLUMN_RESOURCE));
        if (resourceName != null) {
            try {
                InputStream ims = assetManager.open(AssetAdapter.AssetDirectory +"/"+resourceName);
                Drawable d = Drawable.createFromStream(ims, null);
                holder.ivImage.setImageDrawable(d);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        holder.editButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(ctx, StructureActivity.class);
                Uri structureUri = Uri.parse(BluenodesContentProvider.CONTENT_URI + "/" + id);
                i.putExtra(BluenodesContentProvider.CONTENT_ITEM_TYPE, structureUri);
                i.putExtra(Structure.UnitTypeExtra, ((ControllerActivity) ctx).getTypeMode().ordinal());
                i.putExtra(Structure.ParentIdExtra, ((ControllerActivity) ctx).getParentId());
                ctx.startActivity(i);
            }
        });
        holder.deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Uri uri = Uri.parse(BluenodesContentProvider.CONTENT_URI + "/" + id);
                ctx.getContentResolver().delete(uri, null, null);
//                final Intent broadcast = new Intent(BROADCAST_FILL_DATA);
//                LocalBroadcastManager.getInstance(ctx).sendBroadcast(broadcast);
            }
        });

//        holder.clearButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
//                builder.setMessage(R.string.are_you_shure).setPositiveButton(R.string.yes, dialogClickListener)
//                        .setNegativeButton(R.string.no, dialogClickListener).show();
//            }
//        });

    }

//    DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
//        @Override
//        public void onClick(DialogInterface dialog, int which) {
//            switch (which) {
//                case DialogInterface.BUTTON_POSITIVE:
//                    final Intent broadcast = new Intent(BluenodesService.BROADCAST_SEND_CLEARSTORAGE);
//                    LocalBroadcastManager.getInstance(context).sendBroadcast(broadcast);
//                    break;
//                case DialogInterface.BUTTON_NEGATIVE:
//                    break;
//            }
//        }
//    };

    private static class ViewHolder {
        ImageView ivImage;
        TextView tvLabel;
        ImageView ivIsAssigned;
        ImageButton editButton;
        ImageButton deleteButton;
    }
}
