package net.bitquill.ocr;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import android.util.Log;

public class Decoder {
    
    private static final String TAG = "Decoder";
    
    static {
        try {
            System.loadLibrary("ocr");
        } catch (UnsatisfiedLinkError ule) {
            Log.e(TAG, "Could not load native library ocr", ule);
        }   
    }

    static private final int getPixel (byte[] data, int width, int i, int j) {
	return data[i*width + j] & 0xFF;
    }

    static private final void setPixel (byte[] data, int width, int i, int j, byte value) {
	data[i*width + j] = value;
    }

    static private final boolean threshold (int value, int mean, int offset) {
        return (mean - value < offset);
    }
    
    static private final int rowSumIncDec (int[] sum, 
            int rowInc, int rowDec, 
            byte[] data, int width, int height, int radius) {
        
        int total = 0;  // Total of rowInc-th row entries
        int deltaInc = 0, deltaDec = 0;

        // Handle first element
        for (int sj = 0;  sj <= radius;  sj++) {
            if (rowInc < height) {
                int val = getPixel(data, width, rowInc, sj);
                deltaInc += val;
                total += val;  // Increment here, so we read off pixel only once
            }
            if (rowDec >= 0) {
                deltaDec += getPixel(data, width, rowDec, sj);
            }
        }
        sum[0] += deltaInc - deltaDec;
        
        // Incrementally deal with remaining elements
        // Broken into three for loops to avoid if statements inside loop
        for (int j = 1;  j < radius + 1;  j++) {
            // Left edge
            if (rowInc < height) {
                deltaInc += getPixel(data, width, rowInc, j+radius);
            }
            if (rowDec >= 0) {
                deltaDec += getPixel(data, width, rowDec, j+radius);
            }
            sum[j] += deltaInc - deltaDec;
        }
        for (int j = radius + 1;  j < width - radius;  j++) {
            // Internal
            if (rowInc < height) {
                deltaInc -= getPixel(data, width, rowInc, j-radius-1);
                int val = getPixel(data, width, rowInc, j+radius);
                deltaInc += val;
                total += val; // Increment here, so we read off pixel only once
            }
            if (rowDec >= 0) {
                deltaDec -= getPixel(data, width, rowDec, j-radius-1);
                deltaDec += getPixel(data, width, rowDec, j+radius);
            }
            sum[j] += deltaInc - deltaDec;
        }
        for (int j = width - radius;  j < width;  j++) {
            // Right edge
            if (rowInc < height) {
                deltaInc -= getPixel(data, width, rowInc, j-radius-1);
            }
            if (rowDec >= 0) {
                deltaDec -= getPixel(data, width, rowDec, j-radius-1);
            }
            sum[j] += deltaInc - deltaDec;
        }
        
        return total;
    }
    
