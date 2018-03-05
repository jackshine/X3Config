package com.example.x3config;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

import com.example.x3config.utils.FileUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.IllegalFormatCodePointException;
import java.util.List;
import java.util.StringTokenizer;

import android_serialport_api.SerialPort;

import static android.content.ContentValues.TAG;

public class ServerUdp extends Thread {
    private int CARD = 1;
    private final byte FPGA_CMD_PARA = 0x01;
    private final byte FPGA_CMD_TRANSLATE = 0x01;
    private final byte FPGA_CMD_ROUTE = 0x02;
    private final byte FPGA_CMD_LINK = 0x03;
    private final byte FPGA_CMD_GetIP = 0x04;

    private final byte FPGA_CMD_PARA_READ = 0x05;
    private final byte FPGA_CMD_ROUTE_READ = 0x06;
    private final byte FPGA_CMD_LINK_READ = 0x07;

    private final byte FPGA_CMD_SETGAMMA_RED = 14;
    private final byte FPGA_CMD_SETGAMMA_GREEN = 15;
    private final byte FPGA_CMD_SETGAMMA_BLUE = 16;

    private final byte FPGA_CMD_GETGAMMA_RED = 17;
    private final byte FPGA_CMD_GETGAMMA_GREEN = 18;
    private final byte FPGA_CMD_GETGAMMA_BLUE = 19;
    private final byte FPGA_CMD_SETBRIGHT = 20;
    private final byte FPGA_CMD_GETBRIGHT = 21;

    private final byte FPGA_CMD_SETSCHEBRIGHT = 22;//设置定时亮度调节
    private final byte FPGA_CMD_GETSCHEBRIGHT = 23;//获取定时亮度调节

    private final byte FPGA_CMD_TIMEADJUST = 24;//rtc时钟校时

    private final byte FPGA_CMD_WRITESPI = 28;//fpga将所有参数写入spi flash中保存
    private final byte FPGA_CMD_OPENSRAMMODE = 29;    //打开sram模式,以便接收参数


    private final byte FPGA_CMD_LINKWH = 33;//下发级联屏宽高命令,直接转发
    private final byte CMD_SET_PROGRAM_STORE = 34;//设置节目存储的位置(Nandflash还是SD卡)
    private final byte FPGA_CMD_LINKPOSADJ = 35;  //下发级联位置补偿信息//施源
    private final byte FPGA_CMD_LINKWHADJ = 36;//下发级联屏宽高补偿命令

    private final byte FPGA_CMD_GETVERSION = 37;    //获取接收/发送卡的版本信息
    private final byte FPGA_CMD_UPDATE_FPGA = 77;  //接收卡/发送卡程序升级
    private final byte FPGA_CMD_GET_FPGA_STATE = 80;  //读取升级状态

    private final byte ST_CMD_SCREENPARA = 204 - 256;
    private final byte ST_CMD_SETAPPPARA = 201 - 256;
    private final byte ST_CMD_GETAPPPARA = 203 - 256;
    private final byte ST_CMD_SETAPPDATA = 200 - 256;
    private final byte ST_CMD_GETAPPDATA = 202 - 256;
    private final byte ST_CMD_SETBRIGHT = 199 - 256;
    private final byte ST_CMD_GETBRIGHT = 198 - 256;

    private final byte RECIEVE_CARD_COUNT = 197 - 256;  //接收卡数量
    byte g_LastGammaRed = 32, g_LastGammaGreen = 32, g_LastGammaBlue = 32;
    int g_LastBright = 255;
    //新增手机获取信息
    private int conn = 0;
    private final byte UPDATE_FPGA = 78;  //接收卡/发送卡程序升级
    private final byte GET_FPGA_VERSION = 79;  //获取FPGA版本
    MulticastSocket ms = null;
    DatagramPacket dp;

    protected SerialPort mSerialPort;        //serial handle
    protected FileOutputStream mOutputStream;
    public FileInputStream mInputStream;


    public FileOutputStream fd_OutputStream;
    public FileOutputStream fd_ip;
    public FileInputStream fd_InputStream;
    public File fl;

    String respondString = "";
    byte[] btlen = new byte[4];
    byte[] Cmd = new byte[1];
    int len = 0, overtime_read = 0;

    private static String flashfilePath = "sdcard/flashdata.txt";
    private static String spifilePath = "sdcard/solidify.txt";
    private String txt_flash = "";//缓存数据的String
    private String[] para;
    private String Version = "X3M";

