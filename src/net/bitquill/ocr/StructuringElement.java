package net.bitquill.ocr;

public interface StructuringElement {

    public int getWidth ();
    public int getHeight ();
    
    public int getMinX ();
    public int getMaxX ();
    public int getMinY ();
    public int getMaxY ();
    
    public int getNumNeighbors ();
    public int[] getHorizontalOffsets ();
    public int[] getVerticalOffsets ();
    
    public int[] getLinearOffsets (int imgWidth, int imgHeight);
}
