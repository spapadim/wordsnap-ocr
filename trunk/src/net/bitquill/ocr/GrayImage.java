package net.bitquill.ocr;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.Bitmap.Config;
import android.util.Log;

public class GrayImage extends GrayMatrix {
    
    private static final String TAG = "GrayImage";
    
    static {
        try {
            System.loadLibrary("imageproc");
        } catch (UnsatisfiedLinkError ule) {
            Log.e(TAG, "Could not load native library imageproc", ule);
        }   
    }
    
    protected GrayImage (int width, int height) {
        super(width, height);
    }
    
    public GrayImage (byte[] data, int width, int height) {
        super(data, width, height);
    }
    
    public GrayImage (GrayImage other) {
        super(other);
    }
    
    final public float mean () {
        return GrayImage.nativeMean(mData, mWidth, mHeight);
    }
    
    final public float variance () {
        return GrayImage.nativeVariance(mData, mWidth, mHeight);
    }
    
    final public int[] histogram (int[] hist) {
        GrayImage.nativeHistogram(mData, mWidth, mHeight, hist);
        return hist;
    }
    
    final public int[] histogram () {
        return histogram(new int[256]);
    }
    
    public GrayImage erode (StructuringElement strel, GrayImage dest) {
        if (dest.mWidth != mWidth || dest.mHeight != mHeight) {
            throw new IllegalArgumentException("Destination image size must match");
        }
        GrayImage.nativeErode(mData, dest.mData, mWidth, mHeight, 
                strel.getNumNeighbors(), 
                strel.getHorizontalOffsets(), strel.getVerticalOffsets(), 
                strel.getLinearOffsets(mWidth, mHeight), 
                strel.getMinX(), strel.getMaxX(), 
                strel.getMinY(), strel.getMaxY());
        return dest;
    }
    
    public GrayImage erode (StructuringElement strel) {
        return erode(strel, new GrayImage(mWidth, mHeight));
    }
    
    public GrayImage dilate (StructuringElement strel, GrayImage dest) {
        GrayImage.nativeDilate(mData, dest.mData, mWidth, mHeight, 
                strel.getNumNeighbors(), 
                strel.getHorizontalOffsets(), strel.getVerticalOffsets(), 
                strel.getLinearOffsets(mWidth, mHeight), 
                strel.getMinX(), strel.getMaxX(), 
                strel.getMinY(), strel.getMaxY());
        return dest;
    }
    
    public GrayImage dilate (StructuringElement strel) {
        return dilate(strel, new GrayImage(mWidth, mHeight));
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
    
    native private static void nativeGrayToARGB (byte[] in, int imgWidth, int imgHeight, int[] out, int left, int top, int width, int height);
    
    native private static float nativeMean (byte[] in, int width, int height);
    native private static float nativeVariance (byte[] in, int width, int height);
    
    native private static void nativeHistogram (byte[] in, int width, int height, int[] hist);
    
    native private static void nativeErode (byte[] in, byte[] out, int width, int height, 
            int numNeighbors, int[] hOffsets, int vOffsets[], int linearOffsets[],
            int minX, int maxX, int minY, int maxY);
    native private static void nativeDilate (byte[] in, byte[] out, int width, int height, 
            int numNeighbors, int[] hOffsets, int vOffsets[], int linearOffsets[],
            int minX, int maxX, int minY, int maxY);

    static public void decode (byte[] yuv, int width, int height) {
        byte[] mean = new byte[width * height];
        nativeMeanFilter(yuv, mean, width, height, 10);
    }
    
}