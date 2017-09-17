package de.bluenodes.bluenodescontroller.swipe;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Build;
import android.support.v4.content.LocalBroadcastManager;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.AbsListView;
import android.widget.ListView;

import com.fortysevendeg.swipelistview.BaseSwipeListViewListener;
import com.fortysevendeg.swipelistview.SwipeListView;

import de.bluenodes.bluenodescontroller.BluenodesService;
import de.bluenodes.bluenodescontroller.database.contentprovider.BluenodesCursorAdapter;
import de.bluenodes.bluenodescontroller.database.model.Structure;

/**
 * Created by User on 19.09.2015.
 */
public class ExtendedSwipeListView extends SwipeListView {
    private int swipeMode;
    private BluenodesCursorAdapter adapter;

    public ExtendedSwipeListView(Context context, int swipeBackView, int swipeFrontView) {
        super(context, swipeBackView, swipeFrontView);
        init();
    }

    public ExtendedSwipeListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ExtendedSwipeListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {

                @Override
                public void onItemCheckedStateChanged(ActionMode mode, int position,
                                                      long id, boolean checked) {
                    mode.setTitle("Selected (" + getCountSelected() + ")");
                }

                @Override
                public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                    return false;
                }

                @Override
                public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                    return true;
                }

                @Override
                public void onDestroyActionMode(ActionMode mode) {
                    unselectedChoiceStates();
                }

                @Override
                public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                    return false;
                }
            });
        }

        setSwipeListViewListener(new BaseSwipeListViewListener() {

            @Override
            public void onOpened(int position, boolean toRight) {
            }

            @Override
            public void onClosed(int position, boolean fromRight) {
            }

            @Override
            public void onListChanged() {
            }

            @Override
            public void onMove(int position, float x) {
            }

            @Override
            public void onStartOpen(int position, int action, boolean right) {
                // set parent id
                BluenodesCursorAdapter adapter = (BluenodesCursorAdapter) getAdapter();
                Cursor cursor = (Cursor) adapter.getItem(position);
                final Intent broadcast = new Intent(BluenodesService.BROADCAST_ON_OPEN);
                if (right) {
                    broadcast.putExtra(BluenodesService.EXTRA_PARENT_ID, cursor.getLong(cursor.getColumnIndex(Structure.Unit.COLUMN_PARENT)));
                } else {
                    broadcast.putExtra(BluenodesService.EXTRA_PARENT_ID, cursor.getLong(cursor.getColumnIndex(Structure.Unit._ID)));
                }
                LocalBroadcastManager.getInstance(adapter.getContext()).sendBroadcast(broadcast);
            }

            @Override
            public void onStartClose(int position, boolean right) {
            }

            @Override
            public void onClickFrontView(int position) {
                // not allowed if swipemode left is disabled
                if (getSwipeMode() != SWIPE_MODE_RIGHT) {
                    adapter = (BluenodesCursorAdapter) getAdapter();
                    Cursor cursor = (Cursor) adapter.getItem(position);
                    final Intent broadcast = new Intent(BluenodesService.BROADCAST_GO_DOWN);
                    broadcast.putExtra(BluenodesService.EXTRA_PARENT_ID, cursor.getLong(cursor.getColumnIndex(Structure.Unit._ID)));
                    LocalBroadcastManager.getInstance(adapter.getContext()).sendBroadcast(broadcast);
                } else {    // enter device mode
                    adapter = (BluenodesCursorAdapter) getAdapter();
                    Cursor cursor = (Cursor) adapter.getItem(position);
                    final Intent broadcast = new Intent(BluenodesService.BROADCAST_SHOW_DEVICE);
                    if (cursor.getBlob(cursor.getColumnIndex(Structure.Unit.COLUMN_DEVICEADDRESS)) != null) {
                        broadcast.putExtra(BluenodesService.EXTRA_NODE_NAME, cursor.getString(cursor.getColumnIndex(Structure.Unit.COLUMN_NAME)));
                        broadcast.putExtra(BluenodesService.EXTRA_NODE_ID, cursor.getLong(cursor.getColumnIndex(Structure.Unit._ID)));
                        broadcast.putExtra(BluenodesService.EXTRA_DEVICEADDRESS, cursor.getBlob(cursor.getColumnIndex(Structure.Unit.COLUMN_DEVICEADDRESS)));
                        broadcast.putExtra(BluenodesService.EXTRA_CAPABILITY, cursor.getInt(cursor.getColumnIndex(Structure.Unit.COLUMN_CAPABILITY)));
                        LocalBroadcastManager.getInstance(adapter.getContext()).sendBroadcast(broadcast);
                    }
                }
            }

            @Override
            public void onClickBackView(int position) {
                adapter = (BluenodesCursorAdapter) getAdapter();
                Cursor cursor = (Cursor) adapter.getItem(position);
                final Intent broadcast = new Intent(BluenodesService.BROADCAST_ON_OPEN);
                broadcast.putExtra(BluenodesService.EXTRA_PARENT_ID, cursor.getLong(cursor.getColumnIndex(Structure.Unit.COLUMN_PARENT)));
                LocalBroadcastManager.getInstance(adapter.getContext()).sendBroadcast(broadcast);
            }

            @Override
            public void onDismiss(int[] reverseSortedPositions) {
                // dismiss
                adapter = (BluenodesCursorAdapter) getAdapter();
                final Intent broadcast = new Intent(BluenodesService.BROADCAST_GO_DOWN);
                LocalBroadcastManager.getInstance(adapter.getContext()).sendBroadcast(broadcast);
            }

        });
    }

    public int convertDpToPixel(float dp) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float px = dp * (metrics.densityDpi / 160f);
        return (int) px;
    }

    public int getSwipeMode() {
        return swipeMode;
    }

    @Override
    public void setSwipeMode(int swipeMode) {
        this.swipeMode = swipeMode;
        super.setSwipeMode(swipeMode);
    }
}
