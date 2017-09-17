package de.bluenodes.bluenodescontroller.icon;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;

import java.io.IOException;
import java.io.InputStream;

import de.bluenodes.bluenodescontroller.R;

/**
 * Created by User on 21.09.2015.
 */
public class AssetAdapter extends ArrayAdapter<String> {
    private LayoutInflater mInflater;
    private Context ctxt;
    private AssetManager assetManager;
    public final static String AssetDirectory = "icons";

    public AssetAdapter(Context context, int resource) {
        super(context, resource);
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        ctxt = context;
        assetManager = context.getAssets();
        try {
            addAll(assetManager.list(AssetDirectory));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public View getDropDownView(int position, View convertView,
                                ViewGroup parent) {
        return getCustomView(position, convertView, parent);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return getCustomView(position, convertView, parent);
    }

    public View getCustomView(int position, View convertView, ViewGroup parent) {
        ViewHolder iconHolder;
        String filePath = (String)getItem(position);
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.asset_row, parent, false);
            iconHolder = new ViewHolder();
            iconHolder.ivIcon = (ImageView) convertView.findViewById(R.id.assetIcon);
            convertView.setTag(iconHolder);
        } else {
            iconHolder = (ViewHolder)convertView.getTag();
        }

        try {
            InputStream ims = assetManager.open(AssetDirectory +"/"+filePath);
            Drawable d = Drawable.createFromStream(ims, null);
            iconHolder.ivIcon.setImageDrawable(d);
            iconHolder.resourcePath = filePath;
        }
        catch(IOException ex) {
            ex.printStackTrace();
        }
        return convertView;
    }

    private static class ViewHolder {
        ImageView ivIcon;
        String resourcePath;
    }
}