    static private final void avgRow (int[] sum, int row, 
            byte[] data, int width, int height, int radius) {
        // Compute clipped height
        int h = Math.min(height-1, row+radius) - Math.max(0, row-radius) + 1;
        for (int j = 0;  j < width;  j++) {
            // Compute clipped width
            int w = Math.min(width-1, j+radius) - Math.max(0, j-radius) + 1;
            setPixel(data, width, row, j, (byte)(sum[j]/(w*h)));
        }
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
     * @param out Output binary image
     * @param width  Width of input image
     * @param height Height of input image
     * @param radius Radius of mean filter; diameter is 2*radius + 1
     * @return Overall image intensity mean
     */
    static public int meanFilter0 (byte[] in, byte[] out, int width, int height, int radius) {
        int total = 0;  // overall sum
        int[] sums = new int[width];
        
        // Fill in sums for pixel (0,0)
        total += getPixel(in, width, 0, 0);
        sums[0] = 0;
        for (int di = 0;  di <= radius;  di++) {
            for (int dj = 0;  dj <= radius;  dj++) {
                sums[0] += getPixel(in, width, di, dj);
            }
        }
        
        // Fill in sum for remaining first-row pixels (0,j), 1<=j<width
        for (int j = 1;  j < width;  j++) {
            total += getPixel(in, width, 0, j);
            sums[j] = sums[j-1];
            for (int di = 0;  di <= radius;  di++) {
                if (j - radius - 1 >= 0) {
                    sums[j] -= getPixel(in, width, di, j-radius-1);
                }
                if (j + radius < width) {
                    sums[j] += getPixel(in, width, di, j+radius);
                }
            }
        }
        
        // Fill in remaining rows
        for (int i = 1;  i <= height;  i++) {
            // Flush current row, i-1
            for (int j = 0;  j < width;  j++) {
                // Compute clipped width and height
                int wij = Math.min(width-1, j+radius) - Math.max(0, j-radius) + 1;
                int hij = Math.min(height-1, i+radius) - Math.max(0, i-radius) + 1;
                setPixel(out, width, i-1, j, (byte) (sums[j] / (wij*hij)));

                // Slide window downwards
                if (i < height) {
                    total += getPixel(in, width, i, j);
                    int jmin = Math.max(0, j-radius);
                    int jmax = Math.min(width-1, j+radius);
                    for (int sj = jmin;  sj <= jmax;  sj++) {
                        if (i - radius - 1 >= 0) {
                            sums[j] -= getPixel(in, width, i-radius-1, sj);
                        }
                        if (i + radius < height) {
                            sums[j] += getPixel(in, width, i+radius, sj);
                        }
                    }
                }
            }
        }

        return total/(width*height);
    }
    
    static public int meanFilter1 (byte[] in, byte[] out, int width, int height, int radius) {
        int total = 0;
        int[] sum = new int[width];

        for (int si = 0;  si <= radius;  si++) {
            total += rowSumIncDec(sum, si, -1, in, width, height, radius);
        }
        avgRow(sum, 0, out, width, height, radius);
        
        for (int i = 1;  i < height; i++) {
            total += rowSumIncDec(sum, i+radius, i-radius-1, in, width, height, radius);
            avgRow(sum, i, out, width, height, radius);
        }
        
        return total / (width*height);
    }

    static public int naiveMeanFilter (byte[] in, byte[] out, int width, int height, int radius) {
        int total = 0;
        for (int i = 0;  i < height;  i++) {
            for (int j = 0;  j < width;  j++) {
                int imin = Math.max(0, i-radius), imax = Math.min(height-1, i+radius);
                int jmin = Math.max(0, j-radius), jmax = Math.min(width-1, j+radius);
                int sum = 0;
                for (int si = imin;  si <= imax;  si++) {
                    for (int sj = jmin;  sj <= jmax;  sj++) {
                        sum += getPixel(in, width, si, sj);
                    }
                }
                setPixel(out, width, i, j, (byte)(sum/((imax-imin+1)*(jmax-jmin+1))));
                total += getPixel(in, width, i, j);
            }
        }
        return total/(width*height);
    }
    
    native static public int nativeNaiveMeanFilter (byte[] in, byte[] out, int width, int height, int radius);
    native static public int nativeMeanFilter1 (byte[] in, byte[] out, int width, int height, int radius);
    
    static public void decode (byte[] yuv, int width, int height) {
        byte[] mean = new byte[width * height];
        meanFilter0(yuv, mean, width, height, 10);
    }
    
    static public void main (String[] args) throws IOException {
        int width = 480, height = 320;
        byte[] yuv = new byte[width*height];
        byte[] out = new byte[width*height];
        
        // Read raw data
        FileInputStream is = new FileInputStream(args[0]);
        if (is.read(yuv) != width * height) {
            throw new IOException("Incomplete read");
        }
        is.close();
        
        // Run filter
        long startTime = System.currentTimeMillis();
        int totalAvg = meanFilter0(yuv, out, width, height, 10);
        //int totalAvg = naiveMeanFilter(yuv, out, width, height, 10);
        System.err.println("meanFilter time: " + (System.currentTimeMillis() - startTime));
        System.err.println("image average: " + totalAvg);

        // Dump output
        FileOutputStream os = new FileOutputStream("mean_" + args[0]);
        os.write(out);
        os.close();
        
        // Binarize based on adaptive threshold
        int threshOffset = (int)(0.1 * totalAvg);
        for (int i = 0;  i < height;  i++) {
            for (int j = 0;  j < width;  j++) {
                setPixel(out, width, i, j,
                        threshold(getPixel(yuv, width, i, j), getPixel(out, width, i, j), threshOffset) ? 
                                (byte)255 : (byte)0);
            }
        }
        
        // Dump output
        os = new FileOutputStream("bin_" + args[0]);
        os.write(out);
        os.close();
    }
}
