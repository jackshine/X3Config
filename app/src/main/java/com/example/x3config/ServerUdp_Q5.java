package com.example.x3config;

import android.content.Intent;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.StringTokenizer;

import android_serialport_api.SerialPort;

import static android.content.ContentValues.TAG;

public class ServerUdp_Q5 extends Thread {

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

    //新增手机获取信息
    private final byte UPDATE_FPGA = 78;  //接收卡/发送卡程序升级
    private final byte GET_FPGA_VERSION = 79;  //获取FPGA版本
    byte g_LastGammaRed = 32, g_LastGammaGreen = 32, g_LastGammaBlue = 32;
    int g_LastBright = 255;

    Socket socket = null;
    MulticastSocket ms = null;
    DatagramPacket dp;
    Handler handler = new Handler();

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

    public ServerUdp_Q5() {

    }


    public static int byteToInt(byte b) {
        return b & 0xFF;
    }

    @Override
    public void run() {
        String information;
        try {
            mSerialPort = new SerialPort(new File("/dev/ttyAMA2"), 576000, 0);  //flag
        } catch (Exception e) {
            // TODO: handle exception
            e.printStackTrace();
            return;
        }

        byte[] data = new byte[8192];
        byte[] respData = new byte[1024];
        try {
            InetAddress groupAddress = InetAddress.getByName("224.0.0.1");
            ms = new MulticastSocket(5001);
            ms.joinGroup(groupAddress);
        } catch (Exception e) {
            e.printStackTrace();
        }

        mOutputStream = (FileOutputStream) mSerialPort.getOutputStream();
        if (mOutputStream == null)
            return;
        mInputStream = (FileInputStream) mSerialPort.getInputStream();
        if (mInputStream == null)
            return;


        int g_packID = 0;
        byte g_xor = 0;

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

        while (true) {
            try {
                dp = new DatagramPacket(data, data.length);
                if (ms != null) {
                    ms.setSoTimeout(1000);
                    ms.receive(dp);
                }
            } catch (Exception e) {
                //e.printStackTrace();
            }

            if (dp.getAddress() != null) {
                final String quest_ip = dp.getAddress().toString();

                String host_ip = getLocalHostIp();
                System.out.println("host_ip:  --------------------  " + host_ip);
                /*若udp包的ip地址 是 本机的ip地址的话，丢掉这个包(不处理)*/
                if ((!host_ip.equals("")) && host_ip.equals(quest_ip.substring(1))) {
                    continue;
                }
                final String codeString = new String(data, 0, dp.getLength());
                System.out.println("data:------------ " + data[2] + "--codeString: --------------------" + codeString);
                byte[] rBuffer = new byte[1024];
                //for cmd parameters
                if ((data[0] == '*') && ('#' == data[1])) {
                    int datalen = 0;
                    datalen = bytes2Int(data, 3, 4);
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

                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            break;
                        case ST_CMD_SETBRIGHT: {
                            try {
                                mOutputStream.write(data, 7, datalen);
                                Thread.sleep(100);
                                Log.e("x3config", "appbright send success");
                            } catch (Exception e) {
                                // TODO: handle exception
                                Log.e("x3config", "appbright send fail");
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
                            System.out.println("recv cmd of search card .......\n");

                            respondString = "*#";
                            btlen[0] = (byte) (len & 0x0ff);
                            btlen[1] = (byte) ((len >> 8) & 0x0ff);
                            btlen[2] = (byte) ((len >> 16) & 0x0ff);
                            btlen[3] = (byte) ((len >> 24) & 0x0ff);

                            String strMac = "";
                            try {
                                strMac = getMacUseJavaIntetface();
                            } catch (SocketException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }


                            String strlength = "";
                            try {
                                strlength = new String(btlen, "ISO-8859-1");    //ISO-8859-1
                            } catch (Exception e) {
                                // TODO: handle exception
                            }

                            Cmd[0] = FPGA_CMD_GetIP;
                            String strCmd = new String(Cmd);
                            respondString += strCmd;        //cmd
                            respondString += strlength;        //length

                            byte[] localIp = new byte[4];
                            byte[] mask = new byte[6];
                            byte[] gatewayIP = new byte[4];

                            InetAddress inetAddress;
                            try {
                                inetAddress = InetAddress.getByName(host_ip);
                            } catch (Exception e) {
                                // TODO: handle exception
                            }
                            respondString += str2binstr(host_ip, ".");
                            respondString += str2binstr("255.255.255.0", ".");  //mask
                            respondString += str2binstr("192.168.0.1", ".");      //g_dwGatewayIP

                            byte[] mac = new byte[6];
                            mac = getMacBytes(strMac);                        //6 byte mac address
                            try {
                                String stringMac = new String(mac, "ISO-8859-1");
                                respondString += stringMac;
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            byte[] ver = new byte[4];
                            ver[0] = 1;
                            ver[1] = 0;
                            ver[2] = 0;
                            ver[3] = 1;
                            try {
                                String stringVer = new String(ver, "ISO-8859-1");  //version[4]
                                respondString += stringVer;
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            byte[] DevType = new byte[10];            //byte DevType[10]
                            Arrays.fill(DevType, (byte) 0);

                            "Q5".getBytes(0, "Q5".length(), DevType, 0);
                            try {
                                String stringType = new String(DevType, "ISO-8859-1");
                                respondString += stringType;
                            } catch (Exception e) {
                                // TODO: handle exception
                            }

                            byte[] userID = new byte[16];
                            Arrays.fill(userID, (byte) 0);
                            "guest".getBytes(0, "guest".length(), userID, 0);
                            try {
                                String stringUser = new String(userID, "ISO-8859-1");
                                respondString += stringUser;
                            } catch (Exception e) {
                                // TODO: handle exception
                            }

                            byte[] BspID = new byte[16];
                            Arrays.fill(BspID, (byte) 0);
                            "android4.4".getBytes(0, "android4.4".length(), BspID, 0);
                            try {
                                String stringBsp = new String(BspID, "ISO-8859-1");
                                respondString += stringBsp;
                            } catch (Exception e) {
                                // TODO: handle exception
                            }

                            respondString += "#*";
                            try {
                                respData = respondString.getBytes("ISO-8859-1");
                            } catch (Exception e) {
                                // TODO: handle exception
                            }

                            udpSend(respData);

                            //System.out.println(respondString);
                            break;
                        case FPGA_CMD_PARA:        //FPGA_CMD_TRANSLATE
                        case FPGA_CMD_ROUTE:
                        case FPGA_CMD_LINK: {
                            //1 :send to fpga throught uart2);
                            try {
                                mOutputStream.write(data, 7, datalen);
                                Thread.sleep(100);

                            } catch (Exception e) {
                                // TODO: handle exception

                                e.printStackTrace();
                            }

                            String resString = "*#00000#*";
                            boolean ret;

                            //2 :read state from serial port
                            ret = checkFpgaRespond();
                            if (ret) {
                                //send respond to client pc
                                try {
                                    respData = resString.getBytes("ISO-8859-1");
                                } catch (Exception e) {
                                    // TODO: handle exception
                                }
                                udpSend(respData);
                                Log.e("x3config", "through send success");
                            } else {
                                Log.e("x3config", "through send fail");
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
                            for (int i = 0; i < 256; i++) {
                                resString += Int2Hex4string(i);
                            }
                            resString += "#*";
                            try {
                                respData = resString.getBytes("ISO-8859-1");
                            } catch (Exception e) {
                                // TODO: handle exception
                            }

                            try {
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

                        case FPGA_CMD_SETGAMMA_GREEN: {
                            int GammaLUT[] = new int[256];
                            int BrightVal = 65535;
                            char GammaRed;
                            GammaRed = (char) data[7];
                            BuildGammaTable(GammaLUT, GammaRed / 10 + 0.0F, BrightVal);
                            String resString = "*#WL FC00 0202 06f9";
                            for (int i = 0; i < 256; i++) {
                                resString += Int2Hex4string(i);
                            }
                            resString += "#*";
                            try {
                                respData = resString.getBytes("ISO-8859-1");
                            } catch (Exception e) {
                                // TODO: handle exception
                            }

                            try {
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
                            for (int i = 0; i < 256; i++) {
                                resString += Int2Hex4string(i);
                            }
                            resString += "#*";
                            try {
                                respData = resString.getBytes("ISO-8859-1");
                            } catch (Exception e) {
                                // TODO: handle exception
                            }

                            try {
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
                            //g_LastGammaRed
                            len = 1;
                            respondString = "*#";

                            btlen[0] = (byte) (len & 0x0ff);
                            btlen[1] = (byte) ((len >> 8) & 0x0ff);
                            btlen[2] = (byte) ((len >> 16) & 0x0ff);
                            btlen[3] = (byte) ((len >> 24) & 0x0ff);


                            strlength = "";
                            try {
                                strlength = new String(btlen, "ISO-8859-1");    //jiang ISO-8859-1
                            } catch (Exception e) {
                                // TODO: handle exception
                            }

                            Cmd[0] = FPGA_CMD_GETGAMMA_RED;
                            strCmd = new String(Cmd);
                            respondString += strCmd;        //cmd
                            respondString += strlength;    //length
                            Cmd[0] = g_LastGammaRed;
                            strCmd = new String(Cmd);
                            respondString += strCmd;        //data
                            respondString += "#*";

                            try {
                                respData = respondString.getBytes("ISO-8859-1");
                            } catch (Exception e) {
                                // TODO: handle exception
                            }
                            udpSend(respData);
                            try {
                                Thread.sleep(100);
                            } catch (Exception e) {
                                // TODO: handle exception
                            }
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
                            } catch (Exception e) {
                                // TODO: handle exception
                            }

                            Cmd[0] = FPGA_CMD_GETGAMMA_GREEN;
                            strCmd = new String(Cmd);
                            respondString += strCmd;        //cmd
                            respondString += strlength;    //length
                            Cmd[0] = g_LastGammaGreen;
                            strCmd = new String(Cmd);
                            respondString += strCmd;        //data
                            respondString += "#*";

                            try {
                                respData = respondString.getBytes("ISO-8859-1");
                            } catch (Exception e) {
                                // TODO: handle exception
                                e.printStackTrace();
                            }
                            udpSend(respData);
                            try {
                                Thread.sleep(100);
                            } catch (Exception e) {
                                // TODO: handle exception
                                e.printStackTrace();
                            }
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
                            } catch (Exception e) {
                                // TODO: handle exception
                            }

                            Cmd[0] = FPGA_CMD_GETGAMMA_BLUE;
                            strCmd = new String(Cmd);
                            respondString += strCmd;        //cmd
                            respondString += strlength;    //length
                            Cmd[0] = g_LastGammaBlue;
                            strCmd = new String(Cmd);
                            respondString += strCmd;        //data
                            respondString += "#*";
                            try {
                                respData = respondString.getBytes("ISO-8859-1");
                            } catch (Exception e) {
                                // TODO: handle exception
                                e.printStackTrace();
                            }
                            udpSend(respData);
                            try {
                                Thread.sleep(100);
                            } catch (Exception e) {
                                // TODO: handle exception
                                e.printStackTrace();
                            }
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
                            } catch (Exception e) {
                                // TODO: handle exception
                                e.printStackTrace();
                            }
                            udpSend(respData);
                            try {
                                Thread.sleep(100);
                            } catch (Exception e) {
                                // TODO: handle exception
                                e.printStackTrace();
                            }
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
                            } catch (Exception e) {
                                // TODO: handle exception
                            }

                            Cmd[0] = FPGA_CMD_GETBRIGHT;
                            strCmd = new String(Cmd);
                            respondString += strCmd;        //cmd
                            respondString += strlength;    //length
                            Cmd[0] = (byte) g_LastBright;
                            strCmd = new String(Cmd);
                            respondString += strCmd;        //data
                            respondString += "#*";
                            try {
                                respData = respondString.getBytes("ISO-8859-1");
                            } catch (Exception e) {
                                // TODO: handle exception
                                e.printStackTrace();
                            }
                            udpSend(respData);
                            try {
                                Thread.sleep(100);
                            } catch (Exception e) {
                                // TODO: handle exception
                                e.printStackTrace();
                            }
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
                            } catch (Exception e) {
                                // TODO: handle exception
                                e.printStackTrace();
                            }
                            udpSend(respData);
                            try {
                                Thread.sleep(100);
                            } catch (Exception e) {
                                // TODO: handle exception
                                e.printStackTrace();
                            }

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
                            } catch (Exception e) {
                                // TODO: handle exception
                            }

                            Cmd[0] = FPGA_CMD_WRITESPI;
                            strCmd = new String(Cmd);
                            respondString += strCmd;        //cmd
                            respondString += strlength;    //length
                            //no data
                            respondString += "#*";
                            try {
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
                            } catch (Exception e) {
                                // TODO: handle exception
                            }

                            Cmd[0] = FPGA_CMD_OPENSRAMMODE;
                            strCmd = new String(Cmd);
                            respondString += strCmd;        //cmd
                            respondString += strlength;    //length
                            //no data
                            respondString += "#*";
                            try {
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
                            } catch (Exception e) {
                                // TODO: handle exception
                            }

                            Cmd[0] = FPGA_CMD_LINKWH;
                            strCmd = new String(Cmd);
                            respondString += strCmd;        //cmd
                            respondString += strlength;    //length
                            //no data
                            respondString += "#*";

                            //1:send data to fpga throught serial
                            try {
                                mOutputStream.write(data, 7, datalen);
                                Thread.sleep(50);
                            } catch (Exception e) {
                                // TODO: handle exception
                                e.printStackTrace();
                            }

                            String resString = "";
                            boolean ret;

                            //2 :read state from serial port
                            ret = checkFpgaRespond();
                            if (ret) {
                                //send respond to client pc
                                try {
                                    respData = resString.getBytes("ISO-8859-1");
                                } catch (Exception e) {
                                    // TODO: handle exception
                                }
                                udpSend(respData);
                            /*
                            try {
								mInputStream.read(rBuffer) ;
							} catch (Exception e) {
								// TODO: handle exception
								e.printStackTrace() ;
							}
							*/
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
                            } catch (Exception e) {
                                // TODO: handle exception
                            }

                            Cmd[0] = FPGA_CMD_LINKPOSADJ;
                            strCmd = new String(Cmd);
                            respondString += strCmd;        //cmd
                            respondString += strlength;    //length
                            //no data
                            respondString += "#*";

                            //1:send data to fpga throught serial
                            try {
                                mOutputStream.write(data, 7, datalen);
                                Thread.sleep(5);//
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            String resString = "";
                            boolean ret;

                            //2 :read state from serial port
                            ret = checkFpgaRespond();
                            if (ret) {
                                //send respond to client pc
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
                            } catch (Exception e) {
                                // TODO: handle exception
                            }

                            Cmd[0] = FPGA_CMD_LINKWHADJ;
                            strCmd = new String(Cmd);
                            respondString += strCmd;        //cmd
                            respondString += strlength;    //length
                            //no data
                            respondString += "#*";

                            //1:send data to fpga throught serial
                            try {
                                mOutputStream.write(data, 7, datalen);
                                Thread.sleep(5);
                            } catch (Exception e) {
                                // TODO: handle exception
                                e.printStackTrace();
                            }

                            String resString = "";
                            boolean ret;

                            //2 :read state from serial port
                            ret = checkFpgaRespond();
                            if (ret) {
                                //send respond to client pc
                                try {
                                    respData = resString.getBytes("ISO-8859-1");
                                } catch (Exception e) {
                                    // TODO: handle exception
                                }
                                udpSend(respData);
                                try {
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
                            String last = "";
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
                                Log.e(TAG, "vFPGA: "+last );
                            } catch (Exception e) {
                                // TODO: handle exception
                                e.printStackTrace();
                            }
                            udpSend(respData);
                        }
                        break;

                        case FPGA_CMD_UPDATE_FPGA:            //CMD_UPDATE_FPGA 77   FPGA
                            respondString = "*#";
                            respondString += Integer.toString(FPGA_CMD_UPDATE_FPGA);
                            respondString += "0000#*";
                            len = 0;

                            if (datalen == 27) {

                                if ((data[21 + 7] == '0') && (data[22 + 7] == '0') && (data[23 + 7] == '4') && (data[24 + 7] == '7')) {
                                    //clear input buff
                                    try {
                                        if (mInputStream.available() > 0)
                                            len = mInputStream.read(rBuffer);
                                        Thread.sleep(10);
                                    } catch (Exception e) {
                                        // TODO: handle exception
                                    }

                                    try {
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
                                    } catch (Exception e) {
                                        // TODO: handle exception
                                    }
                                    try {
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

                            // translate data to fpga
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
                                if ((rBuffer[0] == '%') && (rBuffer[1] == '#') && (rBuffer[2] == 'W') && (rBuffer[3] == 'L') && (rBuffer[4] == '#') && (rBuffer[5] == '%')) {

                                    break;
                                }
                                if ((rBuffer[0] == '%') && (rBuffer[1] == '#') && (rBuffer[2] == 'R') && (rBuffer[3] == 'L') && (rBuffer[4] == '#') && (rBuffer[5] == '%')) {

                                    break;
                                }
                                if (overtime_read >= 100) {
                                    break;
                                }
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

                            // 璇诲彇鐘舵��
                            try {
                                mOutputStream.write("*#RL 0038 0004#*".getBytes("ISO-8859-1"), 0, 16);
                                Thread.sleep(2);
                            } catch (Exception e) {
                                // TODO: handle exception
                                e.printStackTrace();
                            }

                            try {
                                if (mInputStream.available() > 0)
                                    len = mInputStream.read(rBuffer);
                            } catch (Exception e) {
                                // TODO: handle exception
                            }

                            if (rBuffer[8 + 6] == '3' && rBuffer[9 + 6] == 'B') {
                                System.out.println("(cmd=003B)");
                            } else {
                                System.out.println("(cmd=003B)......");
                                try {
                                    Thread.sleep(1000);
                                } catch (Exception e) {
                                    // TODO: handle exception
                                    e.printStackTrace();
                                }
                            }


                            if (len > 0) {
                                try {
                                    udpSend(respondString.getBytes("ISO-8859-1"));
                                } catch (Exception e) {
                                    // TODO: handle exception
                                }
                            } else {

                                try {
                                    mOutputStream.write("*#WL C800 000a 0bf4000000#*".getBytes("ISO-8859-1"), 0, 27);
                                    Thread.sleep(10);
                                } catch (Exception e) {
                                    // TODO: handle exception
                                    e.printStackTrace();
                                }

                            }

                            break;
                        case FPGA_CMD_GET_FPGA_STATE:
                            respondString = "*#";
                            respondString += Integer.toString(FPGA_CMD_GET_FPGA_STATE);
                            respondString += "10001#*";
                            len = 1;
                            int card_num = 0;

                            card_num = GetCardNum();        //now return only 1

                            System.out.println("recv cmd of CMD_GET_FPGA_STATE .......\n");

                            respondString = "*#";
                            btlen[0] = (byte) (card_num & 0x0ff);
                            btlen[1] = (byte) ((card_num >> 8) & 0x0ff);
                            btlen[2] = (byte) ((card_num >> 16) & 0x0ff);
                            btlen[3] = (byte) ((card_num >> 24) & 0x0ff);

                            String strlen = "";
                            try {
                                strlen = new String(btlen, "ISO-8859-1");
                                respondString += strlen;
                            } catch (Exception e) {
                                // TODO: handle exception
                            }
                            for (int i = 0; i < card_num; i++) {
                                respondString += "1";
                            }
                            respondString += "#*";

                            try {
                                respData = respondString.getBytes("ISO-8859-1");
                            } catch (Exception e) {
                                // TODO: handle exception
                            }
                            udpSend(respData);

                            break;

                        case UPDATE_FPGA:

                        {
                            {
                                byte[] updata = MyApplication.readFile(MyApplication.FPGApath, MyApplication.context);
                                if (null == updata) return;
                                //发送广播开始升级
                                Intent intent = new Intent("com.listen.action.fpga_start_update");
                                intent.putExtra("msg", "start");
                                MyApplication.context.sendBroadcast(intent, null);

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
                                            } catch (Exception e) {
                                                // TODO: handle exception
                                            }
//                                        Log.e(TAG, "rBuffer 0047: " + new String(rBuffer, 0, 10));
                                            try {
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
                                            } catch (Exception e) {
                                                // TODO: handle exception
                                            }
//                                        Log.e(TAG, "rBuffer 4700: " + new String(rBuffer, 0, 30));
                                            try {
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
//                                        System.out.println("(写入FPGA升级成功------)");
                                            sleep(5);
                                        } catch (Exception e) {
                                            // TODO: handle exception
//                                        System.out.println("(写入FPGA升级失败------)");
                                        }


                                        if ((rBuffer[0] == '%') && (rBuffer[1] == '#') && (rBuffer[2] == 'W') && (rBuffer[3] == 'L') && (rBuffer[4] == '#') && (rBuffer[5] == '%')) {
//                                        System.out.println("(FPGA回复升级WL------)");
                                            break;
                                        }
                                        if ((rBuffer[0] == '%') && (rBuffer[1] == '#') && (rBuffer[2] == 'R') && (rBuffer[3] == 'L') && (rBuffer[4] == '#') && (rBuffer[5] == '%')) {
//                                        System.out.println("(FPGA回复升级RL------)");
                                            break;
                                        }
                                        if (overtime_read >= 100) {
                                            System.out.println("(FPGA回复升级回复超时------)");
                                            break;
                                        }
                                    }
//                                Log.e(TAG, "rBuffer2dd2: " + new String(rBuffer, 0, 30));
                                    if (overtime_read >= 150) {
                                        try {
                                            mOutputStream.write("*#WL C800 000a 0bf4000000#*".getBytes("ISO-8859-1"), 0, 27);
                                            sleep(10);
                                            //发送广播升级失败
                                            Intent intent1 = new Intent("com.listen.action.fpga_update_status");
                                            intent1.putExtra("fpga", -1);
                                            MyApplication.context.sendBroadcast(intent1, null);
//                                            udpSend("*#000001#*".getBytes("ISO-8859-1"));
                                        } catch (Exception e) {
                                            // TODO: handle exception
                                            e.printStackTrace();
                                        }
                                        break;
                                    }

                                    try {
                                        mOutputStream.write("*#RL 0038 0004#*".getBytes("ISO-8859-1"), 0, 16);
                                    } catch (Exception e) {
                                        // TODO: handle exception
                                        e.printStackTrace();
                                    }
                                    for (int j = 0; j < 30; j++) {
                                        try {
                                            sleep(4);
                                            if (mInputStream.available() > 0)
                                                len = mInputStream.read(rBuffer);
                                        } catch (Exception e) {
                                            // TODO: handle exception
                                        }
                                        Log.e(TAG, "rBuffer 0038: " + new String(rBuffer, 0, 20));
                                        if (new String(rBuffer, 0, 50).contains("3B")) break;
                                    }
                                }
                                //发送完毕

                                try {
                                    sleep(3000);
                                    mOutputStream.write("*#WL C800 000a 0bf4000000#*".getBytes("ISO-8859-1"), 0, 27);
                                    sleep(3000);
                                    //发送广播，升级成功

                                    Intent intent0 = new Intent("com.listen.action.fpga_update_status");
                                    intent0.putExtra("fpga", 0);
                                    MyApplication.context.sendBroadcast(intent0, null);


//                                    udpSend("*#000000#*".getBytes("ISO-8859-1"));
                                } catch (Exception e) {
                                    // TODO: handle exception
                                    e.printStackTrace();
                                }
                                System.out.println("(发送完毕)......");
                            }
                        }

                        break;
                        case GET_FPGA_VERSION: {
                            String last = "";
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
                        }

                        break;
                        default:
                            break;
                    }
                }

            }
        }

    }

    public void udpSend(byte[] sendByte) {
        try {
            int cnt = sendByte.length;
            DatagramSocket ds = new DatagramSocket();
            Log.i("SocketInfo", "IP：" + dp.getAddress().getHostAddress() + "port:" + dp.getPort() + " cnt: " + cnt);
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
                    Log.d("mac", "interfaceName=" + iF.getName() + ", mac=" + mac);
                    if ("eth0".equalsIgnoreCase(iF.getName())) {
                        string = mac;
                        Log.i("get interface ", "eth0");
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
