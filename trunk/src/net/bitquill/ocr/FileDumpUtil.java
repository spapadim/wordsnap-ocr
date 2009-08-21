package net.bitquill.ocr;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import net.bitquill.ocr.image.GrayImage;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.util.Log;

/**
 * Simple utility class to dump image data for later debugging.
 */
public class FileDumpUtil {
    
    private static final String TAG = "FileDumpUtil";
    private static final File sDumpDirectory = new File("/sdcard/net.bitquill.ocr");
    
    synchronized static public void init () {
        // Create directory, if necessary
        if (!sDumpDirectory.exists()) {
            sDumpDirectory.mkdirs();
        }
    }
        
    synchronized static public void dump (String prefix, GrayImage img) {
        FileOutputStream os = null;
        try {
            long timestamp = System.currentTimeMillis();
            File dumpFile = new File(sDumpDirectory, prefix + timestamp + ".gray");
            os = new FileOutputStream(dumpFile);
            os.write(img.getData());
        } catch (IOException ioe) {
            Log.e(TAG, "GrayImage dump failed", ioe);
        } finally {
            try {
                os.close();
            } catch (Throwable t) {
                // Ignore
            }
        }
    }
    
    synchronized static public void dump (String prefix, Bitmap img) {
        FileOutputStream os = null;
        try {
            long timestamp = System.currentTimeMillis();
            File dumpFile = new File(sDumpDirectory, prefix + timestamp + ".png");
            os = new FileOutputStream(dumpFile);
            img.compress(CompressFormat.PNG, 100, os);
        } catch (IOException ioe) {
            Log.e(TAG, "GrayImage dump failed", ioe);
        } finally {
            try {
                os.close();
            } catch (Throwable t) {
                // Ignore
            }
        }        
    }
}
