package com.listentech.kinward.x3config;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.example.x3config.MyApplication;
import com.example.x3config.ServerUdp;
import com.example.x3config.utils.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ComService extends Service {
    @Override
    public IBinder onBind(Intent arg0) {//这是Service必须要实现的方法，目前这里面什么都没有做
        return null;
    }
    @Override
    public void onCreate() {//在onCreate()方法中打印了一个log便于测试
        super.onCreate();
        Log.i("x3config",MyApplication.VERSION+ "  --Service 已经启动成功");
        if (MyApplication.VERSION.contains("Q5"))
        new ServerUdp().start();
    }

    // 初始化SPI为默认数据或者上次保存的数据，并且将该数据复制到FLASH中
    public void solidify() {
        System.out.println("solidify--------");
        File file = new File("sdcard/solidify.txt");// 固化文件存储，用于每次上电后发送给FPGA，类似SPI
        File filecrash = new File("sdcard/flashdata.txt");// 临时数据缓存,类似FLASH
        if (!file.exists()) {
            try {
                file.createNewFile();
                FileOutputStream out = new FileOutputStream(file, true);
                StringBuffer sb = new StringBuffer();
                // 共有属性

                sb.append("bright,");// 亮度
                sb.append("screen,");// 屏参
                sb.append("reseves,");// 预留
                sb.append("red,");// 伽马红
                sb.append("green,");// 伽马绿
                sb.append("blue,");// 伽马蓝
                sb.append("string,");// 走线
                sb.append("roworder,");// 行序
                sb.append("nomal,");// 常规芯片寄存器
                sb.append("bitscan,");// Bit表
                sb.append("bitoewide,");// Bit表
                sb.append("-,");// 级联端口
                // 高刷芯片属性
                sb.append("-,");// 高刷芯片刷新算法数据表
                sb.append("1");//几张卡
                out.write(sb.toString().getBytes("utf-8"));

                out.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        if (!filecrash.exists()) {
            try {
                filecrash.createNewFile();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        FileUtils.copyFile(file, filecrash);// 复制SPI文件到FLASH
    }
}