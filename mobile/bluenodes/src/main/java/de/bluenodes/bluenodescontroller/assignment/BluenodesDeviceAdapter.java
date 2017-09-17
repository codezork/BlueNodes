package de.bluenodes.bluenodescontroller.assignment;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;

import de.bluenodes.bluenodescontroller.R;

/**
 * Created by User on 06.02.2016.
 */
public class BluenodesDeviceAdapter extends BaseAdapter {
    private LayoutInflater mInflater;
    private Context mContext;
    private final ArrayList<BluenodesDevice> mListDevices = new ArrayList<>();
    private static final int TYPE_ITEM = 0;
    private static final int TYPE_EMPTY = 1;

    public BluenodesDeviceAdapter(Context context) {
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mContext = context;
//        for(int i=0; i<50; i++) {
//            BluenodesDevice dev = new BluenodesDevice("device"+i, "00:00:00:00:00:00");
//            addDevice(dev);
//        }
    }

    public void addDevice(BluenodesDevice device) {
        boolean found = false;
        for(BluenodesDevice d:mListDevices) {
            if(d.getAddress().equals(device.getAddress())) {
                found = true;
                break;
            }
        }
        if(!found) {
            mListDevices.add(device);
            notifyDataSetChanged();
        }
    }

    public void clearDevices() {
        mListDevices.clear();
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return mListDevices.size();
    }

    @Override
    public Object getItem(int position) {
        return mListDevices.get(position);
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(int position) {
        return getItemViewType(position) == TYPE_ITEM;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == getCount() && mListDevices.isEmpty())
            return TYPE_EMPTY;

        return TYPE_ITEM;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View oldView, ViewGroup parent) {
        final LayoutInflater inflater = LayoutInflater.from(mContext);
        final int type = getItemViewType(position);

        View view = oldView;
        switch (type) {
            case TYPE_EMPTY:
                if (view == null) {
                    view = inflater.inflate(R.layout.device_node_empty, parent, false);
                }
                break;
            default:
                if (view == null) {
                    view = inflater.inflate(R.layout.device_node_row, parent, false);
                    final ViewHolder holder = new ViewHolder();
                    holder.name = (TextView) view.findViewById(R.id.name);
                    holder.address = (TextView) view.findViewById(R.id.address);
                    view.setTag(holder);
                }

                final BluenodesDevice device = (BluenodesDevice) getItem(position);
                final ViewHolder holder = (ViewHolder) view.getTag();
                holder.name.setText(device.getName());
                holder.address.setText(device.getAddress());
                break;
        }
        return view;
    }

    private static class ViewHolder {
        private TextView name;
        private TextView address;
    }

}
