package com.example.x3config;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class MainActivity extends Activity {
//    public static Context context;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        context=this;
//		setContentView(R.layout.activity_main);
//        if (MyApplication.VERSION.contains("Q5")) {
//            Intent service = new Intent(this, com.listentech.kinward.x3config.ComService.class);// 要启动的Service
//            this.startService(service);
//        } else {
//            Intent service = new Intent(this, com.example.x3config.ComService.class);// 要启动的Service
//            this.startService(service);
//        }
//        Log.i("x3config", "Activity 已经启动成功");
    }
}