    /**
     * 写文件
     */
    public boolean writeTxtFile() {
        FileWriter writer = null;
        try {
            // 打开一个写文件器，构造函数中的第二个参数true表示以追加形式写文件,false表示覆盖的方式写入
            writer = new FileWriter(new File(flashfilePath), false);
            writer.write(txt_flash);
        } catch (IOException e) {
            e.printStackTrace();
            return true;
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
                return true;
            }
        }
        return true;
    }

    /**
     * 读文件
     */
    public static String readTxtFile(String path) {
        String read;
        FileReader fileread;
        StringBuffer readStr = new StringBuffer();
        try {
            fileread = new FileReader(new File(path));
            BufferedReader bufread = new BufferedReader(fileread);
            try {
                while ((read = bufread.readLine()) != null) {
                    readStr.append(read);
                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
//        System.out.println("读取固化文件内容是 :" + "/r/n" + readStr.toString());
        return readStr.toString();
    }


    public ServerUdp() {
        this.Version = MyApplication.VERSION;
        if (Version.equals("X3")) {
            txt_flash = readTxtFile(flashfilePath);//读取缓存的数据内容
            para = txt_flash.split(",");
            try {
                CARD = Integer.parseInt(para[13]);
            } catch (Exception e) {
                CARD = 1;
            }
            Log.i("x3config", "CARD: " + CARD);
        }
    }

    /**
     * 将固化到本地的数据发送给FPGA
     */
    private void sendSolidify() {
        // TODO Auto-generated method stub

        for (int i = 0; i < para.length - 1; i++) {

            if (null == para[i] || para[i].equals("") || para[i].equals("-")) {
            } else {
                byte[] b = para[i].getBytes();
                Log.v("X3config", "parserTxtDate: " + para[i]);
                try {
                    mOutputStream.write(b);
                    Thread.sleep(100);
                    Log.v("X3config: ", "自动发送  主卡 buff_receive: 共1包，第1包----发送成功");
                } catch (Exception e) {
                    Log.v("X3config: ", "自动发送  主卡 buff_receive: 共1包，第1包----发送失败");
                    // TODO: handle exception
                    e.printStackTrace();
                }
                //如果存在副卡，发送两次
                if (null != para[para.length - 1] && !para[para.length - 1].equals("1")) {
                    /**V9命令转X3*/
                    List<byte[]> datalist = parserTxtDate(para[i]);
                    for (int j = 0; j < datalist.size(); j++) {
                        try {
                            mOutputStream.write(datalist.get(j));
                            Thread.sleep(50);
                        } catch (Exception e) {
                            // TODO: handle exception
                            e.printStackTrace();
                        }
                        byte[] buff_receive = new byte[10];
                        len = 1;
                        try {
                            if (mInputStream.available() > 0)
                                len = mInputStream.read(buff_receive);
                        } catch (Exception e) {
                            // TODO: handle exception
                            e.printStackTrace();
                        }
                        String rec = "";
                        try {
                            rec = new String(buff_receive, "ISO-8859-1");
                            Log.v("X3config: ", "自动发送 buff_receive:—" + rec);
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                        if (buff_receive != new byte[10]) {
                            Log.v("X3config: ", "自动发送  副卡 buff_receive: 共" + datalist.size() + "包，" + "第" + (j + 1) + "包----发送成功");
                        } else {//丢包返回
                            Log.v("X3config: ", "自动发送  副卡 buff_receive: 共" + datalist.size() + "包，" + "第" + (j + 1) + "包----发送失败，丢包返回");
                        }
                    }
                }
            }


        }
    }

    public List<byte[]> parserTxtDate(String str) {//*#WL 0400 0100 01fe03010200010...1e1f#*

        byte[] value = str.getBytes();
        byte[] data = new byte[value.length - 21];
        if (value[15] == '3' && value[16] == 'c' && value[17] == 'c' && value[18] == '3') {//级联端口
            System.arraycopy(value, 19, data, 0, CARD * 16);
        } else {
            System.arraycopy(value, 19, data, 0, data.length);
        }
        List<byte[]> datalist = new ArrayList<byte[]>();
        byte[] head = new byte[]{0x2a, 0x23, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, (byte) 0xd5,//同步
                (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,//目的地址
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00,//源发地址
                0x00,//令牌
                0x00,//控制字
                (byte) 0x69,//包类型
                (byte) 0xff,//目标卡ID
                0x00, 0x03, 0x00//数据段长度
        };

        //尾
        byte[] tail = new byte[]{(byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0x5a};
        //地址，内容
        byte[] addr = new byte[2];
        if (value[15] == '0' && value[16] == 'D' && value[17] == 'F' && value[18] == '2') {//亮度
            addr = new byte[]{0x0d, (byte) 0xf2};
        }
        if (value[15] == '0' && value[16] == '1' && value[17] == 'f' && value[18] == 'e') {//屏参
            addr = new byte[]{0x01, (byte) 0xfe};
        }
        if (value[15] == '4' && value[16] == 'b' && value[17] == 'b' && value[18] == '4') {//预留
            addr = new byte[]{0x4b, (byte) 0xb4};
        }
        if (value[15] == '0' && value[16] == '5' && value[17] == 'f' && value[18] == 'a') {//伽马红
            addr = new byte[]{0x05, (byte) 0xfa};
        }
        if (value[15] == '0' && value[16] == '6' && value[17] == 'f' && value[18] == '9') {//伽马绿
            addr = new byte[]{0x06, (byte) 0xf9};
        }
        if (value[15] == '0' && value[16] == '7' && value[17] == 'f' && value[18] == '8') {//伽马蓝
            addr = new byte[]{0x07, (byte) 0xf8};
        }
        if (value[15] == '0' && value[16] == '2' && value[17] == 'f' && value[18] == 'd') {//走线
            addr = new byte[]{0x02, (byte) 0xfd};
        }
        if (value[15] == '1' && value[16] == 'd' && value[17] == 'e' && value[18] == '2') {//行序
            addr = new byte[]{0x1d, (byte) 0xe2};
        }
        if (value[15] == '0' && value[16] == '2' && value[17] == 'f' && value[18] == 'c') {//常规芯片寄存器
            addr = new byte[]{0x02, (byte) 0xfc};
        }
        if (value[15] == '0' && value[16] == '8' && value[17] == 'c' && value[18] == '7') {//Bitscan表
            addr = new byte[]{0x08, (byte) 0xc7};
        }
        if (value[15] == '0' && value[16] == '9' && value[17] == 'c' && value[18] == '6') {//Bitoewide表
            addr = new byte[]{0x09, (byte) 0xc6};
        }

        if (value[15] == '0' && value[16] == '1' && value[17] == 'f' && value[18] == 'd') {//高刷芯片刷新算法数据表
            addr = new byte[]{0x01, (byte) 0xfd};
        }
        if (value[15] == '0' && value[16] == 'b' && value[17] == 'f' && value[18] == '4') {//0bf4
            addr = new byte[]{0x0b, (byte) 0xf4};
        }
        if (value[15] == '0' && value[16] == 'a' && value[17] == 'f' && value[18] == '5') {   //0af5
            addr = new byte[]{0x0a, (byte) 0xf5};
        }

        if (value[15] == '4' && value[16] == 'b' && value[17] == 'b' && value[18] == '4') {//预留
            addr = new byte[]{0x4b, (byte) 0xb4};
        }

        if (value[15] == '3' && value[16] == 'c' && value[17] == 'c' && value[18] == '3') {//级联端口
            byte[] result = hexStringToByteArray(new String(data, 0, data.length));
            addr = new byte[]{0x3c, (byte) 0xc3};
            for (int i = 0; i < CARD; i++) {
                byte[] temp = new byte[768];
                temp[0] = 0x00;
                temp[1] = (byte) (i & 0xff);
                System.arraycopy(result, i * 8, temp, 2, 8);
                byte[] arr = addBytes(addBytes(addBytes(head, addr), temp), tail);
                if (i != 0)
                    datalist.add(arr);
            }
        } else {
            byte[] result = hexStringToByteArray(new String(data));
            int count = (result.length) % 768 == 0 ? (result.length) / 768 : (result.length) / 768 + 1;
            for (int i = 0; i < count; i++) {
                byte[] temp = new byte[768];
                System.arraycopy(result, i * 768, temp, 0, (i == count - 1) ? (result.length % 768) : 768);
                byte[] arr = addBytes(addBytes(addBytes(head, addr), temp), tail);
                datalist.add(arr);
            }
        }

        return datalist;

    }

    public List<byte[]> parserOriginalDate(byte[] value) {
        int len = bytes2Int(value, 3, 4);
        byte[] data = new byte[CARD * 16];
        if (value[22] == '3' && value[23] == 'c' && value[24] == 'c' && value[25] == '3') {//级联端口
            System.arraycopy(value, 26, data, 0, CARD * 16);
        } else {
            data = new byte[len - 21];
            System.arraycopy(value, 26, data, 0, len - 21);
        }
        List<byte[]> datalist = new ArrayList<byte[]>();
        byte[] head = new byte[]{0x2a, 0x23, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, 0x55, (byte) 0xd5,//同步
                (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,//目的地址
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00,//源发地址
                0x00,//令牌
                0x00,//控制字
                (byte) 0x69,//包类型
                (byte) 0xff,//目标卡ID
                0x00, 0x03, 0x00//数据段长度
        };

        //尾
        byte[] tail = new byte[]{(byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0x5a};
        //地址，内容
        byte[] addr = new byte[2];
        if (value[22] == '0' && value[23] == 'D' && value[24] == 'F' && value[25] == '2') {//亮度
            addr = new byte[]{0x0d, (byte) 0xf2};
        }
        if (value[22] == '0' && value[23] == '1' && value[24] == 'f' && value[25] == 'e') {//屏参
            addr = new byte[]{0x01, (byte) 0xfe};
        }
        if (value[22] == '4' && value[23] == 'b' && value[24] == 'b' && value[25] == '4') {//预留
            addr = new byte[]{0x4b, (byte) 0xb4};
        }
        if (value[22] == '0' && value[23] == '5' && value[24] == 'f' && value[25] == 'a') {//伽马红
            addr = new byte[]{0x05, (byte) 0xfa};
        }
        if (value[22] == '0' && value[23] == '6' && value[24] == 'f' && value[25] == '9') {//伽马绿
            addr = new byte[]{0x06, (byte) 0xf9};
        }
        if (value[22] == '0' && value[23] == '7' && value[24] == 'f' && value[25] == '8') {//伽马蓝
            addr = new byte[]{0x07, (byte) 0xf8};
        }
        if (value[22] == '0' && value[23] == '2' && value[24] == 'f' && value[25] == 'd') {//走线
            addr = new byte[]{0x02, (byte) 0xfd};
        }
        if (value[22] == '1' && value[23] == 'd' && value[24] == 'e' && value[25] == '2') {//行序
            addr = new byte[]{0x1d, (byte) 0xe2};
        }
        if (value[22] == '0' && value[23] == '2' && value[24] == 'f' && value[25] == 'c') {//常规芯片寄存器
            addr = new byte[]{0x02, (byte) 0xfc};
        }
        if (value[22] == '0' && value[23] == '8' && value[24] == 'c' && value[25] == '7') {//Bitscan表
            addr = new byte[]{0x08, (byte) 0xc7};
        }
        if (value[22] == '0' && value[23] == '9' && value[24] == 'c' && value[25] == '6') {//Bitoewide表
            addr = new byte[]{0x09, (byte) 0xc6};
        }

        if (value[22] == '0' && value[23] == '1' && value[24] == 'f' && value[25] == 'd') {//高刷芯片刷新算法数据表
            addr = new byte[]{0x01, (byte) 0xfd};
        }
        if (value[22] == '0' && value[23] == 'b' && value[24] == 'f' && value[25] == '4') {//0bf4
            addr = new byte[]{0x0b, (byte) 0xf4};
        }
        if (value[22] == '0' && value[23] == 'a' && value[24] == 'f' && value[25] == '5') {   //0af5
            addr = new byte[]{0x0a, (byte) 0xf5};
        }

        if (value[22] == '4' && value[23] == 'b' && value[24] == 'b' && value[25] == '4') {//预留
            addr = new byte[]{0x4b, (byte) 0xb4};
        }

        if (value[22] == '3' && value[23] == 'c' && value[24] == 'c' && value[25] == '3') {//级联端口
            byte[] result = hexStringToByteArray(new String(data, 0, data.length));
            addr = new byte[]{0x3c, (byte) 0xc3};
            for (int i = 0; i < CARD; i++) {
                byte[] temp = new byte[768];
                temp[0] = 0x00;
                temp[1] = (byte) (i & 0xff);
                System.arraycopy(result, i * 8, temp, 2, 8);
                byte[] arr = addBytes(addBytes(addBytes(head, addr), temp), tail);
                if (i != 0)
                    datalist.add(arr);
            }
        } else {
            byte[] result = hexStringToByteArray(new String(data));
            int count = (result.length) % 768 == 0 ? (result.length) / 768 : (result.length) / 768 + 1;
            for (int i = 0; i < count; i++) {
                byte[] temp = new byte[768];
                System.arraycopy(result, i * 768, temp, 0, (i == count - 1) ? (result.length % 768) : 768);
                byte[] arr = addBytes(addBytes(addBytes(head, addr), temp), tail);
                datalist.add(arr);
            }
        }

        return datalist;

    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] b = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            // 两位一组，表示一个字节,把这样表示的16进制字符串，还原成一个字节
            b[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }
        return b;
    }

    //合并
    public static byte[] addBytes(byte[] data1, byte[] data2) {
        byte[] data3 = new byte[data1.length + data2.length];
        System.arraycopy(data1, 0, data3, 0, data1.length);
        System.arraycopy(data2, 0, data3, data1.length, data2.length);
        return data3;

    }

    /**
     * 发送亮度命令文件
     */
    private String brightfile = "sdcard/bright/0.txt";

    private boolean copyAssetsToDst(Context context, String srcPath, String dstPath) {
        try {
            String fileNames[] = context.getAssets().list(srcPath);
            if (fileNames.length > 0) {
                File file = new File(Environment.getExternalStorageDirectory(), dstPath);
                if (!file.exists()) file.mkdirs();
                for (String fileName : fileNames) {
                    if (!srcPath.equals("")) { // assets 文件夹下的目录
                        copyAssetsToDst(context, srcPath + File.separator + fileName, dstPath + File.separator + fileName);
                    } else { // assets 文件夹
                        copyAssetsToDst(context, fileName, dstPath + File.separator + fileName);
                    }
                }
            } else {
                File outFile = new File(Environment.getExternalStorageDirectory(), dstPath);
                InputStream is = context.getAssets().open(srcPath);
                FileOutputStream fos = new FileOutputStream(outFile);
                byte[] buffer = new byte[1024];
                int byteCount;
                while ((byteCount = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, byteCount);
                }
                fos.flush();
                is.close();
                fos.close();
            }
            return true;
        } catch (Exception e) {
//            e.printStackTrace();
            return false;
        }
    }


    public static int byteToInt(byte b) {
        return b & 0xFF;
    }

    int datalen = 0;
    int g_packID = 0;
    byte g_xor = 0;
    byte[] data = null, respData = null;
    byte[] rBuffer = null;

    @Override
    public void run() {
        String information;
        try {
            mSerialPort = new SerialPort(new File("/dev/ttyAMA2"), 576000, 0);  //flag
        } catch (IOException e) {
            // TODO: handle exception
            e.printStackTrace();
            return;
        }
        data = new byte[8192];
        respData = new byte[1024];
        try {
            InetAddress groupAddress = InetAddress.getByName("224.0.0.1");
            ms = new MulticastSocket(5001);
            ms.joinGroup(groupAddress);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mOutputStream = (FileOutputStream) mSerialPort.getOutputStream();
        if (mOutputStream == null)
            return;
        mInputStream = (FileInputStream) mSerialPort.getInputStream();
        if (mInputStream == null)
            return;
        g_packID = 0;
        g_xor = 0;
        /**自动发送线程*/
        if (MyApplication.VERSION.equals("X3")) {
            /**自动发送线程*/
            new Thread() {
                public void run() {
                    while (true) {
                        sendSolidify();//将固化到本地的数据发送给FPGA
                        try {
                            sleep(15000);
                        } catch (InterruptedException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                }
            }.start();
        }
        /**初始化亮度指令*/
        if (Version.equals("X3T")) {
            copyAssetsToDst(MyApplication.context, "bright", "bright");
            String appbright = readTxtFile("sdcard/appbright.txt");
            if (null == appbright) appbright = "*#wl C800 0003 0DF2FF#*";
            if (null != appbright) {
                data = appbright.getBytes();
                String ss = new String(data, 19, 2);
                int filee = (Integer.parseInt(ss, 16) / 43);
                if (filee < 0) filee = 0;
                if (filee > 5) filee = 5;
                Log.i("x3config", "send brightlevel = " + filee);
                brightfile = "sdcard/bright/" + filee + ".txt";
                String filecontentt = readTxtFile(brightfile);
                if (null == filecontentt) {
                    //加载资源文件
                    Log.e("x3config", "copyAssetsToDst 失败，直接获取资源文件 ");
                    try {
                        InputStream is;
                        is = MyApplication.context.getAssets().open("bright/" + filee + ".txt");//获得AssetManger 对象, 调用其open 方法取得  对应的inputStream对象 
                        int size = is.available();//取得数据流的数据大小  
                        byte[] buffer = new byte[size];
                        is.read(buffer);
                        is.close();
                        filecontentt = new String(buffer, "GB2312");
                    } catch (Exception e) {
                        Log.e("Assert", e.toString());
                        filecontentt = null;
                    }
                }
                //解析文件
                if (null != filecontentt) {
                    String[] arr = filecontentt.split(",");
                    for (int i = 0; i < arr.length; i++) {
                        if (i != 0 && i != arr.length - 1) {
                            byte[] b = arr[i].getBytes();
                            try {

                                if (i == arr.length - 3) sleep(3000);
                                mOutputStream.write(b);
                                Thread.sleep(100);
//                            Log.e("x3config", "send brightfile success " + i);
                            } catch (Exception e) {
                                // TODO: handle exception
//                            e.printStackTrace();
                                Log.e("x3config", "send brightfile fail " + i);
                                return;
                            }
                        }
                        if (i == arr.length - 1) {
                            byte[] b = "*#wl C800 0003 0DF2FF#*".getBytes();
                            try {
                                mOutputStream.write(b);
                                Thread.sleep(100);
//                            Log.e("x3config", "send bright ff success " + i);
                            } catch (Exception e) {
                                // TODO: handle exception
//                            e.printStackTrace();
                                Log.e("x3config", "send bright ff fail " + i);
                                return;
                            }
                        }
                    }

                    Log.i("x3config", "send all brightfile success ");
                }
            }
        } else {
            try {
                fl = new File("sdcard/appbright.txt");
                fd_InputStream = new FileInputStream(fl);
                len = fd_InputStream.available();

                byte[] b = new byte[len];
                int res = fd_InputStream.read(b);
                fd_InputStream.close();

                try {
                    mOutputStream.write(b, 0, res);
                    Thread.sleep(100);
                    Log.i("x3config", "appbright send success");
                } catch (Exception e) {
                    // TODO: handle exception
                    Log.i("x3config", "appbright send fail");
                    e.printStackTrace();
                }
            } catch (Exception e) {
                // TODO: handle exception
                Log.e("x3config", "appbright except");
                e.printStackTrace();
            }
        }

        /**接收外界UDP数据*/
        while (true) {
            try {
                dp = new DatagramPacket(data, data.length);
                if (ms != null) {
                    ms.setSoTimeout(1000);
                    ms.receive(dp);
                }
            } catch (Exception e) {

            }
            if (conn != 0) Log.i("x3config", "FPGA正在升级中");
            if (dp.getAddress() != null && conn == 0) {
                final String quest_ip = dp.getAddress().toString();
                String host_ip = getLocalHostIp();
                /*若udp包的ip地址 是 本机的ip地址的话，丢掉这个包(不处理)*/
                if ((!host_ip.equals("")) && host_ip.equals(quest_ip.substring(1))) continue;
                final String codeString = new String(data, 0, dp.getLength());
                Log.i("X3config", host_ip + " X3命令字: " + data[2] + " content: " + codeString);
                rBuffer = new byte[1024];
                if ((data[0] == '*') && ('#' == data[1])) {

                    datalen = bytes2Int(data, 3, 4);

                    if (Version.equals("X3") && data.length > 26) {
                        if (data[22] == '0' && data[23] == 'D' && data[24] == 'F' && data[25] == '2') {//亮度
                            para[0] = new String(data, 7, 23);
                        }
                        if (data[22] == '0' && data[23] == '1' && data[24] == 'f' && data[25] == 'e') {//屏参
                            para[1] = new String(data, 7, datalen);
                        }
                        if (data[22] == '4' && data[23] == 'b' && data[24] == 'b' && data[25] == '4') {//预留
                            para[2] = new String(data, 7, datalen);
                        }
                        if (data[22] == '0' && data[23] == '5' && data[24] == 'f' && data[25] == 'a') {//伽马红
                            para[3] = new String(data, 7, datalen);
                        }
                        if (data[22] == '0' && data[23] == '6' && data[24] == 'f' && data[25] == '9') {//伽马绿
                            para[4] = new String(data, 7, datalen);
                        }
                        if (data[22] == '0' && data[23] == '7' && data[24] == 'f' && data[25] == '8') {//伽马蓝
                            para[5] = new String(data, 7, datalen);
                        }
                        if (data[22] == '0' && data[23] == '2' && data[24] == 'f' && data[25] == 'd') {//走线
                            para[6] = new String(data, 7, datalen);
                        }
                        if (data[22] == '1' && data[23] == 'd' && data[24] == 'e' && data[25] == '2') {//行序
                            para[7] = new String(data, 7, datalen);
                        }
                        if (data[22] == '0' && data[23] == '2' && data[24] == 'f' && data[25] == 'c') {//常规芯片寄存器
                            para[8] = new String(data, 7, datalen);
                        }
                        if (data[22] == '0' && data[23] == '8' && data[24] == 'c' && data[25] == '7') {//Bitscan表
                            para[9] = new String(data, 7, datalen);
                        }
                        if (data[22] == '0' && data[23] == '9' && data[24] == 'c' && data[25] == '6') {//Bitoewide表
                            para[10] = new String(data, 7, datalen);
                        }
                        if (data[22] == '3' && data[23] == 'c' && data[24] == 'c' && data[25] == '3') {//级联端口
                            para[11] = new String(data, 7, datalen);
                        }
                        if (data[22] == '0' && data[23] == '1' && data[24] == 'f' && data[25] == 'd') {//高刷芯片刷新算法数据表
                            para[12] = new String(data, 7, datalen);
                        }
                        if (data[2] == RECIEVE_CARD_COUNT) {//几张卡
                            CARD = bytes2Int(data, 7, 4);
                            para[13] = CARD + "";
                            Log.i("X3config", "197命令 :" + CARD + "张接收卡");
                        }
                        StringBuffer buffer = new StringBuffer();
                        for (int i = 0; i < para.length; i++) {
                            buffer.append(para[i]);
                            if (i != para.length - 1)
                                buffer.append(",");
                        }
                        txt_flash = buffer.toString();
                        writeTxtFile();//保存数据到本地缓存文件flashdata.txt中
                        if (data[22] == '0' && data[23] == 'a' && data[24] == 'f' && data[25] == '5')//关闭SPI,固化，复制缓存文件到固化文件
                        {
                            File file = new File("sdcard/solidify.txt");//SPI
                            File filecrash = new File("sdcard/flashdata.txt");//FLASH
                            FileUtils.copyFile(filecrash, file);
                        }
                    } else if (Version.equals("X3T") && data.length > 26) {

                        if (data[22] == '0' && data[23] == 'D' && data[24] == 'F' && data[25] == '2') {//亮度
                            String s = "FF";
                            try {
                                s = new String(data, 26, 2);
                            } catch (StringIndexOutOfBoundsException e) {
                                s = "FF";
                            }
                            int file = (Integer.parseInt(s, 16) / 43);
                            Log.i("x3config", "send brightlevel =  " + file);
                            brightfile = "sdcard/bright/" + file + ".txt";
                            String filecontent = readTxtFile(brightfile);
                            if (null == filecontent) {
                                try {
                                    InputStream is;
                                    is = MyApplication.context.getAssets().open("bright/" + file + ".txt");//获得AssetManger 对象, 调用其open 方法取得  对应的inputStream对象 
                                    int size = is.available();//取得数据流的数据大小  
                                    byte[] buffer = new byte[size];
                                    is.read(buffer);
                                    is.close();
                                    filecontent = new String(buffer, "GB2312");
                                } catch (Exception e) {
                                    Log.e("Assert", e.toString());
                                    filecontent = null;
                                }
                            }
                            if (null != filecontent) {
                                String resString = "*#00000#*";
                                try {
                                    respData = resString.getBytes("ISO-8859-1");
                                    udpSend(respData);
                                    fl = new File("sdcard/appbright.txt");
                                    fd_OutputStream = new FileOutputStream(fl);
                                    fd_OutputStream.write(data, 7, datalen);
                                    fd_OutputStream.close();
                                } catch (Exception e) {
                                }
                                String[] arr = filecontent.split(",");
                                for (int i = 0; i < arr.length; i++) {
                                    if (i != 0 && i != arr.length - 1) {
                                        byte[] b = arr[i].getBytes();
                                        try {
                                            if (i == arr.length - 3) sleep(3000);
                                            mOutputStream.write(b);
                                            Thread.sleep(100);
                                        } catch (Exception e) {
                                            Log.e("x3config", "send brightfile fail " + i);
                                            return;
                                        }
                                    }
                                    if (i == arr.length - 1) {
                                        byte[] b = "*#wl C800 0003 0DF2FF#*".getBytes();
                                        try {
                                            mOutputStream.write(b);
                                            Thread.sleep(100);
                                        } catch (Exception e) {
                                            Log.e("x3config", "send bright ff fail " + i);
                                            return;
                                        }
                                    }
                                }
                                Log.i("x3config", "send all brightfile success ");
                            }
                            data = new byte[8192];

                        }
                    }


                    switch (data[2]) {
                        case ST_CMD_SCREENPARA:
                            try {
                                int w = byteToInt(data[7]) + (byteToInt(data[8]) << 8) + (byteToInt(data[9]) << 16) + (byteToInt(data[10]) << 24) + (byteToInt(data[11]) << 32) + (byteToInt(data[12]) << 40) + (byteToInt(data[13]) << 48) + (byteToInt(data[14]) << 56);
                                final String width = Integer.toString(w);
                                int h = byteToInt(data[15]) + (byteToInt(data[16]) << 8) + (byteToInt(data[17]) << 16) + (byteToInt(data[18]) << 24) + (byteToInt(data[19]) << 32) + (byteToInt(data[20]) << 40) + (byteToInt(data[21]) << 48) + (byteToInt(data[22]) << 56);
                                final String height = Integer.toString(h);
                                information = "<root>" + "<width>" + width + "</width>" + "<height>" + height + "</height>" + "</root>";
                                fl = new File("sdcard/screenpara.txt");
                                fd_OutputStream = new FileOutputStream(fl);
                                fd_OutputStream.write(information.getBytes());
                                fd_OutputStream.close();
                                String resString = "*#00000#*";
                                try {
                                    respData = resString.getBytes("ISO-8859-1");
                                } catch (Exception e) {
                                    // TODO: handle exception
                                }
                                udpSend(respData);
                            } catch (FileNotFoundException e) {
                                e.printStackTrace();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            break;
                        case ST_CMD_SETBRIGHT: {
                            if (Version.equals("X3") || Version.contains("Q5")) {
                                try {
                                    mOutputStream.write(data, 7, datalen);
                                    Thread.sleep(100);
                                } catch (IOException e) {
                                    // TODO: handle exception
                                    e.printStackTrace();
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                String resString = "*#00000#*";
                                try {
                                    respData = resString.getBytes("ISO-8859-1");
                                } catch (Exception e) {
                                    // TODO: handle exception
                                }
                                udpSend(respData);
                                fl = new File("sdcard/appbright.txt");
                                try {
                                    fd_OutputStream = new FileOutputStream(fl);
                                    fd_OutputStream.write(data, 7, datalen);
                                    fd_OutputStream.close();
                                } catch (Exception e) {
                                    // TODO: handle exception
                                    e.printStackTrace();
                                }
                            } else {

                            }

                        }
                        break;
                        case ST_CMD_GETBRIGHT: {
                            Cmd[0] = ST_CMD_GETAPPPARA;
                            int len = 0;
                            String respondString = "*#";
                            String strCmd = new String(Cmd);
                            String strlength = Integer.toString(len);

                            respondString += strCmd;        //cmd
                            respondString += strlength;

                            String bright_data = "";
                            try {
                                fl = new File("sdcard/appbright.txt");
                                fd_InputStream = new FileInputStream(fl);
                                len = fd_InputStream.available();

                                byte[] b = new byte[len];
                                int res = fd_InputStream.read(b);
                                fd_InputStream.close();
                                bright_data = new String(b);
                            } catch (Exception e) {
                                // TODO: handle exception
                                e.printStackTrace();
                            }
                            respondString += bright_data;
                            respondString += "#*";


                            try {
                                respData = respondString.getBytes("ISO-8859-1");
                            } catch (Exception e) {
                                // TODO: handle exception
                            }
                            udpSend(respData);

                        }
                        break;


                        case ST_CMD_SETAPPPARA:
                        case ST_CMD_SETAPPDATA:
                            try {


                                if (data[2] == ST_CMD_SETAPPPARA) {
                                    fl = new File("sdcard/apppara.txt");
                                } else {
                                    fl = new File("sdcard/appdata.txt");
                                }
                                fd_OutputStream = new FileOutputStream(fl);
                                int reclen = 0;

                                while (reclen < datalen) {
                                    ms.receive(dp);
                                    reclen += dp.getLength();
                                    fd_OutputStream.write(data, 0, dp.getLength());
                                }
                                fd_OutputStream.close();


                                String resString = "*#00000#*";
                                try {
                                    respData = resString.getBytes("ISO-8859-1");
                                } catch (Exception e) {
                                    // TODO: handle exception
                                }
                                udpSend(respData);

                            } catch (SocketTimeoutException ex) {

                                Log.i("timeout", "appdata...");

                            } catch (SocketException e1) {
                                Log.i("SocketExecption", "appdata..." + e1.getMessage());
                                e1.printStackTrace();
                            } catch (IOException e2) {
                                Log.e("IOException", "appdata..." + e2.getMessage());
                            }
                            break;
                        case ST_CMD_GETAPPPARA:
                        case ST_CMD_GETAPPDATA:
                            int cmd_id = 0;
                            if (data[2] == ST_CMD_GETAPPPARA) {
                                fl = new File("sdcard/apppara.txt");
                                Cmd[0] = ST_CMD_GETAPPPARA;
                                cmd_id = ST_CMD_GETAPPPARA + 256;
                            } else {
                                fl = new File("sdcard/appdata.txt");
                                Cmd[0] = ST_CMD_GETAPPDATA;
                                cmd_id = ST_CMD_GETAPPDATA + 256;
                            }

                            try {
                                fd_InputStream = new FileInputStream(fl);

                                int len = fd_InputStream.available();

                                String resString = "*#";
                                byte[] bytes = new byte[1];
                                bytes = intToDecimOneByte(cmd_id);
                                try {
                                    String s_b = new String(bytes, "ISO-8859-1");
                                    resString += s_b;
                                } catch (Exception e) {
                                    // TODO: handle exception
                                }


                                byte[] bbyte = new byte[4];
                                bbyte = int2Bytes(len, 4);
                                try {
                                    String s_len = new String(bbyte, "ISO-8859-1");
                                    resString += s_len;
                                } catch (Exception e) {
                                    // TODO: handle exception
                                }


                                try {
                                    respData = resString.getBytes("ISO-8859-1");
                                } catch (Exception e) {
                                    // TODO: handle exception
                                }
                                udpSend(respData);
                                int sendlen = len;
                                while (sendlen > 0) {
                                    if (sendlen > 1024) {
                                        byte[] b = new byte[1024];
                                        int res = fd_InputStream.read(b);
                                        sendlen -= res;
                                        udpSend(b);
                                    } else {
                                        byte[] b = new byte[sendlen];
                                        int res = fd_InputStream.read(b);
                                        sendlen -= res;
                                        udpSend(b);
                                    }
                                }


                            } catch (Exception e) {

                            }
                            break;
                        case FPGA_CMD_GetIP:
                            //int len=60 ;
                            len = 64;
                            len = 32;
                            respondString = "*#";
                            btlen[0] = (byte) (len & 0x0ff);
                            btlen[1] = (byte) ((len >> 8) & 0x0ff);
                            btlen[2] = (byte) ((len >> 16) & 0x0ff);
                            btlen[3] = (byte) ((len >> 24) & 0x0ff);
                            String strlength = "";
                            String strMac = "";
                            String strCmd = "";
                            try {
                                strMac = getMacUseJavaIntetface();
                                strlength = new String(btlen, "ISO-8859-1");    //ISO-8859-1
                                Cmd[0] = FPGA_CMD_GetIP;
                                strCmd = new String(Cmd);
                                respondString += strCmd;        //cmd
                                respondString += strlength;        //length
                                byte[] localIp = new byte[4];
                                byte[] mask = new byte[6];
                                byte[] gatewayIP = new byte[4];
                                int leng = 0;
                                respondString += str2binstr(host_ip, ".");
                                respondString += str2binstr("255.255.255.0", ".");  //mask
                                respondString += str2binstr("192.168.0.1", ".");      //g_dwGatewayIP
                                byte[] mac = new byte[6];
                                mac = getMacBytes(strMac);                        //6 byte mac address
                                String stringMac = new String(mac, "ISO-8859-1");
                                respondString += stringMac;
                                byte[] ver = new byte[4];
                                ver[0] = 1;
                                ver[1] = 0;
                                ver[2] = 0;
                                ver[3] = 6;
                                String stringVer = new String(ver, "ISO-8859-1");  //version[4]
                                respondString += stringVer;
                                byte[] DevType = new byte[10];            //byte DevType[10]
                                Arrays.fill(DevType, (byte) 0);
                                Version.getBytes(0, Version.length(), DevType, 0);
                                String stringType = new String(DevType, "ISO-8859-1");
                                respondString += stringType;
                                byte[] userID = new byte[16];
                                Arrays.fill(userID, (byte) 0);
                                "guest".getBytes(0, "guest".length(), userID, 0);
                                String stringUser = new String(userID, "ISO-8859-1");
                                respondString += stringUser;
                                byte[] BspID = new byte[16];
                                Arrays.fill(BspID, (byte) 0);
                                "android4.4".getBytes(0, "android4.4".length(), BspID, 0);
                                String stringBsp = new String(BspID, "ISO-8859-1");
                                respondString += stringBsp;
                                respondString += "#*";
                                respData = respondString.getBytes("ISO-8859-1");
                            } catch (Exception e) {
                                // TODO: handle exception
                            }
                            udpSend(respData);
                            break;
                        case RECIEVE_CARD_COUNT://接收卡数量
                        {
                            try {
                                CARD = bytes2Int(data, 7, 4);
                                Log.e("RECIEVE_CARD_COUNT: ", CARD + "-----197");
                            } catch (NumberFormatException e) {
                                CARD = 1;
                            }

                        }
                        break;
                        case FPGA_CMD_PARA:     //FPGA_CMD_TRANSLATE
                        {
                            if (Version.equals("X3")) {
                                boolean isok = false;
                                if (CARD != 1) {
                                    try {
                                        mOutputStream.write(data, 7, datalen);
                                        Thread.sleep(100);
                                    } catch (Exception e) {
                                        // TODO: handle exception
                                        e.printStackTrace();
                                    }
                                    isok = checkFpgaRespond();
//                                if (isok) Log.i("X3config: ", "主卡 buff_receive: 共" + 1 + "包，" + "第" + 1 + "包----发送成功");
//                                else Log.e("X3config: ", "主卡 buff_receive: 共" + 1 + "包，" + "第" + 1 + "包----发送失败，丢包返回");
                                    /**V9命令转X3*/
                                    List<byte[]> datalist = parserOriginalDate(data);
                                    for (int i = 0; i < datalist.size(); i++) {
                                        try {
                                            mOutputStream.write(datalist.get(i));
                                            Thread.sleep(50);

                                            byte[] buff_receive = new byte[10];
                                            len = 1;

                                            if (mInputStream.available() > 0)
                                                len = mInputStream.read(buff_receive);
                                            String rec = new String(buff_receive, "ISO-8859-1");
                                            Log.i("X3config: ", "buff_receive:—" + rec);
                                            if (buff_receive != new byte[10]) {
                                                Log.i("X3config: ", "副卡 buff_receive: 共" + datalist.size() + "包，" + "第" + (i + 1) + "包----发送成功");
                                            } else {//丢包返回
                                                Log.i("X3config: ", "副卡 buff_receive: 共" + datalist.size() + "包，" + "第" + (i + 1) + "包----发送失败，丢包返回");
                                                isok = false;
                                                return;
                                            }
                                        } catch (UnsupportedEncodingException e) {
                                            e.printStackTrace();
                                        } catch (InterruptedException e) {
                                            e.printStackTrace();
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                    Log.i("X3config: ", "buff_receive: 全部数据----发送成功");
                                } else {
                                    try {
                                        mOutputStream.write(data, 7, datalen);
                                        Thread.sleep(100);
                                    } catch (Exception e) {
                                        // TODO: handle exception
                                        e.printStackTrace();
                                    }
                                    isok = checkFpgaRespond();
                                }
                                String resString = "*#00000#*";
                                if (!isok) resString = "*#00001#*";
                                try {
                                    respData = resString.getBytes("ISO-8859-1");
                                } catch (Exception e) {
                                    // TODO: handle exception
                                }
                                udpSend(respData);
                            } else if (Version.equals("X3T")) {
                                String resString = "*#00000#*";
                                try {
                                    respData = resString.getBytes("ISO-8859-1");
                                } catch (Exception e) {
                                    // TODO: handle exception
                                }
                                udpSend(respData);
                            } else {
                                try {
                                    mOutputStream.write(data, 7, datalen);
                                    Thread.sleep(100);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                String resString = "*#00000#*";
                                boolean ret;
                                ret = checkFpgaRespond();
                                if (ret) {
                                    //send respond to client pc
                                    try {
                                        respData = resString.getBytes("ISO-8859-1");
                                    } catch (Exception e) {
                                        // TODO: handle exception
                                    }
                                    udpSend(respData);
                                    Log.i("x3config", "through send success");
                                } else {
                                    Log.e("x3config", "through send fail");
                                }
                            }
                        }
                        break;
                        case FPGA_CMD_PARA_READ:
                            break;
                        case FPGA_CMD_ROUTE_READ:
                            break;
                        case FPGA_CMD_LINK_READ:
                            break;
                        case FPGA_CMD_SETGAMMA_RED: {
                            int GammaLUT[] = new int[256];
                            int BrightVal = 65535;
                            char GammaRed;
                            GammaRed = (char) data[7];
                            BuildGammaTable(GammaLUT, GammaRed / 10 + 0.0F, BrightVal);
                            String resString = "*#WL FC00 0202 05fa";
                            for (int i = 0; i < 256; i++) resString += Int2Hex4string(i);
                            resString += "#*";
                            try {
                                respData = resString.getBytes("ISO-8859-1");
                                mOutputStream.write(respData, 0, resString.length());
                                Thread.sleep(50);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            boolean ret = false;
                            ret = checkFpgaRespond();
                            if (ret) {
                                resString = "*#00000#*";
                                try {
                                    respData = resString.getBytes("ISO-8859-1");
                                } catch (Exception e) {
                                    // TODO: handle exception
                                }
                                udpSend(respData);
                            }

                        }
                        break;

                        case FPGA_CMD_SETGAMMA_GREEN: {
                            int GammaLUT[] = new int[256];
                            int BrightVal = 65535;
                            char GammaRed;
                            GammaRed = (char) data[7];
                            BuildGammaTable(GammaLUT, GammaRed / 10 + 0.0F, BrightVal);
                            String resString = "*#WL FC00 0202 06f9";
                            for (int i = 0; i < 256; i++) resString += Int2Hex4string(i);
                            resString += "#*";
                            try {
                                respData = resString.getBytes("ISO-8859-1");
                                mOutputStream.write(respData, 0, resString.length());
                                Thread.sleep(50);
                            } catch (Exception e) {
                                // TODO: handle exception
                                e.printStackTrace();
                            }

                            boolean ret = false;
                            ret = checkFpgaRespond();
                            if (ret) {
                                resString = "*#00000#*";
                                try {
                                    respData = resString.getBytes("ISO-8859-1");
                                } catch (Exception e) {
                                    // TODO: handle exception
                                }
                                udpSend(respData);
                            }

                        }
                        break;
                        case FPGA_CMD_SETGAMMA_BLUE: {
                            int GammaLUT[] = new int[256];
                            int BrightVal = 65535;
                            char GammaRed;
                            GammaRed = (char) data[7];
                            BuildGammaTable(GammaLUT, GammaRed / 10 + 0.0F, BrightVal);
                            String resString = "*#WL FC00 0202 07f8";
                            for (int i = 0; i < 256; i++) resString += Int2Hex4string(i);
                            resString += "#*";
                            try {
                                respData = resString.getBytes("ISO-8859-1");
                                mOutputStream.write(respData, 0, resString.length());
                                Thread.sleep(50);
                            } catch (Exception e) {
                                // TODO: handle exception
                                e.printStackTrace();
                            }
                            boolean ret = false;
                            ret = checkFpgaRespond();
                            if (ret) {
                                resString = "*#00000#*";
                                try {
                                    respData = resString.getBytes("ISO-8859-1");
                                } catch (Exception e) {
                                    // TODO: handle exception
                                }
                                udpSend(respData);
                            }
                        }
                        break;
                        case FPGA_CMD_GETGAMMA_RED: {
                            len = 1;
                            respondString = "*#";
                            btlen[0] = (byte) (len & 0x0ff);
                            btlen[1] = (byte) ((len >> 8) & 0x0ff);
                            btlen[2] = (byte) ((len >> 16) & 0x0ff);
                            btlen[3] = (byte) ((len >> 24) & 0x0ff);
                            strlength = "";
                            try {
                                strlength = new String(btlen, "ISO-8859-1");    //jiang ISO-8859-1
                                Cmd[0] = FPGA_CMD_GETGAMMA_RED;
                                strCmd = new String(Cmd);
                                respondString += strCmd;        //cmd
                                respondString += strlength;    //length
                                Cmd[0] = g_LastGammaRed;
                                strCmd = new String(Cmd);
                                respondString += strCmd;        //data
                                respondString += "#*";
                                respData = respondString.getBytes("ISO-8859-1");
                                Thread.sleep(100);
                            } catch (Exception e) {
                                // TODO: handle exception
                            }
                            udpSend(respData);
                        }
                        break;
                        case FPGA_CMD_GETGAMMA_GREEN: {
                            len = 1;
                            respondString = "*#";
                            btlen[0] = (byte) (len & 0x0ff);
                            btlen[1] = (byte) ((len >> 8) & 0x0ff);
                            btlen[2] = (byte) ((len >> 16) & 0x0ff);
                            btlen[3] = (byte) ((len >> 24) & 0x0ff);
                            strlength = "";
                            try {
                                strlength = new String(btlen, "ISO-8859-1");
                                Cmd[0] = FPGA_CMD_GETGAMMA_GREEN;
                                strCmd = new String(Cmd);
                                respondString += strCmd;        //cmd
                                respondString += strlength;    //length
                                Cmd[0] = g_LastGammaGreen;
                                strCmd = new String(Cmd);
                                respondString += strCmd;        //data
                                respondString += "#*";
                                respData = respondString.getBytes("ISO-8859-1");
                                Thread.sleep(100);
                            } catch (Exception e) {
                                // TODO: handle exception
                                e.printStackTrace();
                            }
                            udpSend(respData);
                        }
                        break;
                        case FPGA_CMD_GETGAMMA_BLUE: {
                            len = 1;
                            respondString = "*#";
                            btlen[0] = (byte) (len & 0x0ff);
                            btlen[1] = (byte) ((len >> 8) & 0x0ff);
                            btlen[2] = (byte) ((len >> 16) & 0x0ff);
                            btlen[3] = (byte) ((len >> 24) & 0x0ff);
                            strlength = "";
                            try {
                                strlength = new String(btlen, "ISO-8859-1");

                                Cmd[0] = FPGA_CMD_GETGAMMA_BLUE;
                                strCmd = new String(Cmd);
                                respondString += strCmd;        //cmd
                                respondString += strlength;    //length
                                Cmd[0] = g_LastGammaBlue;
                                strCmd = new String(Cmd);
                                respondString += strCmd;        //data
                                respondString += "#*";
                                respData = respondString.getBytes("ISO-8859-1");
                                Thread.sleep(100);
                            } catch (Exception e) {
                                // TODO: handle exception
                                e.printStackTrace();
                            }
                            udpSend(respData);
                        }
                        break;
                        case FPGA_CMD_SETBRIGHT: {
                            respondString = "*#";
                            Cmd[0] = FPGA_CMD_SETBRIGHT;
                            strCmd = new String(Cmd);
                            respondString += strCmd;        //cmd
                            respondString += "0000#*";   //+ length +data +tail
                            try {
                                respData = respondString.getBytes("ISO-8859-1");
                                Thread.sleep(100);
                            } catch (Exception e) {
                                // TODO: handle exception
                                e.printStackTrace();
                            }
                            udpSend(respData);
                        }
                        break;
                        case FPGA_CMD_GETBRIGHT: {
                            len = 1;
                            respondString = "*#";
                            btlen[0] = (byte) (len & 0x0ff);
                            btlen[1] = (byte) ((len >> 8) & 0x0ff);
                            btlen[2] = (byte) ((len >> 16) & 0x0ff);
                            btlen[3] = (byte) ((len >> 24) & 0x0ff);
                            strlength = "";
                            try {
                                strlength = new String(btlen, "ISO-8859-1");
                                Cmd[0] = FPGA_CMD_GETBRIGHT;
                                strCmd = new String(Cmd);
                                respondString += strCmd;        //cmd
                                respondString += strlength;    //length
                                Cmd[0] = (byte) g_LastBright;
                                strCmd = new String(Cmd);
                                respondString += strCmd;        //data
                                respondString += "#*";
                                respData = respondString.getBytes("ISO-8859-1");
                                Thread.sleep(100);
                            } catch (Exception e) {
                                // TODO: handle exception
                                e.printStackTrace();
                            }
                            udpSend(respData);
                        }
                        break;
                        case FPGA_CMD_SETSCHEBRIGHT: {
                            respondString = "*#";
                            Cmd[0] = FPGA_CMD_SETSCHEBRIGHT;
                            strCmd = new String(Cmd);
                            respondString += strCmd;        //cmd
                            respondString += "0000#*";   //+ length +data +tail
                            try {
                                respData = respondString.getBytes("ISO-8859-1");
                                Thread.sleep(100);
                            } catch (Exception e) {
                                // TODO: handle exception
                                e.printStackTrace();
                            }
                            udpSend(respData);
                        }
                        break;
                        case FPGA_CMD_GETSCHEBRIGHT: {
                        }
                        break;
                        case FPGA_CMD_TIMEADJUST: {
                            Calendar calendar = Calendar.getInstance();
                            int year, month, day, hour, minute, second;
                            year = bytes2Int(data, 7, 4);
                            month = data[11];
                            day = data[12];
                            hour = data[13];
                            minute = data[14];
                            second = data[15];
                            SetDatetime(year, month, day, hour, minute, second);
                            try {
                                mOutputStream.write("FPGA_CMD_TIMEADJUST".getBytes());
                            } catch (Exception e) {
                                // TODO: handle exception
                                e.printStackTrace();
                            }
                        }
                        break;
                        case FPGA_CMD_WRITESPI: {
                            len = 0;
                            respondString = "*#";
                            btlen[0] = (byte) (len & 0x0ff);
                            btlen[1] = (byte) ((len >> 8) & 0x0ff);
                            btlen[2] = (byte) ((len >> 16) & 0x0ff);
                            btlen[3] = (byte) ((len >> 24) & 0x0ff);
                            strlength = "";
                            try {
                                strlength = new String(btlen, "ISO-8859-1");
                                Cmd[0] = FPGA_CMD_WRITESPI;
                                strCmd = new String(Cmd);
                                respondString += strCmd;        //cmd
                                respondString += strlength;    //length
                                //no data
                                respondString += "#*";
                                respData = respondString.getBytes("ISO-8859-1");
                            } catch (Exception e) {
                                // TODO: handle exception
                                e.printStackTrace();
                            }
                            udpSend(respData);
                            if (data[7] == 1) {
                                SetFpgaParaStoreToSpi(true);
                                try {
                                    Thread.sleep(2000);
                                } catch (Exception e) {
                                    // TODO: handle exception
                                }
                                SetFpgaParaStoreToSpi(false);
                            }
                            SetFpgaCtrModeSram(false);

                        }
                        break;
                        case FPGA_CMD_OPENSRAMMODE: {
                            len = 0;
                            respondString = "*#";
                            btlen[0] = (byte) (len & 0x0ff);
                            btlen[1] = (byte) ((len >> 8) & 0x0ff);
                            btlen[2] = (byte) ((len >> 16) & 0x0ff);
                            btlen[3] = (byte) ((len >> 24) & 0x0ff);
                            strlength = "";
                            try {
                                strlength = new String(btlen, "ISO-8859-1");
                                Cmd[0] = FPGA_CMD_OPENSRAMMODE;
                                strCmd = new String(Cmd);
                                respondString += strCmd;        //cmd
                                respondString += strlength;    //length
                                //no data
                                respondString += "#*";
                                respData = respondString.getBytes("ISO-8859-1");
                            } catch (Exception e) {
                                // TODO: handle exception
                                e.printStackTrace();
                            }
                            SetFpgaCtrModeSram(true);
                            udpSend(respData);
                        }
                        break;
                        case CMD_SET_PROGRAM_STORE: {
                        }
                        break;
                        case FPGA_CMD_LINKWH: {
                            len = 0;
                            respondString = "*#";
                            btlen[0] = (byte) (len & 0x0ff);
                            btlen[1] = (byte) ((len >> 8) & 0x0ff);
                            btlen[2] = (byte) ((len >> 16) & 0x0ff);
                            btlen[3] = (byte) ((len >> 24) & 0x0ff);
                            strlength = "";
                            try {
                                strlength = new String(btlen, "ISO-8859-1");
                                Cmd[0] = FPGA_CMD_LINKWH;
                                strCmd = new String(Cmd);
                                respondString += strCmd;        //cmd
                                respondString += strlength;    //length
                                respondString += "#*";
                                mOutputStream.write(data, 7, datalen);
                                Thread.sleep(50);
                            } catch (Exception e) {
                                // TODO: handle exception
                                e.printStackTrace();
                            }
                            String resString = "";
                            boolean ret = checkFpgaRespond();
                            if (ret) {
                                try {
                                    respData = resString.getBytes("ISO-8859-1");
                                } catch (Exception e) {
                                    // TODO: handle exception
                                }
                                udpSend(respData);
                            }
                        }
                        break;
                        case FPGA_CMD_LINKPOSADJ: {
                            len = 0;
                            respondString = "*#";
                            btlen[0] = (byte) (len & 0x0ff);
                            btlen[1] = (byte) ((len >> 8) & 0x0ff);
                            btlen[2] = (byte) ((len >> 16) & 0x0ff);
                            btlen[3] = (byte) ((len >> 24) & 0x0ff);

                            strlength = "";
                            try {
                                strlength = new String(btlen, "ISO-8859-1");
                                Cmd[0] = FPGA_CMD_LINKPOSADJ;
                                strCmd = new String(Cmd);
                                respondString += strCmd;        //cmd
                                respondString += strlength;    //length
                                respondString += "#*";
                                mOutputStream.write(data, 7, datalen);
                                Thread.sleep(5);//
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            String resString = "";
                            boolean ret = checkFpgaRespond();
                            if (ret) {
                                try {
                                    respData = resString.getBytes("ISO-8859-1");
                                } catch (Exception e) {
                                    // TODO: handle exception
                                }
                                udpSend(respData);
                            }
                        }
                        break;
                        case FPGA_CMD_LINKWHADJ: {
                            len = 0;
                            respondString = "*#";
                            btlen[0] = (byte) (len & 0x0ff);
                            btlen[1] = (byte) ((len >> 8) & 0x0ff);
                            btlen[2] = (byte) ((len >> 16) & 0x0ff);
                            btlen[3] = (byte) ((len >> 24) & 0x0ff);
                            strlength = "";
                            try {
                                strlength = new String(btlen, "ISO-8859-1");
                                Cmd[0] = FPGA_CMD_LINKWHADJ;
                                strCmd = new String(Cmd);
                                respondString += strCmd;        //cmd
                                respondString += strlength;    //length
                                respondString += "#*";
                                mOutputStream.write(data, 7, datalen);
                                Thread.sleep(5);
                            } catch (Exception e) {
                                // TODO: handle exception
                                e.printStackTrace();
                            }
                            String resString = "";
                            boolean ret = checkFpgaRespond();
                            if (ret) {
                                try {
                                    respData = resString.getBytes("ISO-8859-1");
                                    udpSend(respData);
                                    if (mInputStream.available() > 0)
                                        mInputStream.read(rBuffer);
                                } catch (Exception e) {
                                    // TODO: handle exception
                                    e.printStackTrace();
                                }
                            }
                        }
                        break;
                        case FPGA_CMD_GETVERSION: {

                            String last = "";
                            if (Version.contains("Q5")) {
                                len = 0;
                                respondString = "*#";
                                btlen[0] = (byte) (len & 0x0ff);
                                btlen[1] = (byte) ((len >> 8) & 0x0ff);
                                btlen[2] = (byte) ((len >> 16) & 0x0ff);
                                btlen[3] = (byte) ((len >> 24) & 0x0ff);

                                strlength = "";
                                try {
                                    strlength = new String(btlen, "ISO-8859-1");
                                } catch (Exception e) {
                                    // TODO: handle exception
                                }

                                Cmd[0] = FPGA_CMD_GETVERSION;
                                strCmd = new String(Cmd);
                                respondString += strCmd;        //cmd
                                respondString += strlength;    //length
                                //no data
                                respondString += "#*";


                                //1: *********** 读出 *****************
                                String resString = "*#RL 0002 0002#*";
                                try {
                                    respData = resString.getBytes("ISO-8859-1");
                                } catch (Exception e) {
                                    // TODO: handle exception
                                }
                                try {
                                    mOutputStream.write(respData);
                                    Thread.sleep(50);
                                } catch (Exception e) {
                                    // TODO: handle exception
                                    e.printStackTrace();
                                }
                                try {
                                    if (mInputStream.available() > 0)
                                        len = mInputStream.read(rBuffer);
                                } catch (Exception e) {
                                    // TODO: handle exception
                                    e.printStackTrace();
                                }

                                String string = "";
                                String fpver = "";
                                try {
                                    string = new String(rBuffer, "ISO-8859-1");
                                    //String[] stringsplit=string.split("&", 1) ;
                                } catch (Exception e) {
                                    // TODO: handle exception
                                }
                                int pos = string.indexOf("%*#");
                                if (pos < 0) {
                                    System.out.println("get version has not get respond,return");
                                    break;
                                }
                                for (int c = 0; c < 2; c++) {
                                    String ver1 = string.substring(pos + 3 + c * 2, pos + 3 + 2 * (c + 1));
                                    last = string.substring(pos + 3, pos + 3 + 4);
                                    byte[] bytes = new byte[1];
                                    if (!isNumeric(ver1)) return;
                                    int device = Integer.parseInt(ver1);
                                    bytes = intToDecimOneByte(device);

                                    try {
                                        ver1 = new String(bytes, "ISO-8859-1");
                                        fpver += ver1;
                                    } catch (Exception e) {

                                    }
                                }


                                len = 4;
                                respondString = "*#";
                                btlen[0] = (byte) (len & 0x0ff);
                                btlen[1] = (byte) ((len >> 8) & 0x0ff);
                                btlen[2] = (byte) ((len >> 16) & 0x0ff);
                                btlen[3] = (byte) ((len >> 24) & 0x0ff);

                                strlength = "";
                                try {
                                    strlength = new String(btlen, "ISO-8859-1");
                                } catch (Exception e) {
                                    // TODO: handle exception
                                }

                                Cmd[0] = FPGA_CMD_GETVERSION;
                                strCmd = new String(Cmd);
                                respondString += strCmd;            //cmd
                                respondString += strlength;        //length
                                respondString += fpver;
                                respondString += "#*";

                                try {
                                    respData = respondString.getBytes("ISO-8859-1");
                                    Log.e(TAG, "vFPGA: " + last);
                                } catch (Exception e) {
                                    // TODO: handle exception
                                    e.printStackTrace();
                                }
                                udpSend(respData);
                            } else {

                                len = 0;
                                respondString = "*#";
                                btlen[0] = (byte) (len & 0x0ff);
                                btlen[1] = (byte) ((len >> 8) & 0x0ff);
                                btlen[2] = (byte) ((len >> 16) & 0x0ff);
                                btlen[3] = (byte) ((len >> 24) & 0x0ff);
                                strlength = "";
                                String string = "";
                                String resString = "*#RL 000f 0080#*";
                                int pos = -1;
                                try {
                                    strlength = new String(btlen, "ISO-8859-1");
                                    Cmd[0] = FPGA_CMD_GETVERSION;
                                    strCmd = new String(Cmd);
                                    respondString += strCmd;        //cmd
                                    respondString += strlength;    //length
                                    respondString += "#*";
                                    mOutputStream.write(data, 7, datalen);
                                    Thread.sleep(5);
                                    respData = resString.getBytes("ISO-8859-1");
                                    mOutputStream.write(respData);
                                    Thread.sleep(50);
                                    if (mInputStream.available() > 0)
                                        len = mInputStream.read(rBuffer);
                                    string = new String(rBuffer, "ISO-8859-1");
                                    pos = string.indexOf("%*#");
                                    if (pos < 0) {
                                        System.out.println("get version has not get respond,return");
                                        break;
                                    }
                                    string = string.substring(pos + 3, pos + 3 + 2);
                                    int iDeviceNum = Integer.parseInt(string);
                                    resString = "*#RL 4000 ";
                                    resString += Int2Dec4string(iDeviceNum + iDeviceNum);
                                    resString += "#*";
                                    respData = resString.getBytes("ISO-8859-1");
                                    mOutputStream.write(respData);
                                    Thread.sleep(300);
                                    if (mInputStream.available() > 0)
                                        len = mInputStream.read(rBuffer);
                                    string = new String(rBuffer, "ISO-8859-1");
                                    pos = string.indexOf("%#RL#%*#");
                                    if (pos < 0) {
                                        System.out.println("no find fpga device");
                                        iDeviceNum = 0;
                                    }
                                    string = string.replace("%#RL#%*#", "");
                                    string = string.replace("#*", "");
                                    string = string.replace("\r\n", "");
                                    string = string.replace(" ", "");
                                    len = iDeviceNum * 2;
                                    respondString = "*#";
                                    btlen[0] = (byte) (len & 0x0ff);
                                    btlen[1] = (byte) ((len >> 8) & 0x0ff);
                                    btlen[2] = (byte) ((len >> 16) & 0x0ff);
                                    btlen[3] = (byte) ((len >> 24) & 0x0ff);
                                    strlength = new String(btlen, "ISO-8859-1");
                                    Cmd[0] = FPGA_CMD_GETVERSION;
                                    strCmd = new String(Cmd);
                                    respondString += strCmd;            //cmd
                                    respondString += strlength;        //length
                                    String[] strp = string.split("&");
                                    for (int n = 0; n < iDeviceNum * 2; n++)    //no data
                                    {
                                        int fpgaVer = 0;
                                        String st = "";
                                        if (strp[n].length() >= 2)
                                            st = strp[n].substring(0, 2);
                                        if (isNumeric(st)) {
                                            fpgaVer = Integer.parseInt(st, 10);
                                        } else {
                                            try {
                                                fpgaVer = Integer.parseInt(st, 10);
                                                fpgaVer += 0x80;
                                            } catch (Exception e) {
                                                // TODO: handle exception
                                            }
                                        }
                                        try {
                                            strlength = new String(btlen, "ISO-8859-1");
                                        } catch (Exception e) {
                                            // TODO: handle exception
                                        }
                                        String verStr = "";
                                        byte[] bytes = new byte[1];
                                        bytes = intToDecimOneByte(fpgaVer);
                                        try {
                                            verStr = new String(bytes, "ISO-8859-1");
                                        } catch (Exception e) {
                                            // TODO: handle exception
                                        }
                                        last += st;
                                        respondString += verStr;

                                    }
                                    Log.e(TAG, "respFPAGV: " + last);
                                    respondString += "#*";
                                    respData = respondString.getBytes("ISO-8859-1");
                                } catch (Exception e) {
                                    // TODO: handle exception
                                    e.printStackTrace();
                                }
                                udpSend(respData);
                            }
                        }
                        break;

                        case FPGA_CMD_UPDATE_FPGA:            //CMD_UPDATE_FPGA 77   FPGA
                            respondString = "*#";
                            respondString += Integer.toString(FPGA_CMD_UPDATE_FPGA);
                            respondString += "0000#*";
                            len = 0;
                            if (datalen == 27) {
                                if ((data[21 + 7] == '0') && (data[22 + 7] == '0') && (data[23 + 7] == '4') && (data[24 + 7] == '7')) {
                                    try {
                                        if (mInputStream.available() > 0)
                                            len = mInputStream.read(rBuffer);
                                        Thread.sleep(10);
                                        mOutputStream.write("*#WL C800 000a 0bf4000000#*".getBytes("ISO-8859-1"), 0, 27);
                                        Thread.sleep(10);
                                    } catch (Exception e) {
                                        // TODO: handle exception
                                        e.printStackTrace();
                                    }
                                } else if ((data[21 + 7] == '4') && (data[22 + 7] == '7') && (data[23 + 7] == '0') && (data[24 + 7] == '0')) {
                                    try {
                                        if (mInputStream.available() > 0)
                                            len = mInputStream.read(rBuffer);
                                        Thread.sleep(10);
                                        mOutputStream.write("*#WL C800 000a 0bf4000000#*".getBytes("ISO-8859-1"), 0, 27);
                                        Thread.sleep(10);
                                    } catch (Exception e) {
                                        // TODO: handle exception
                                        e.printStackTrace();
                                    }
                                } else if ((data[21 + 7] == '0') && (data[22 + 7] == '0') && (data[23 + 7] == '0') && (data[24 + 7] == '0')) {

                                }

                                // clear buff
                                try {
                                    if (mInputStream.available() > 0)
                                        len = mInputStream.read(rBuffer);
                                } catch (Exception e) {
                                    // TODO: handle exception
                                }
                            }
                            if (datalen >= 500) {
                                byte xor = 0;
                                int packSerials = 0;
                                byte a, b, c, d = 0;
                                a = data[531 + 7];
                                b = data[532 + 7];
                                c = data[533 + 7];
                                d = data[534 + 7];
                                packSerials = hexCharToInt(b) * 256 + hexCharToInt(c) * 16 + hexCharToInt(d);
//                                Log.i("get current pack=", Integer.toString(packSerials));
                                g_packID = packSerials;
                                data[537 + 7] = data[535 + 7];
                                data[538 + 7] = data[536 + 7];
                                xor = CheckXOR(data, 7);
                                int i_xr = Math.abs(xor);
                                if (xor < 0) i_xr = 256 - Math.abs(xor);
                                byte xorhi = 0, xorlo = 0;
                                xorhi = (byte) (i_xr / 16);
                                xorlo = (byte) (i_xr % 16);
                                if ((xorhi >= 0) && (xorhi <= 9))
                                    data[535 + 7] = (byte) (xorhi + '0');
                                else if ((xorhi >= 10) && (xorhi <= 15))
                                    data[535 + 7] = (byte) (xorhi - 10 + 'A');
                                else
                                    data[535 + 7] = '0';
                                if ((xorlo >= 0) && (xorlo <= 9))
                                    data[536 + 7] = (byte) (xorlo + '0');
                                else if ((xorlo >= 10) && (xorlo <= 15))
                                    data[536 + 7] = (byte) (xorlo - 10 + 'A');
                                else
                                    data[536 + 7] = '0';
                                data[13 + 7] = 'A';
                                datalen = datalen + 2;
                                g_xor = xor;
                            }
                            try {
                                mOutputStream.write(data, 7, datalen);
                                Thread.sleep(2);
                            } catch (Exception e) {
                                // TODO: handle exception
                                e.printStackTrace();
                            }
                            overtime_read = 0;
                            while (true) {
                                overtime_read++;
                                try {
                                    if (mInputStream.available() > 0)
                                        len = mInputStream.read(rBuffer);
                                    Thread.sleep(5);
                                } catch (Exception e) {
                                    // TODO: handle exception
                                }
                                if ((rBuffer[0] == '%') && (rBuffer[1] == '#') && (rBuffer[2] == 'W') && (rBuffer[3] == 'L') && (rBuffer[4] == '#') && (rBuffer[5] == '%'))
                                    break;
                                if ((rBuffer[0] == '%') && (rBuffer[1] == '#') && (rBuffer[2] == 'R') && (rBuffer[3] == 'L') && (rBuffer[4] == '#') && (rBuffer[5] == '%'))
                                    break;
                                if (overtime_read >= 100) break;
                            }
                            if (overtime_read >= 100) {
                                try {
                                    mOutputStream.write("*#WL C800 000a 0bf4000000#*".getBytes("ISO-8859-1"), 0, 27);
                                    Thread.sleep(10);
                                } catch (Exception e) {
                                    // TODO: handle exception
                                    e.printStackTrace();
                                }

                            }
                            try {
                                mOutputStream.write("*#RL 0038 0004#*".getBytes("ISO-8859-1"), 0, 16);
                                Thread.sleep(2);
                                if (mInputStream.available() > 0) len = mInputStream.read(rBuffer);
                                if (rBuffer[8 + 6] == '3' && rBuffer[9 + 6] == 'B') {
                                    System.out.println("(cmd=003B)");
                                } else {
                                    System.out.println("(cmd=003B)......");
                                    Thread.sleep(1000);
                                }
                                if (len > 0) {
                                    udpSend(respondString.getBytes("ISO-8859-1"));
                                } else {
                                    mOutputStream.write("*#WL C800 000a 0bf4000000#*".getBytes("ISO-8859-1"), 0, 27);
                                    Thread.sleep(10);
                                }
                            } catch (Exception e) {
                                // TODO: handle exception
                                e.printStackTrace();
                            }
                            break;
                        case FPGA_CMD_GET_FPGA_STATE:
                            respondString = "*#";
                            respondString += Integer.toString(FPGA_CMD_GET_FPGA_STATE);
                            respondString += "10001#*";
                            len = 1;
                            int card_num = 0;
                            card_num = GetCardNum();        //now return only 1
//                            System.out.println("recv cmd of CMD_GET_FPGA_STATE .......");
                            respondString = "*#";
                            btlen[0] = (byte) (card_num & 0x0ff);
                            btlen[1] = (byte) ((card_num >> 8) & 0x0ff);
                            btlen[2] = (byte) ((card_num >> 16) & 0x0ff);
                            btlen[3] = (byte) ((card_num >> 24) & 0x0ff);
                            String strlen = "";
                            try {
                                strlen = new String(btlen, "ISO-8859-1");
                                respondString += strlen;
                                for (int i = 0; i < card_num; i++) respondString += "1";
                                respondString += "#*";
                                respData = respondString.getBytes("ISO-8859-1");
                            } catch (Exception e) {
                                // TODO: handle exception
                            }
                            udpSend(respData);
                            break;
                        case UPDATE_FPGA: {
                            conn = 1;
                            Log.i("x3config", "收到升级指令");

                            Thread updataThread = new Thread()
                            {
                                @Override
                                public void run() {
                                    if (conn!=0)
                                    {
                                        byte[] updata = MyApplication.readFile(MyApplication.FPGApath, MyApplication.context);
                                        if (null == updata) {
                                            Log.i("x3config", "未找到升级文件");
                                            conn = 0;
                                            //发送广播升级失败
                                            Intent intent1 = new Intent("com.listen.action.fpga_update_status");
                                            intent1.putExtra("fpga", -1);
                                            MyApplication.context.sendBroadcast(intent1, null);
                                        }
                                        if (conn == 0) {
                                            //发送广播开始升级
                                            Intent intent = new Intent("com.listen.action.fpga_start_update");
                                            intent.putExtra("msg", "start");
                                            MyApplication.context.sendBroadcast(intent, null);
                                            this.interrupt();
                                        }
                                        byte[] temp = new byte[256];
                                        int count = (updata.length - 271) / 256;
                                        for (int i = -1; i < count; i++) {
                                            rBuffer = new byte[1024];
                                            byte[] order = MyApplication.hexStringToBytes(Int2Hex4string(i));
                                            if (i == -1) {
                                                try {
                                                    datalen = 27;
                                                    data = "*#M0000*#WL C800 000A 0bf4004700#*#*".getBytes("ISO-8859-1");//升级允许
                                                } catch (UnsupportedEncodingException e) {
                                                    e.printStackTrace();
                                                }
                                            } else {
                                                datalen = 537;
                                                System.arraycopy(updata, 271 + 256 * i, temp, 0, 256);
                                                StringBuffer bf = new StringBuffer("*#M0000*#WL C800 0208 2DD2");
                                                bf.append(MyApplication.bytesToHexString(temp));
                                                bf.append(MyApplication.bytesToHexString(order));
                                                bf.append("#*#*");
                                                try {
                                                    data = bf.toString().getBytes("ISO-8859-1");
                                                } catch (UnsupportedEncodingException e) {
                                                    e.printStackTrace();
                                                }
                                            }
                                            len = 0;
                                            //发送操作
                                            if (datalen == 27) {
                                                if ((data[21 + 7] == '0') && (data[22 + 7] == '0') && (data[23 + 7] == '4') && (data[24 + 7] == '7')) {
                                                    //clear input buff
                                                    try {
                                                        if (mInputStream.available() > 0)
                                                            len = mInputStream.read(rBuffer);
                                                        Thread.sleep(10);
                                                        mOutputStream.write("*#WL C800 000a 0bf4000000#*".getBytes("ISO-8859-1"), 0, 27);
                                                        Thread.sleep(10);
                                                    } catch (Exception e) {
                                                        // TODO: handle exception
                                                        e.printStackTrace();
                                                    }

                                                } else if ((data[21 + 7] == '4') && (data[22 + 7] == '7') && (data[23 + 7] == '0') && (data[24 + 7] == '0')) {
                                                    //clear input buff
                                                    try {
                                                        if (mInputStream.available() > 0)
                                                            len = mInputStream.read(rBuffer);
                                                        Thread.sleep(10);
                                                        mOutputStream.write("*#WL C800 000a 0bf4000000#*".getBytes("ISO-8859-1"), 0, 27);
                                                        Thread.sleep(10);
                                                    } catch (Exception e) {
                                                        // TODO: handle exception
                                                        e.printStackTrace();
                                                    }
                                                } else if ((data[21 + 7] == '0') && (data[22 + 7] == '0') && (data[23 + 7] == '0') && (data[24 + 7] == '0')) {
                                                }
                                                // clear buff
                                                try {
                                                    if (mInputStream.available() > 0)
                                                        len = mInputStream.read(rBuffer);
                                                } catch (Exception e) {
                                                    // TODO: handle exception
                                                }
                                            }
                                            int packSerials = 0;
                                            if (datalen >= 500) {
                                                byte xor = 0;
                                                byte a, b, c, d = 0;
                                                a = data[531 + 7];
                                                b = data[532 + 7];
                                                c = data[533 + 7];
                                                d = data[534 + 7];
                                                packSerials = hexCharToInt(b) * 256 + hexCharToInt(c) * 16 + hexCharToInt(d);
                                                Log.i("get current pack=", Integer.toString(packSerials));
                                                g_packID = packSerials;
                                                data[537 + 7] = data[535 + 7];
                                                data[538 + 7] = data[536 + 7];
                                                xor = CheckXOR(data, 7);
                                                int i_xr = Math.abs(xor);
                                                if (xor < 0)
                                                    i_xr = 256 - Math.abs(xor);
                                                byte xorhi = 0, xorlo = 0;
                                                xorhi = (byte) (i_xr / 16);
                                                xorlo = (byte) (i_xr % 16);
                                                if ((xorhi >= 0) && (xorhi <= 9))
                                                    data[535 + 7] = (byte) (xorhi + '0');
                                                else if ((xorhi >= 10) && (xorhi <= 15))
                                                    data[535 + 7] = (byte) (xorhi - 10 + 'A');
                                                else
                                                    data[535 + 7] = '0';
                                                if ((xorlo >= 0) && (xorlo <= 9))
                                                    data[536 + 7] = (byte) (xorlo + '0');
                                                else if ((xorlo >= 10) && (xorlo <= 15))
                                                    data[536 + 7] = (byte) (xorlo - 10 + 'A');
                                                else
                                                    data[536 + 7] = '0';
                                                data[13 + 7] = 'A';
                                                datalen = datalen + 2;
                                                g_xor = xor;
                                            }

                                            try {
                                                mOutputStream.write(data, 7, datalen);
                                                if (i == -1) sleep(10000);
                                                sleep(50);
                                            } catch (Exception e) {
                                                // TODO: handle exception
                                                e.printStackTrace();
                                            }

                                            overtime_read = 0;
                                            while (true) {
                                                overtime_read++;
                                                try {
                                                    if (mInputStream.available() > 0)
                                                        len = mInputStream.read(rBuffer);
                                                    sleep(5);
                                                } catch (Exception e) {
                                                    // TODO: handle exception
                                                }
                                                if ((rBuffer[0] == '%') && (rBuffer[1] == '#') && (rBuffer[2] == 'W') && (rBuffer[3] == 'L') && (rBuffer[4] == '#') && (rBuffer[5] == '%'))
                                                    break;
                                                if ((rBuffer[0] == '%') && (rBuffer[1] == '#') && (rBuffer[2] == 'R') && (rBuffer[3] == 'L') && (rBuffer[4] == '#') && (rBuffer[5] == '%'))
                                                    break;
                                                if (overtime_read >= 150) {
                                                    break;
                                                }
                                            }
                                            if (overtime_read >= 150) {
                                                try {
                                                    mOutputStream.write("*#WL C800 000a 0bf4000000#*".getBytes("ISO-8859-1"), 0, 27);
                                                    sleep(10);
                                                    conn = 0;
                                                    Log.i("x3config", "升级异常中断,FPGA回复升级回复超时,包序号：" + Integer.toString(packSerials));
                                                    //发送广播升级失败
                                                    Intent intent1 = new Intent("com.listen.action.fpga_update_status");
                                                    intent1.putExtra("fpga", -1);
                                                    MyApplication.context.sendBroadcast(intent1, null);
                                                } catch (Exception e) {
                                                    // TODO: handle exception
                                                    e.printStackTrace();
                                                }
                                                break;
                                            }
                                            for (int j = 0; j < 30; j++) {
                                                try {
                                                    mOutputStream.write("*#RL 0038 0004#*".getBytes("ISO-8859-1"), 0, 16);
                                                } catch (Exception e) {
                                                    // TODO: handle exception
                                                    e.printStackTrace();
                                                }
                                                try {
                                                    sleep(4);
                                                    if (mInputStream.available() > 0)
                                                        len = mInputStream.read(rBuffer);
                                                } catch (Exception e) {
                                                    // TODO: handle exception
                                                }
//                                        Log.e(TAG, "rBuffer 0038: " + new String(rBuffer, 0, 20));
                                                if (new String(rBuffer, 0, 50).contains("3B"))
                                                    break;
                                            }
                                        }
                                        //发送完毕

                                        try {
                                            boolean issuccess = false;
                                            for (int j = 0; j < 30; j++) {
                                                try {
                                                    mOutputStream.write("*#RL 0038 0004#*".getBytes("ISO-8859-1"), 0, 16);
                                                } catch (Exception e) {
                                                    // TODO: handle exception
                                                    e.printStackTrace();
                                                }
                                                try {
                                                    sleep(1000);
                                                    if (mInputStream.available() > 0)
                                                        len = mInputStream.read(rBuffer);
                                                } catch (Exception e) {
                                                    // TODO: handle exception
                                                }
                                                if (new String(rBuffer, 0, 50).contains("65")) {
                                                    issuccess = true;
                                                    break;
                                                }
                                            }
                                            Log.e("x3config", "升级issuccess: " + issuccess);
                                            if (issuccess) {
                                                mOutputStream.write("*#WL C800 000a 0bf4000000#*".getBytes("ISO-8859-1"), 0, 27);
                                                //发送广播，升级成功
                                                conn = 0;
                                                Intent intent0 = new Intent("com.listen.action.fpga_update_status");
                                                intent0.putExtra("fpga", 0);
                                                MyApplication.context.sendBroadcast(intent0, null);
                                            } else {
                                                mOutputStream.write("*#WL C800 000a 0bf4000000#*".getBytes("ISO-8859-1"), 0, 27);
                                                //发送广播，升级完成，不一定成功
                                                conn = 0;
                                                Intent intent1 = new Intent("com.listen.action.fpga_update_status");
                                                intent1.putExtra("fpga", -1);
                                                MyApplication.context.sendBroadcast(intent1, null);
                                            }

                                        } catch (Exception e) {
                                            // TODO: handle exception
                                            e.printStackTrace();
                                        }
                                        conn = 0;
                                        System.out.println("(发送完毕)......");
                                        this.interrupt();
                                    }
                                }
                            };
                            updataThread.start();
                        }
//                        {
//                            byte[] updata = MyApplication.readFile(MyApplication.FPGApath, MyApplication.context);
//                            if (null == updata) {
//                                Log.i("x3config", "未找到升级文件");
//                                conn = 0;
//                                //发送广播升级失败
//                                Intent intent1 = new Intent("com.listen.action.fpga_update_status");
//                                intent1.putExtra("fpga", -1);
//                                MyApplication.context.sendBroadcast(intent1, null);
//                            }
//                            if (conn == 0) {
//                                //发送广播开始升级
//                                Intent intent = new Intent("com.listen.action.fpga_start_update");
//                                intent.putExtra("msg", "start");
//                                MyApplication.context.sendBroadcast(intent, null);
//                                break;
//                            }
//                            byte[] temp = new byte[256];
//                            int count = (updata.length - 271) / 256;
//                            for (int i = -1; i < count; i++) {
//                                rBuffer = new byte[1024];
//                                byte[] order = MyApplication.hexStringToBytes(Int2Hex4string(i));
//                                if (i == -1) {
//                                    try {
//                                        datalen = 27;
//                                        data = "*#M0000*#WL C800 000A 0bf4004700#*#*".getBytes("ISO-8859-1");//升级允许
//                                    } catch (UnsupportedEncodingException e) {
//                                        e.printStackTrace();
//                                    }
//                                } else {
//                                    datalen = 537;
//                                    System.arraycopy(updata, 271 + 256 * i, temp, 0, 256);
//                                    StringBuffer bf = new StringBuffer("*#M0000*#WL C800 0208 2DD2");
//                                    bf.append(MyApplication.bytesToHexString(temp));
//                                    bf.append(MyApplication.bytesToHexString(order));
//                                    bf.append("#*#*");
//                                    try {
//                                        data = bf.toString().getBytes("ISO-8859-1");
//                                    } catch (UnsupportedEncodingException e) {
//                                        e.printStackTrace();
//                                    }
//                                }
//                                len = 0;
//                                //发送操作
//                                if (datalen == 27) {
//                                    if ((data[21 + 7] == '0') && (data[22 + 7] == '0') && (data[23 + 7] == '4') && (data[24 + 7] == '7')) {
//                                        //clear input buff
//                                        try {
//                                            if (mInputStream.available() > 0)
//                                                len = mInputStream.read(rBuffer);
//                                            Thread.sleep(10);
//                                            mOutputStream.write("*#WL C800 000a 0bf4000000#*".getBytes("ISO-8859-1"), 0, 27);
//                                            Thread.sleep(10);
//                                        } catch (Exception e) {
//                                            // TODO: handle exception
//                                            e.printStackTrace();
//                                        }
//
//                                    } else if ((data[21 + 7] == '4') && (data[22 + 7] == '7') && (data[23 + 7] == '0') && (data[24 + 7] == '0')) {
//                                        //clear input buff
//                                        try {
//                                            if (mInputStream.available() > 0)
//                                                len = mInputStream.read(rBuffer);
//                                            Thread.sleep(10);
//                                            mOutputStream.write("*#WL C800 000a 0bf4000000#*".getBytes("ISO-8859-1"), 0, 27);
//                                            Thread.sleep(10);
//                                        } catch (Exception e) {
//                                            // TODO: handle exception
//                                            e.printStackTrace();
//                                        }
//                                    } else if ((data[21 + 7] == '0') && (data[22 + 7] == '0') && (data[23 + 7] == '0') && (data[24 + 7] == '0')) {
//                                    }
//                                    // clear buff
//                                    try {
//                                        if (mInputStream.available() > 0)
//                                            len = mInputStream.read(rBuffer);
//                                    } catch (Exception e) {
//                                        // TODO: handle exception
//                                    }
//                                }
//                                int packSerials = 0;
//                                if (datalen >= 500) {
//                                    byte xor = 0;
//                                    byte a, b, c, d = 0;
//                                    a = data[531 + 7];
//                                    b = data[532 + 7];
//                                    c = data[533 + 7];
//                                    d = data[534 + 7];
//                                    packSerials = hexCharToInt(b) * 256 + hexCharToInt(c) * 16 + hexCharToInt(d);
//                                    Log.i("get current pack=", Integer.toString(packSerials));
//                                    g_packID = packSerials;
//                                    data[537 + 7] = data[535 + 7];
//                                    data[538 + 7] = data[536 + 7];
//                                    xor = CheckXOR(data, 7);
//                                    int i_xr = Math.abs(xor);
//                                    if (xor < 0)
//                                        i_xr = 256 - Math.abs(xor);
//                                    byte xorhi = 0, xorlo = 0;
//                                    xorhi = (byte) (i_xr / 16);
//                                    xorlo = (byte) (i_xr % 16);
//                                    if ((xorhi >= 0) && (xorhi <= 9))
//                                        data[535 + 7] = (byte) (xorhi + '0');
//                                    else if ((xorhi >= 10) && (xorhi <= 15))
//                                        data[535 + 7] = (byte) (xorhi - 10 + 'A');
//                                    else
//                                        data[535 + 7] = '0';
//                                    if ((xorlo >= 0) && (xorlo <= 9))
//                                        data[536 + 7] = (byte) (xorlo + '0');
//                                    else if ((xorlo >= 10) && (xorlo <= 15))
//                                        data[536 + 7] = (byte) (xorlo - 10 + 'A');
//                                    else
//                                        data[536 + 7] = '0';
//                                    data[13 + 7] = 'A';
//                                    datalen = datalen + 2;
//                                    g_xor = xor;
//                                }
//
//                                try {
//                                    mOutputStream.write(data, 7, datalen);
//                                    if (i == -1) sleep(10000);
//                                    sleep(50);
//                                } catch (Exception e) {
//                                    // TODO: handle exception
//                                    e.printStackTrace();
//                                }
//
//                                overtime_read = 0;
//                                while (true) {
//                                    overtime_read++;
//                                    try {
//                                        if (mInputStream.available() > 0)
//                                            len = mInputStream.read(rBuffer);
//                                        sleep(5);
//                                    } catch (Exception e) {
//                                        // TODO: handle exception
//                                    }
//                                    if ((rBuffer[0] == '%') && (rBuffer[1] == '#') && (rBuffer[2] == 'W') && (rBuffer[3] == 'L') && (rBuffer[4] == '#') && (rBuffer[5] == '%'))
//                                        break;
//                                    if ((rBuffer[0] == '%') && (rBuffer[1] == '#') && (rBuffer[2] == 'R') && (rBuffer[3] == 'L') && (rBuffer[4] == '#') && (rBuffer[5] == '%'))
//                                        break;
//                                    if (overtime_read >= 150) {
//                                        break;
//                                    }
//                                }
//                                if (overtime_read >= 150) {
//                                    try {
//                                        mOutputStream.write("*#WL C800 000a 0bf4000000#*".getBytes("ISO-8859-1"), 0, 27);
//                                        sleep(10);
//                                        conn = 0;
//                                        Log.i("x3config", "升级异常中断,FPGA回复升级回复超时,包序号：" + Integer.toString(packSerials));
//                                        //发送广播升级失败
//                                        Intent intent1 = new Intent("com.listen.action.fpga_update_status");
//                                        intent1.putExtra("fpga", -1);
//                                        MyApplication.context.sendBroadcast(intent1, null);
//                                    } catch (Exception e) {
//                                        // TODO: handle exception
//                                        e.printStackTrace();
//                                    }
//                                    break;
//                                }
//                                for (int j = 0; j < 30; j++) {
//                                    try {
//                                        mOutputStream.write("*#RL 0038 0004#*".getBytes("ISO-8859-1"), 0, 16);
//                                    } catch (Exception e) {
//                                        // TODO: handle exception
//                                        e.printStackTrace();
//                                    }
//                                    try {
//                                        sleep(4);
//                                        if (mInputStream.available() > 0)
//                                            len = mInputStream.read(rBuffer);
//                                    } catch (Exception e) {
//                                        // TODO: handle exception
//                                    }
////                                        Log.e(TAG, "rBuffer 0038: " + new String(rBuffer, 0, 20));
//                                    if (new String(rBuffer, 0, 50).contains("3B"))
//                                        break;
//                                }
//                            }
//                            //发送完毕
//
//                            try {
//                                boolean issuccess = false;
//                                for (int j = 0; j < 30; j++) {
//                                    try {
//                                        mOutputStream.write("*#RL 0038 0004#*".getBytes("ISO-8859-1"), 0, 16);
//                                    } catch (Exception e) {
//                                        // TODO: handle exception
//                                        e.printStackTrace();
//                                    }
//                                    try {
//                                        sleep(1000);
//                                        if (mInputStream.available() > 0)
//                                            len = mInputStream.read(rBuffer);
//                                    } catch (Exception e) {
//                                        // TODO: handle exception
//                                    }
//                                    if (new String(rBuffer, 0, 50).contains("65")) {
//                                        issuccess = true;
//                                        break;
//                                    }
//                                }
//                                Log.e("x3config", "升级issuccess: " + issuccess);
//                                if (issuccess) {
//                                    mOutputStream.write("*#WL C800 000a 0bf4000000#*".getBytes("ISO-8859-1"), 0, 27);
//                                    //发送广播，升级成功
//                                    conn = 0;
//                                    Intent intent0 = new Intent("com.listen.action.fpga_update_status");
//                                    intent0.putExtra("fpga", 0);
//                                    MyApplication.context.sendBroadcast(intent0, null);
//                                } else {
//                                    mOutputStream.write("*#WL C800 000a 0bf4000000#*".getBytes("ISO-8859-1"), 0, 27);
//                                    //发送广播，升级完成，不一定成功
//                                    conn = 0;
//                                    Intent intent1 = new Intent("com.listen.action.fpga_update_status");
//                                    intent1.putExtra("fpga", -1);
//                                    MyApplication.context.sendBroadcast(intent1, null);
//                                }
//
//                            } catch (Exception e) {
//                                // TODO: handle exception
//                                e.printStackTrace();
//                            }
//                            conn = 0;
//                            System.out.println("(发送完毕)......");
//                        }
//                    }
                        break;
                        case GET_FPGA_VERSION: {
                            String last = "";
                            if (Version.contains("Q5")) {
                                len = 0;
                                respondString = "*#";
                                btlen[0] = (byte) (len & 0x0ff);
                                btlen[1] = (byte) ((len >> 8) & 0x0ff);
                                btlen[2] = (byte) ((len >> 16) & 0x0ff);
                                btlen[3] = (byte) ((len >> 24) & 0x0ff);
                                strlength = "";
                                try {
                                    strlength = new String(btlen, "ISO-8859-1");
                                } catch (Exception e) {
                                    // TODO: handle exception
                                }

                                Cmd[0] = FPGA_CMD_GETVERSION;
                                strCmd = new String(Cmd);
                                respondString += strCmd;        //cmd
                                respondString += strlength;    //length
                                //no data
                                respondString += "#*";

                                //1: *********** 读出 *****************
                                String resString = "*#RL 0002 0002#*";
                                try {
                                    respData = resString.getBytes("ISO-8859-1");
                                } catch (Exception e) {
                                    // TODO: handle exception
                                }
                                try {
                                    mOutputStream.write(respData);
                                    Thread.sleep(50);
                                } catch (Exception e) {
                                    // TODO: handle exception
                                    e.printStackTrace();
                                }
                                try {
                                    if (mInputStream.available() > 0)
                                        len = mInputStream.read(rBuffer);
                                } catch (Exception e) {
                                    // TODO: handle exception
                                    e.printStackTrace();
                                }

                                String string = "";
                                String fpver = "";
                                try {
                                    string = new String(rBuffer, "ISO-8859-1");
                                    //String[] stringsplit=string.split("&", 1) ;
                                } catch (Exception e) {
                                    // TODO: handle exception
                                }
                                int pos = string.indexOf("%*#");
                                if (pos < 0) {
                                    System.out.println("get version has not get respond,return");
                                    break;
                                }
                                for (int c = 0; c < 2; c++) {
                                    String ver1 = string.substring(pos + 3 + c * 2, pos + 3 + 2 * (c + 1));
                                    last = string.substring(pos + 3, pos + 3 + 4);
                                    byte[] bytes = new byte[1];
                                    if (!isNumeric(ver1)) return;
                                    int device = Integer.parseInt(ver1);
                                    bytes = intToDecimOneByte(device);

                                    try {
                                        ver1 = new String(bytes, "ISO-8859-1");
                                        fpver += ver1;
                                    } catch (Exception e) {

                                    }
                                }

                                Log.e(TAG, "respFPAGV: " + last);
//                            发送广播
                                Intent intent0 = new Intent("com.listen.action.fpga_version");
                                intent0.putExtra("fpgaversion", last);
                                MyApplication.context.sendBroadcast(intent0, null);
                            } else {
                                len = 0;
                                respondString = "*#";
                                btlen[0] = (byte) (len & 0x0ff);
                                btlen[1] = (byte) ((len >> 8) & 0x0ff);
                                btlen[2] = (byte) ((len >> 16) & 0x0ff);
                                btlen[3] = (byte) ((len >> 24) & 0x0ff);

                                strlength = "";
                                try {
                                    strlength = new String(btlen, "ISO-8859-1");
                                } catch (Exception e) {
                                    // TODO: handle exception
                                }

                                Cmd[0] = FPGA_CMD_GETVERSION;
                                strCmd = new String(Cmd);
                                respondString += strCmd;        //cmd
                                respondString += strlength;    //length
                                //no data
                                respondString += "#*";

                                //1:send data to fpga throught serial
//                                try {
//                                    mOutputStream.write(data, 7, datalen);
//                                    Thread.sleep(5);
//                                } catch (Exception e) {
//                                    // TODO: handle exception
//                                    e.printStackTrace();
//                                }


                                String resString = "*#RL 000f 0080#*";
                                try {
                                    respData = resString.getBytes("ISO-8859-1");
                                } catch (Exception e) {
                                    // TODO: handle exception
                                }
                                try {
                                    mOutputStream.write(respData);
                                    Thread.sleep(50);
                                } catch (Exception e) {
                                    // TODO: handle exception
                                    e.printStackTrace();
                                }
                                try {
                                    if (mInputStream.available() > 0)
                                        len = mInputStream.read(rBuffer);
                                } catch (Exception e) {
                                    // TODO: handle exception
                                    e.printStackTrace();
                                }

                                String string = "";
                                try {
                                    string = new String(rBuffer, "ISO-8859-1");
                                    //String[] stringsplit=string.split("&", 1) ;
                                } catch (Exception e) {
                                    // TODO: handle exception
                                }
                                int pos = string.indexOf("%*#");
                                if (pos < 0) {
                                    System.out.println("get version has not get respond,return");
                                    break;
                                }
                                string = string.substring(pos + 3, pos + 3 + 2);

                                int iDeviceNum = Integer.parseInt(string);

                                resString = "*#RL 4000 ";
                                resString += Int2Dec4string(iDeviceNum + iDeviceNum);
                                resString += "#*";

                                try {
                                    respData = resString.getBytes("ISO-8859-1");
                                } catch (Exception e) {
                                    // TODO: handle exception
                                    e.printStackTrace();
                                }
                                try {
                                    mOutputStream.write(respData);
                                    Thread.sleep(300);
                                } catch (Exception e) {
                                    // TODO: handle exception
                                    e.printStackTrace();
                                }

                                try {
                                    if (mInputStream.available() > 0)
                                        len = mInputStream.read(rBuffer);
                                } catch (Exception e) {
                                    // TODO: handle exception
                                    e.printStackTrace();
                                }
                                try {
                                    string = new String(rBuffer, "ISO-8859-1");
                                } catch (Exception e) {
                                    // TODO: handle exception
                                    e.printStackTrace();
                                }
                                pos = string.indexOf("%#RL#%*#");
                                if (pos < 0) {
                                    System.out.println("no find fpga device");
                                    iDeviceNum = 0;
                                }

                                string = string.replace("%#RL#%*#", "");
                                string = string.replace("#*", "");
                                string = string.replace("\r\n", "");
                                string = string.replace(" ", "");

                                len = iDeviceNum * 2;
                                respondString = "*#";
                                btlen[0] = (byte) (len & 0x0ff);
                                btlen[1] = (byte) ((len >> 8) & 0x0ff);
                                btlen[2] = (byte) ((len >> 16) & 0x0ff);
                                btlen[3] = (byte) ((len >> 24) & 0x0ff);

                                strlength = "";
                                try {
                                    strlength = new String(btlen, "ISO-8859-1");
                                } catch (Exception e) {
                                    // TODO: handle exception
                                }

                                Cmd[0] = FPGA_CMD_GETVERSION;
                                strCmd = new String(Cmd);
                                respondString += strCmd;            //cmd
                                respondString += strlength;        //length
                                String[] strp = string.split("&");
                                for (int n = 0; n < iDeviceNum * 2; n++)    //no data
                                {

                                    int fpgaVer = 0;
                                    String st = "";
                                    if (strp[n].length() >= 2)
                                        st = strp[n].substring(0, 2);
                                    if (isNumeric(st)) {
                                        fpgaVer = Integer.parseInt(st, 10);
                                    } else {

                                        try {
                                            fpgaVer = Integer.parseInt(st, 10);
                                            fpgaVer += 0x80;
                                        } catch (Exception e) {
                                            // TODO: handle exception

                                        }
                                    }

                                    try {
                                        strlength = new String(btlen, "ISO-8859-1");
                                    } catch (Exception e) {
                                        // TODO: handle exception
                                    }
                                    String verStr = "";
                                    byte[] bytes = new byte[1];
                                    bytes = intToDecimOneByte(fpgaVer);
                                    try {
                                        verStr = new String(bytes, "ISO-8859-1");
                                    } catch (Exception e) {
                                        // TODO: handle exception
                                    }
                                    last += st;
                                    respondString += verStr;

                                }
                                Log.e(TAG, "respFPAGV: " + last);
                                //发送广播
                                Intent intent0 = new Intent("com.listen.action.fpga_version");
                                intent0.putExtra("fpgaversion", last);
                                MyApplication.context.sendBroadcast(intent0, null);
                            }


                        }

                        break;
                        default:
                            break;
                    }
                }

                //try to send data to client the same data
                /*
                try
                {
                    final String target_ip = dp.getAddress().toString().substring(1);

                    msg = new Message();
                    msg.what = 0x111;
                    information = "send udp requestion to: \n" + target_ip + "\n";
                    msg.obj = information;
                    handler.sendMessage(msg);

                    information="respond ...\r\n" ;
                    udpSend(codeString);
                    socket = new Socket(target_ip, 8080);  //not use ,only pick for try
                }

                catch (IOException e) {
                    e.printStackTrace();
                }
                */

            }
        }

    }

    public void udpSend(String sendStr) {
        try {
            DatagramSocket ds = new DatagramSocket();
//            Log.i("SocketInfo", "IP：" + dp.getAddress().getHostAddress() + "port:" + dp.getPort());
            dp = new DatagramPacket(sendStr.getBytes(), sendStr.getBytes().length, dp.getAddress(), dp.getPort());
            ds.send(dp);
        } catch (Exception e) {
            // TODO: handle exception
        }
    }


    public void udpSend(byte[] sendByte) {
        try {
            int cnt = sendByte.length;
            DatagramSocket ds = new DatagramSocket();
//            Log.i("SocketInfo", "IP：" + dp.getAddress().getHostAddress() + "port:" + dp.getPort() + " cnt: " + cnt);
            dp = new DatagramPacket(sendByte, cnt, dp.getAddress(), dp.getPort());
            ds.send(dp);
        } catch (Exception e) {
            e.printStackTrace();
            // TODO: handle exception
        }
    }


    private static byte CheckXOR(byte[] checkcon, int offset) {
        byte[] con_buf = new byte[258];
        for (int j = 0; j < 258; j++) {
            con_buf[j] = ConbimeStr(checkcon[19 + offset + (j * 2)], checkcon[19 + offset + (j * 2 + 1)]);
        }

        byte check = 0;
        check = (byte) (con_buf[0] ^ con_buf[1]);
        for (int i = 2; i < 258; i++) {
            check = (byte) (check ^ con_buf[i]);
        }

        return check;
    }

    private static int hexCharToInt(byte c) {
        if ((c >= '0') && (c <= '9'))
            return (c - '0');
        if ((c >= 'A') && (c <= 'F'))
            return (c - 'A' + 10);
        if ((c >= 'a') && (c <= 'f'))
            return (c - 'a' + 10);

        throw new RuntimeException("invalid hex char '" + c + "'");
    }

    private static byte ConbimeStr(byte high, byte low) {
        byte retVal = 0, hi = 0, lo = 0;
        if ((high >= '0') && (high <= '9'))
            hi = (byte) (high - '0');
        if ((high >= 'a') && (high <= 'f'))
            hi = (byte) (high - 'a' + 10);
        if ((high >= 'A') && (high <= 'F'))
            hi = (byte) (high - 'A' + 10);

        if ((low >= '0') && (low <= '9'))
            lo = (byte) (low - 0x30);
        if ((low >= 'a') && (low <= 'f'))
            lo = (byte) (low - 'a' + 10);
        if ((low >= 'A') && (low <= 'F'))
            lo = (byte) (low - 'A' + 10);

        retVal = (byte) (hi * 16 + lo);

        return retVal;
    }

    // small endian
    private static byte[] int2Bytes(int value, int len) {
        byte[] b = new byte[len];
        for (int i = 0; i < len; i++) {
            b[len - i - 1] = (byte) ((value >> 8 * (len - i - 1)) & 0xff);
        }
        return b;
    }

    //little endian convert
    private static int bytes2Int(byte[] b, int start, int len) {
        int sum = 0;
        int end = start + len - 1;
        for (int i = end; i >= start; i--) {
            int n = ((int) b[i]) & 0xff;
            n <<= (--len) * 8;
            sum += n;
        }
        return sum;
    }

    private String getLocalHostIp() {
        String ipaddress = "";
        try {//得到本机所有的网络接口
            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
            // search all the internet interface
            while (en.hasMoreElements()) {
                NetworkInterface nif = en.nextElement();//get all the binder ip
                if (nif.getName().equals("eth0")) {
                    Enumeration<InetAddress> inet = nif.getInetAddresses();
                    //list all the ip
                    while (inet.hasMoreElements()) {
                        InetAddress ip = inet.nextElement();
                        if (!ip.isLoopbackAddress() && ip instanceof Inet4Address) {
                            try {
                                File file_ip = new File("sdcard/iplist.txt");
                                fd_ip = new FileOutputStream(file_ip, true);
                                String l_ip = ip.getHostAddress();
                                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                                Date curDate = new Date(System.currentTimeMillis());//
                                String str = formatter.format(curDate);
                                fd_ip.write(str.getBytes());
                                fd_ip.write(l_ip.getBytes());
                                fd_ip.write("\r\n".getBytes());//
                                fd_ip.close();
                            } catch (Exception e) {
                                // TODO: handle exception
                                e.printStackTrace();
                            }

                            return ip.getHostAddress();
                        }
                    }
                }
            }
        } catch (SocketException e) {
            Log.e("feige", "得到本机所有的网络接口失败---");
            e.printStackTrace();
        }
        return ipaddress;
    }


    public static String getMacUseJavaIntetface() throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        String string = "00:00:00:00:00:00";
        while (interfaces.hasMoreElements()) {
            try {
                NetworkInterface iF = interfaces.nextElement();
                if (iF.getName().equals("eth0")) {
                    byte[] addr = iF.getHardwareAddress();
                    if (addr == null || addr.length == 0) {
                        continue;
                    }
                    StringBuilder buf = new StringBuilder();
                    for (byte b : addr) {
                        buf.append(String.format("%02X:", b));
                    }
                    if (buf.length() > 0) {
                        buf.deleteCharAt(buf.length() - 1);
                    }
                    String mac = buf.toString();
//                    Log.d("mac", "interfaceName=" + iF.getName() + ", mac=" + mac);
                    if ("eth0".equalsIgnoreCase(iF.getName())) {
                        string = mac;
//                        Log.i("get interface ", "eth0");
                        break;
                    }
                }
            } catch (Exception e) {
                // TODO: handle exception
                System.out.println("exceptios");
            }

        }
        return string;
    }


    private static String str2binstr(String str, String sep) {

        String result = "";
        String sr = "";
        byte[] bbyte = new byte[6];
        long value = 0;
        int partnum = 0;
        if (str != null) {
            StringTokenizer st = new StringTokenizer(str, sep);
            while (st.hasMoreTokens()) {
                partnum = Integer.parseInt(st.nextToken());
                value = (value << 8) + partnum;
            }
            bbyte = intToBytes(value);
            try {
                String strlocalIp = new String(bbyte, "ISO-8859-1");
                result += strlocalIp;
            } catch (Exception e) {
                // TODO: handle exception
            }
        }
        return result;
    }


    //for big-endian
    public static byte[] intToBytes(long value) {
        byte[] src = new byte[4];
        src[0] = (byte) ((value >> 24) & 0xFF);
        src[1] = (byte) ((value >> 16) & 0xFF);
        src[2] = (byte) ((value >> 8) & 0xFF);
        src[3] = (byte) (value & 0xFF);
        return src;
    }

    // convert int to
    public static byte[] intToDecimOneByte(int value) {
        byte[] src = new byte[1];
        /*
        src[0] =  (byte) ((value>>24) & 0xFF);
        src[1] =  (byte) ((value>>16) & 0xFF);
        src[2] =  (byte) ((value>>8) & 0xFF);
        */
        src[0] = (byte) (value & 0xFF);
        //src[1] =  (byte) (0);
        //src[2] =  (byte) (0);
        //src[3] =  (byte) (0);
        return src;
    }


    private static byte[] inet_addr(String ipstr) {
        try {
            InetAddress ia = InetAddress.getByName(ipstr);
            byte[] iparr = ia.getAddress();

            return iparr;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static byte[] getMacBytes(String mac) {
        byte[] macBytes = new byte[6];
        String[] strArr = mac.split(":");

        for (int i = 0; i < strArr.length; i++) {
            int value = Integer.parseInt(strArr[i], 16);
            macBytes[i] = (byte) value;
        }
        return macBytes;
    }


    private static String texToIp(String ips) {
        try {
            StringBuffer result = new StringBuffer();
            if (ips != null && ips.length() == 8) {
                for (int i = 0; i < 8; i += 2) {
                    if (i != 0)
                        result.append('.');
                    result.append(Integer.parseInt(ips.substring(i, i + 2), 16));
                }
            }
            return result.toString();
        } catch (NumberFormatException ex) {
            //Logger.e(ex);
        }
        return "";
    }


    private static String str2HexStr(String str) {
        char[] chars = "0123456789ABCDEF".toCharArray();
        StringBuilder sb = new StringBuilder("");
        byte[] bs = str.getBytes();
        int bit;
        for (int i = 0; i < bs.length; i++) {
            bit = (bs[i] & 0x0f0) >> 4;
            sb.append(chars[bit]);
            bit = bs[i] & 0x0f;
            sb.append(chars[bit]);
            //sb.append(' ');
        }
        return sb.toString().trim();
    }


    private String bytes2String(byte[] data) {
        String getString = "";

        for (int i = 0; i < data.length; i++) {

            getString += String.format("%02X", data[i]);

        }

        return getString;

    }

    private static int BytesToint(byte[] arr) {
        int rs0 = (int) ((arr[0] & 0xff) << 0 * 8);
        int rs1 = (int) ((arr[1] & 0xff) << 1 * 8);
        int rs2 = (int) ((arr[2] & 0xff) << 2 * 8);
        int rs3 = (int) ((arr[3] & 0xff) << 3 * 8);
        return rs0 + rs1 + rs2 + rs3;
    }

    //妫�娴媐pga鍥炲鏁版嵁
    private boolean checkFpgaRespond() {
        boolean ret = false;
        byte[] rBuffer = new byte[512];
        int len = 0, len1 = 0;
        try {
            len1 = mInputStream.available();

            if (len1 > 0)
                len = mInputStream.read(rBuffer);
        } catch (Exception e) {
            // TODO: handle exception
            e.printStackTrace();
        }
        if (((rBuffer[0] == '%') && (rBuffer[1] == '#') && (rBuffer[2] == 'W') && (rBuffer[3] == 'L') && (rBuffer[4] == '#') && (rBuffer[5] == '%'))
                || ((rBuffer[0] == '%') && (rBuffer[1] == '#') && (rBuffer[2] == 'R') && (rBuffer[3] == 'L') && (rBuffer[4] == '#') && (rBuffer[5] == '%'))) {
            ret = true;
        }

        return ret;
    }

    //create fPrecompensation=1/gamma
    private void BuildGammaTable(int GammaLUT[], float gamma, int MaxVal) {
        int i;
        float f;
        int gammaVal;
        for (i = 0; i < 256; i++) {
            f = (float) ((i + 0.0) / 255);        //褰掍竴鍖�
            f = (float) Math.pow(f, gamma);    //棰勮ˉ鍋�(姹傛寚鏁板嚱鏁�)
            gammaVal = (int) (f * (MaxVal - 1)); //鍙嶅綊涓�鍖�
            GammaLUT[i] = (gammaVal >> 4);
        }
    }

    //convert int to 4char hex string(eg: 20="0014")
    private String Int2Hex4string(int n) {
        String str = Integer.toHexString(n);
        int l = str.length();
        if (l == 1)
            str = "000" + str;
        if (l == 2)
            str = "00" + str;
        if (l == 3)
            str = "0" + str;
        return str;
    }

    //convert int to 4char decim string(eg: 20="0020")
    private String Int2Dec4string(int n) {
        String str = Integer.toString(n, 10);
        int l = str.length();
        if (l == 1)
            str = "000" + str;
        if (l == 2)
            str = "00" + str;
        if (l == 3)
            str = "0" + str;
        return str;
    }

    private boolean SetFpgaCtrModeSram(boolean mode) {
        boolean ret = false;
        byte[] sendbyte = new byte[30];
        String str = "";
        if (mode)
            str = "*#WL C800 0202 0bf447#*";
        else {
            str = "*#WL C800 0202 0bf400#*";
        }

        try {
            sendbyte = str.getBytes("ISO-8859-1");
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            mOutputStream.write(sendbyte, 0, sendbyte.length);
            Thread.sleep(50);//寤舵椂50ms
        } catch (Exception e) {
            // TODO: handle exception
            e.printStackTrace();
        }
        try {
            mInputStream.read(sendbyte);
        } catch (Exception e) {
            // TODO: handle exception
            e.printStackTrace();
        }
        return ret;
    }

    private boolean SetFpgaParaStoreToSpi(boolean store) {
        byte[] sendbyte = new byte[30];
        String str = "";
        if (store)
            str = "*#WL C400 0202 0af547#*";
        else {
            str = "*#WL C400 0202 0af500#*";
        }

        try {
            sendbyte = str.getBytes("ISO-8859-1");
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            mOutputStream.write(sendbyte, 0, sendbyte.length);
            Thread.sleep(50);//寤舵椂50ms
        } catch (Exception e) {
            // TODO: handle exception
            e.printStackTrace();
        }
        try {
            mInputStream.read(sendbyte);
        } catch (Exception e) {
            // TODO: handle exception
            e.printStackTrace();
        }
        return true;
    }

    private int GetCardNum() {

        return 1;
    }

    private static boolean isNumeric(String str) {
        if (str.length() <= 0) return false;
        for (int i = str.length(); --i >= 0; ) {
            if (!Character.isDigit(str.charAt(i))) {
                return false;
            }

        }
        return true;
    }

    //璁剧疆绯荤粺鏃堕棿
    private void SetDatetime(int year, int month, int day, int hour, int min, int sec) {
        Calendar cal = Calendar.getInstance();

        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month);
        cal.set(Calendar.DAY_OF_MONTH, day);
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, min);
        cal.set(Calendar.SECOND, sec);
        long when = cal.getTimeInMillis();

        if (when / 1000 < Integer.MAX_VALUE) {
            SystemClock.setCurrentTimeMillis(when);
        }
    }
}
