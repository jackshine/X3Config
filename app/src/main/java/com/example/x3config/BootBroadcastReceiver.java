package com.example.x3config;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootBroadcastReceiver extends BroadcastReceiver {
    static final String ACTION = "android.intent.action.BOOT_COMPLETED";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("BootBroadcastReceiver", "intent.getAction() ===== " + intent.getAction());
        if (intent.getAction().equals(ACTION)) {

            //1.启动一个Activity
//            Intent mainActivityIntent = new Intent(context, MainActivity.class);// 要启动的Activity
//            Log.d("xxx","开机自启动一个Activity");
//            mainActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//            context.startActivity(mainActivityIntent);

            //2.启动一个Service

            if (MyApplication.VERSION.equals("Q5")) {
                Intent service = new Intent(context, com.listentech.kinward.x3config.ComService.class);// 要启动的Service
                context.startService(service);
            } else {
                Intent service = new Intent(context, com.example.x3config.ComService.class);// 要启动的Service
                context.startService(service);
            }

            Log.d("BootBroadcastReceiver", "开机自启动一个Service");

            //3.启动一个app
//            Intent app = context.getPackageManager().getLaunchIntentForPackage("com.example.x3config");//包名
//            context.startActivity(app);

        }
    }
}