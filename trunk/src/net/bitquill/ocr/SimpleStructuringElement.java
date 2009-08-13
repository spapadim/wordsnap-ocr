package net.bitquill.ocr;

import java.util.Arrays;

public class SimpleStructuringElement extends GrayMatrix implements StructuringElement {
    
    private int mAnchorX;
    private int mAnchorY;
    
    private int mMinX, mMaxX;
    private int mMinY, mMaxY;
    private int[] mDeltaX;
    private int[] mDeltaY;
    private int mNumNeighbors;
    
    private void initOffsets (int anchorX, int anchorY) {
        int width = mWidth;
        int height = mHeight;
        byte[] data = mData;
        mAnchorX = anchorX;
        mAnchorY = anchorY;
        
        // Count non-zeros
        int numNeighbors = 0;
        for (int i = 0;  i < height;  i++) {
            for (int j = 0;  j < width;  j++) {
                byte val = GrayMatrix.getByte(data, width, i, j);
                if (val != 0) {
                    ++numNeighbors;
                }
            }
        }
        mNumNeighbors = numNeighbors;
        
        // Compute offsets; use locals for efficiency
        int[] deltaX = new int[numNeighbors];
        int[] deltaY = new int[numNeighbors];
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        int n = 0;  // Neighbor offset array index
        for (int i = 0;  i < height;  i++) {
            for (int j = 0;  j < width;  j++) {
                byte val = GrayMatrix.getByte(data, width, i, j);
                if (val != 0) {
                    int dx = i - anchorX;
                    int dy = j - anchorY;
                    minX = Math.min(minX, dx);  maxX = Math.max(maxX, dx);
                    minY = Math.min(minY, dy);  maxY = Math.max(maxY, dy);
                    deltaX[n] = dx;
                    deltaY[n] = dy;
                    ++n;
                }
            }
        }
        // Copy back to class member fields
        mDeltaX = deltaX;
        mDeltaY = deltaY;
        mMinX = minX;  mMaxX = maxX;
        mMinY = minY;  mMaxY = maxY;
    }
    
    public SimpleStructuringElement (byte[] mask, int width, int height, int anchorX, int anchorY) {
        super(mask, width, height);
        initOffsets(anchorX, anchorY);
    }
    
    public SimpleStructuringElement (byte[] mask, int width, int height) {
        this(mask, width, height, width/2, height/2);
    }

    @Override
    public int getMaxX() {
        return mMaxX;
    }

    @Override
    public int getMaxY() {
        return mMaxY;
    }

    @Override
    public int getMinX() {
        return mMinX;
    }

    @Override
    public int getMinY() {
        return mMinY;
    }
    
    @Override
    public int getNumNeighbors () {
        return mNumNeighbors;
    }
    
    @Override
    public int[] getHorizontalOffsets () {
        return mDeltaY;
    }
    
    @Override
    public int[] getVerticalOffsets () {
        return mDeltaX;
    }

    @Override
    public int[] getLinearOffsets(int imgWidth, int imgHeight) {
        // Copy to locals for efficiency
        int numNeighbors = mNumNeighbors;
        int[] deltaX = mDeltaX;
        int[] deltaY = mDeltaY;
        
        int ofs[] = new int[numNeighbors];
        for (int ni = 0;  ni < numNeighbors;  ni++) {
            ofs[ni] = deltaX[ni] * imgWidth + deltaY[ni];
        }
        return ofs;
    }
    
    public static final SimpleStructuringElement createHorizontal (int radius) {
        int length = 2*radius + 1;
        byte[] mask = new byte[length];
        Arrays.fill(mask, (byte)1);
        return new SimpleStructuringElement(mask, length, 1);
    }
    
    // FIXME consolidate with createHorizontal ?
    public static final SimpleStructuringElement createVertical (int radius) {
        int length = 2*radius + 1;
        byte[] mask = new byte[length];
        Arrays.fill(mask, (byte)1);
        return new SimpleStructuringElement(mask, 1, length);
    }
}
