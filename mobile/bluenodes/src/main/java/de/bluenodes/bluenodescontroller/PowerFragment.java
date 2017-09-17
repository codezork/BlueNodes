package de.bluenodes.bluenodescontroller;


import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.github.mikephil.charting.charts.HorizontalBarChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.utils.ColorTemplate;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.text.DateFormatSymbols;

import de.bluenodes.bluenodescontroller.utility.StorageData;

/**
 * Created by User on 15.03.2016.
 */
public class PowerFragment  extends Fragment {
    public static final String ARG_PAGE = "ARG_PAGE";

    private int mPage;
    private TextView mTitle;
    private HorizontalBarChart barChart;
    private StorageData storageData;
    private Button clearButton;

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();

            if (BluenodesService.BROADCAST_DISPLAY_STORAGE.equals(action)) {
                Bundle data = intent.getExtras();
                if(data.getInt(BluenodesService.EXTRA_TABPAGE) == mPage) {
                    storageData = (StorageData) data.getParcelable(BluenodesService.EXTRA_STORAGEDATA);
                    mTitle.setText(data.getString(BluenodesService.EXTRA_SUBTITLE));
                    displayData();
                }
            }
        }
    };

    private static IntentFilter makeIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluenodesService.BROADCAST_DISPLAY_STORAGE);
        return intentFilter;
    }

    private void displayData() {
        barChart.setVisibility(View.VISIBLE);
        barChart.setData(barData(mPage));
//              barChart.setDescription("Description");
//              barChart.setDescriptionTextSize(15f);
        barChart.setDescriptionColor(Color.WHITE);
        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextSize(10f);
        xAxis.setTextColor(Color.WHITE);
        xAxis.setDrawAxisLine(true);
        xAxis.setDrawGridLines(false);
        YAxis yAxis = barChart.getAxisLeft();
        yAxis.setTextSize(10f);
        yAxis.setTextColor(Color.WHITE);
        yAxis.setDrawAxisLine(true);
        yAxis.setDrawGridLines(false);
        Legend legend = barChart.getLegend();
        legend.setTextColor(Color.WHITE);
    }

    public static PowerFragment newInstance(int page) {
        Bundle args = new Bundle();
        args.putInt(ARG_PAGE, page);
        PowerFragment fragment = new PowerFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPage = getArguments().getInt(ARG_PAGE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_power, container, false);
        mTitle = (TextView) view.findViewById(R.id.powerTitle);
        if(mPage < 5) {
            barChart = (HorizontalBarChart) view.findViewById(R.id.chart);
            LocalBroadcastManager.getInstance(getContext()).registerReceiver(mBroadcastReceiver, makeIntentFilter());
        } else {
            clearButton = (Button)view.findViewById(R.id.cleardata);
            clearButton.setVisibility(View.VISIBLE);
            clearButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    builder.setMessage(R.string.are_you_shure).setPositiveButton(R.string.yes, dialogClickListener)
                            .setNegativeButton(R.string.no, dialogClickListener).show();
                }
            });
        }
        return view;
    }

    DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which){
                case DialogInterface.BUTTON_POSITIVE:
                    final Intent broadcast = new Intent(BluenodesService.BROADCAST_SEND_CLEARSTORAGE);
                    LocalBroadcastManager.getInstance(getContext()).sendBroadcast(broadcast);
                    break;

                case DialogInterface.BUTTON_NEGATIVE:
                    //No button clicked
                    break;
            }
        }
    };

    // creating list of x-axis values
    private ArrayList<String> getXAxisValues(int page) {
        ArrayList<String> labels = new ArrayList<>();
        switch (page) {
            case 0:     // per hour
                for (int i = 0; i < 60; i++) {
                    labels.add(new Integer(i + 1).toString());
                }
                break;
            case 1:     // per day
                for (int i = 0; i < 24; i++) {
                    labels.add(new Integer(i).toString());
                }
                break;
            case 2:     // per month
                Calendar cal = Calendar.getInstance();
                int days = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
                for (int i = 0; i < days; i++) {
                    labels.add(new Integer(i + 1).toString());
                }
                break;
            case 3:     // per year
                DateFormatSymbols ds = new DateFormatSymbols(Locale.getDefault());
                for (int i = 0; i < 12; i++) {
                    labels.add(ds.getMonths()[i]);
                }
                break;
            case 4:     // history
                for (int i = 0; i < 30; i++) {
                    labels.add(new Integer(2016 + i).toString());
                }
                break;
        }
        return labels;
    }

    // this method is used to create data for Bar graph
    public BarData barData(int page) {
        ArrayList<BarEntry> group = new ArrayList<>();
        switch (page) {
            case 0:     // current hour's minutes
                for (int i = 0; i < 60; i++) {
                    group.add(new BarEntry(storageData.getFloat(i), i));
                }
                break;
            case 1:     // current day's hours
                for (int i = 0; i < 24; i++) {
                    group.add(new BarEntry(storageData.getFloat(i), i));
                }
                break;
            case 2:     // current month's days
                Calendar cal = Calendar.getInstance();
                int days = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
                for (int i = 0; i < days; i++) {
                    group.add(new BarEntry(storageData.getFloat(i+24), i));
                }
                break;
            case 3:     // current year's months
                DateFormatSymbols ds = new DateFormatSymbols(Locale.getDefault());
                for (int i = 0; i < 12; i++) {
                    group.add(new BarEntry(storageData.getFloat(i), i));
                }
                break;
            case 4:     // history
                for (int i = 0; i < 30; i++) {
                    group.add(new BarEntry(storageData.getFloat(i+12), i));
                }
                break;
        }

        BarDataSet barDataSet = new BarDataSet(group, "kWh");
        barDataSet.setColors(ColorTemplate.JOYFUL_COLORS);

        BarData barData = new BarData(getXAxisValues(page), barDataSet);
        return barData;
    }
}