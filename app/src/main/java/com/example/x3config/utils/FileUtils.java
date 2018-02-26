package com.example.x3config.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by Android on 2018/1/3.
 */

public class FileUtils {
    /** 复制文件 */
    public static void copyFile(File fromFile, File toFile) {
        FileInputStream ins = null;
        FileOutputStream out = null;
        try {
            ins = new FileInputStream(fromFile);
            out = new FileOutputStream(toFile);
            byte[] b = new byte[1024];
            int n = 0;

            while ((n = ins.read(b)) != -1) {
                try {
                    out.write(b, 0, n);
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            ins.close();
            out.close();
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
    }
}
