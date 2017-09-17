package de.bluenodes.bluenodescontroller;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;

import java.util.Calendar;

import de.bluenodes.bluenodescontroller.utility.StorageData;


public class PowerActivity extends AppCompatActivity {
    private String mNodeName;
    private byte[] mDeviceAddress;
    private FragmentPagerAdapter adapterViewPager;
    private StorageData.Level lastFetchedLevel = null;
    private String mSubTitle;
    private StorageData storageData;
    private int mPage;

    private void displayStorageData() {
        final Intent send = new Intent(BluenodesService.BROADCAST_DISPLAY_STORAGE);
        send.putExtra(BluenodesService.EXTRA_SUBTITLE, mSubTitle);
        send.putExtra(BluenodesService.EXTRA_STORAGEDATA, storageData);
        send.putExtra(BluenodesService.EXTRA_TABPAGE, mPage);
        LocalBroadcastManager.getInstance(PowerActivity.this).sendBroadcast(send);
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();

            if (BluenodesService.BROADCAST_RECEIVE_STORAGE.equals(action)) {
                Bundle data = intent.getExtras();
                storageData = (StorageData) data.getParcelable(BluenodesService.EXTRA_STORAGEDATA);
                lastFetchedLevel = StorageData.Level.getLevel(storageData.getLevel());
                displayStorageData();
            }
        }
    };

    private static IntentFilter makeIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluenodesService.BROADCAST_RECEIVE_STORAGE);
        return intentFilter;
    }

    private void requestStorageData(StorageData.Level level) {
        // request storage data
        if(!level.equals(lastFetchedLevel)) {
            final Intent intent = new Intent(BluenodesService.BROADCAST_REQUEST_STORAGE);
            intent.putExtra(BluenodesService.EXTRA_DEVICEADDRESS, mDeviceAddress);
            intent.putExtra(BluenodesService.EXTRA_STORAGELEVEL, level.getValue());
            LocalBroadcastManager.getInstance(PowerActivity.this).sendBroadcast(intent);
        } else {
            displayStorageData();
        }
    }

    public void showPage(int position) {
        Calendar now = Calendar.getInstance();
        mPage = position;
        switch (position) {
            case 0:     // per hour
                mSubTitle = ""+now.get(Calendar.HOUR_OF_DAY) + ":00 ";
                requestStorageData(StorageData.Level.MINUTE);
                break;
            case 1:     // per day
                mSubTitle ="" + now.get(Calendar.DATE)+"." + (now.get(Calendar.MONTH)+1) + "." + now.get(Calendar.YEAR);
                requestStorageData(StorageData.Level.DAY);
                break;
            case 2:     // per month
                int days = now.getActualMaximum(Calendar.DAY_OF_MONTH);
                mSubTitle = "1.-"+days+"." + (now.get(Calendar.MONTH)+1) + "." + now.get(Calendar.YEAR);
                requestStorageData(StorageData.Level.DAY);
                break;
            case 3:     // year
                mSubTitle = "" + now.get(Calendar.YEAR);
                requestStorageData(StorageData.Level.YEAR);
                break;
            case 4:     // history
                mSubTitle = "";
                requestStorageData(StorageData.Level.YEAR);
                break;
        }
    }

    @Override
    protected void onCreate(final Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_power);
        TabLayout tabLayout = (TabLayout) findViewById(R.id.sliding_tabs);
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            mDeviceAddress = extras.getByteArray(BluenodesService.EXTRA_DEVICEADDRESS);
            mNodeName = extras.getString(BluenodesService.EXTRA_NODE_NAME);
        }
        // Get the ViewPager and set it's PagerAdapter so that it can display items
        final ViewPager viewPager = (ViewPager) findViewById(R.id.viewpager);
        adapterViewPager = new PowerFragmentPagerAdapter(getSupportFragmentManager(),
                PowerActivity.this);
        viewPager.setAdapter(adapterViewPager);
        tabLayout.setupWithViewPager(viewPager);
        viewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout) {
            // This method will be invoked when a new page becomes selected.
            @Override
            public void onPageSelected(int position) {
                showPage(position);
            }
        });
        showPage(0);
        LocalBroadcastManager.getInstance(PowerActivity.this).registerReceiver(mBroadcastReceiver, makeIntentFilter());
    }

    public class PowerFragmentPagerAdapter extends FragmentPagerAdapter {
        final int PAGE_COUNT = 6;
        private String tabTitles[] = new String[] { "HOUR", "DAY", "MONTH", "YEAR", "HISTORY", "SETTINGS" };

        public PowerFragmentPagerAdapter(FragmentManager fm, Context context) {
            super(fm);
        }

        @Override
        public int getCount() {
            return PAGE_COUNT;
        }

        @Override
        public Fragment getItem(int position) {
            return PowerFragment.newInstance(position);
        }

        @Override
        public CharSequence getPageTitle(int position) {
            // Generate title based on item position
            return getResources().getString(getResources().getIdentifier(tabTitles[position], "string", getPackageName()));
        }
    }
}
