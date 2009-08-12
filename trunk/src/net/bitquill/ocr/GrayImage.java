package net.bitquill.ocr;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.Bitmap.Config;
import android.util.Log;

public class GrayImage {
    
    private static final String TAG = "PreviewImage";
    
    static {
        try {
            System.loadLibrary("ocr");
        } catch (UnsatisfiedLinkError ule) {
            Log.e(TAG, "Could not load native library ocr", ule);
        }   
    }
    
    private byte[] mData;
    private int mWidth;
    private int mHeight;
    
    // TODO - decide method scope
    protected GrayImage (int width, int height) {
        mWidth = width;
        mHeight = height;
        mData = new byte[width * height];
    }

    public GrayImage (byte[] data, int width, int height) {
        if (data.length < width * height) {
            throw new IllegalArgumentException("Image data array is too short");
        }
        mData = data;
        mWidth = width;
        mHeight = height;
    }
    
    /**
     * Copy constructor.
     * @param other  PreviewImage to copy from.
     */
    public GrayImage (GrayImage other) {
        int width = other.mWidth;
        int height = other.mHeight;
        mWidth = width;
        mHeight = height;
        mData = new byte[width*height];
        System.arraycopy(other.mData, 0, mData, 0, width * height);
    }
    
    public final int getWidth () {
        return mWidth;
    }
    
    public final int getHeight () {
        return mHeight;
    }
    
    public final int getPixel (int i, int j) {
        return GrayImage.getPixel(mData, mWidth, i, j);
    }
    
    public final void setPixel (int i, int j, byte value) {
        GrayImage.setPixel(mData, mWidth, i, j, value);
    }
    
    public final void getRow (int i, byte[] values) {
        int width = mWidth;
        System.arraycopy(mData, i*width, values, 0, width);
    }
    
    // FIXME - remove this method??
    public final byte[] getData () {
        return mData;
    }
    
    public int mean () {
        return GrayImage.nativeMean(mData, mWidth, mHeight);
    }
    
    public GrayImage meanFilter (int radius, GrayImage dest) {
        if (dest.mWidth != mWidth || dest.mHeight != mHeight) {
            throw new IllegalArgumentException("Destination image size must match");
        }
        GrayImage.nativeMeanFilter(mData, dest.mData, mWidth, mHeight, radius);
        return dest;
    }
    
    public GrayImage meanFilter (int radius) {
        return meanFilter(radius, new GrayImage(mWidth, mHeight));
    }
    
    public GrayImage adaptiveThreshold (byte hi, byte lo, int offset, GrayImage thresh, GrayImage dest) {
        int width = mWidth;
        int height = mHeight;
        if (thresh.mWidth != width || thresh.mHeight != height) {
            throw new IllegalArgumentException("Threshold image size must match");
        }
        if (dest.mWidth != width || dest.mHeight != height) {
            throw new IllegalArgumentException("Destination image size must match");            
        }
        GrayImage.nativeAdaptiveThreshold(mData, thresh.mData, dest.mData, width, height, hi, lo, offset);
        return dest;
    }
    
    public Bitmap asBitmap (int left, int top, int width, int height, int[] buf) {
        if (buf == null) {
            throw new NullPointerException("Buffer is null");
        }
        int imgWidth = mWidth;
        int imgHeight = mHeight;
        nativeGrayToARGB(mData, imgWidth, imgHeight, buf, left, top, width, height);
        Bitmap b = Bitmap.createBitmap(buf, width, height, Config.ARGB_8888);
        return b;        
    }
    
    public Bitmap asBitmap (int left, int top, int width, int height) {
        return asBitmap(left, top, width, height, new int[width*height]);
    }
    
    public Bitmap asBitmap (Rect roi, int[] buf) {
        return asBitmap(roi.left, roi.top, roi.width(), roi.height(), buf);
    }
    
    public Bitmap asBitmap (Rect roi) {
        return asBitmap(roi.left, roi.top, roi.width(), roi.height());
    }
    
    public Bitmap asBitmap (int[] buf) {
        return asBitmap(0, 0, mWidth, mHeight, buf);
    }
    
    public Bitmap asBitmap () {
        return asBitmap(0, 0, mWidth, mHeight);
    }
     
    private static final int getPixel (byte[] data, int width, int i, int j) {
        return data[i*width + j] & 0xFF;
    }

    private static final void setPixel (byte[] data, int width, int i, int j, byte value) {
        data[i*width + j] = value;
    }
        
    /**
     * Adaptive mean filter for grayscale image, using a square filter of given radius.
     * 
     * Does not perform any parameter checking, so you must ensure that:
     * (i) radius does not exceed width or height;
     * (ii) 256*(2*radius+1)^2 fits in a 32-bit integer;
     * (iii) the output byte array is sufficiently large.
     * 
     * @param in  Input grayscale image
     * @param out Output filtered grayscale image
     * @param width  Width of input image
     * @param height Height of input image
     * @param radius Radius of mean filter; diameter is 2*radius + 1
     * @return Overall image intensity mean
     */    
    native private static void nativeMeanFilter (byte[] in, byte[] out, int width, int height, int radius);
    
    native private static void nativeAdaptiveThreshold (byte[] in, byte[] thresh, byte[] out, int width, int heigth, byte hi, byte lo, int offset);
    
    native private static int nativeMean (byte[] in, int width, int height);
    
    native private static void nativeGrayToARGB (byte[] in, int imgWidth, int imgHeight, int[] out, int left, int top, int width, int height);
    
    static public void decode (byte[] yuv, int width, int height) {
        byte[] mean = new byte[width * height];
        nativeMeanFilter(yuv, mean, width, height, 10);
    }
    
}