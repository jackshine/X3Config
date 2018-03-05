package com.example.x3config;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.example.x3config.utils.CrashHandler;
import com.example.x3config.utils.LogCatHelper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Properties;

import static android.content.ContentValues.TAG;

public class MyApplication extends Application {
	public static Context context;
    public static String VERSION="X3";
   public static String FPGApath = "sdcard/updata.ups";
    @Override
    public void onCreate() {
        super.onCreate();
        context = this;
        //获取版本
        VERSION = getVersion();
        Log.e("x3config", VERSION +"");
//        CrashHandler.getInstance().init(this);
//        LogCatHelper.getInstance(this, "/sdcard/log_config").start();
    }
    public String getVersion() {
        String configpath = "/sdcard/listen/config/paramConfig.ini";
        FileInputStream fis = null; // 读
        OutputStream fos;
        String devEquipmentType = null;
        Properties pp = new Properties();
        try {
            fis = new FileInputStream(configpath);
            pp.load(fis);
            devEquipmentType = pp.get("devEquipmentType").toString();// 同上
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return devEquipmentType;
    }


    /**
     * 读文件
     */

    public static byte[] readFile(String path, Context context) {
        FileInputStream input = null;
        try {
            input = new FileInputStream(new File(path));
        } catch (FileNotFoundException e) {
            Log.e(TAG, "readFile: 升级文件未找到");
            return null;
        }
        //字节数组输出流，可以捕获内存缓冲区的数据，转换成字节数组。
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        byte[] bytes = new byte[1024];
        int length;
        //从文件出入流中读取，已数组类型存入byte中
        try {
            while ((length = input.read(bytes)) != -1) {
                //把数据写入流里
                stream.write(bytes, 0, length);
            }
            input.close();
            stream.close();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return stream.toByteArray();
    }
    public static byte[] hexStringToBytes(String hexString) {
        if (hexString == null || hexString.equals("")) {
            return null;
        }
        hexString = hexString.toUpperCase();
        int length = hexString.length() / 2;
        char[] hexChars = hexString.toCharArray();
        byte[] d = new byte[length];
        for (int i = 0; i < length; i++) {
            int pos = i * 2;
            d[i] = (byte) (charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));
        }
        return d;
    }  private static byte charToByte(char c) {
        return (byte) "0123456789ABCDEF".indexOf(c);
    }public static  String bytesToHexString(byte[] bArray) {
        StringBuffer sb = new StringBuffer(bArray.length);
        String sTemp;
        for (int i = 0; i < bArray.length; i++) {
            sTemp = Integer.toHexString(0xFF & bArray[i]);
            if (sTemp.length() < 2)
                sb.append(0);
            sb.append(sTemp.toUpperCase());
        }
        return sb.toString();
    }
}
