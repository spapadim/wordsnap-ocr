package net.bitquill.ocr;

import android.app.Activity;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;

import java.io.FileOutputStream;
import java.io.IOException;

public class CaptureActivity extends Activity implements SurfaceHolder.Callback {
    private static final String TAG = CaptureActivity.class.getName();
    
    private static final long AUTO_FOCUS_INTERVAL = 1750L;  // in milliseconds
    
    private static int dumpCount = 1;  // FIXME temporary
    
    private SurfaceView mPreview;
    private boolean mHasSurface;
    private boolean mAutoFocusInProgress;
    private boolean mPreviewCaptureInProgress;
    private int mPreviewWidth, mPreviewHeight;
    private Camera mCamera;
   
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        requestWindowFeature(Window.FEATURE_NO_TITLE);      
    
        setContentView(R.layout.capture);
        mPreview = (SurfaceView)findViewById(R.id.capture_surface);
    }
    
    private void startCamera () {
        SurfaceHolder holder = mPreview.getHolder();
        if (mHasSurface) {
            Log.d(TAG, "startCamera after pause");
            // Resumed after pause, surface already exists
            surfaceCreated(holder);
            mCamera.startPreview();
            requestAutoFocus(); // Start autofocusing
        } else {
            Log.d(TAG, "startCamera from scratch");
            // Install a SurfaceHolder.Callback so we get notified when the
            // underlying surface is created and destroyed.
            holder.addCallback(this);
            holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }
    }
    
    private void stopCamera () {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }
    
    private void requestAutoFocus () {
        if (mAutoFocusInProgress) {
            return;
        }
        mAutoFocusInProgress = true;
        mCamera.autoFocus(new Camera.AutoFocusCallback() { 
            @Override
            public void onAutoFocus(boolean success, Camera camera) {
                mHandler.sendEmptyMessage(R.id.msg_auto_focus);
            }
        });
    }
    
    private void requestPreviewFrame () {
        if (mPreviewCaptureInProgress) {
            return;
        }
        mPreviewCaptureInProgress = true;
        mCamera.setOneShotPreviewCallback(new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                Message msg = mHandler.obtainMessage(R.id.msg_preview_frame, data);
                mHandler.sendMessage(msg);
            }
        });
    }
    
    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
        startCamera();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
        stopCamera();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_FOCUS) {
            requestAutoFocus();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_CAMERA) {
            requestPreviewFrame();
            return true;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, acquire the camera and tell it where
        // to draw.
        mCamera = Camera.open();
        try {
           mCamera.setPreviewDisplay(holder);
           Log.d(TAG, "surfaceCreated: setPreviewDisplay");
        } catch (IOException e) {
            Log.e(TAG, "Camera preview failed", e);
            mCamera.release();
            mCamera = null;
            // TODO: add more exception handling logic here
        }
        mHasSurface = true;
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        // Surface will be destroyed when we return, so stop the preview.
        // Because the CameraDevice object is not a shared resource, it's very
        // important to release it when the activity is paused.
        stopCamera();
        mHasSurface = false;
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // Size is known: set up camera parameters for preview size
        Camera.Parameters params = mCamera.getParameters();
        params.setPreviewSize(w, h);
        mCamera.setParameters(params);
        
        // Get parameters back; actual preview size may differ
        params = mCamera.getParameters();
        Size sz = params.getPreviewSize();
        mPreviewWidth = sz.width;
        mPreviewHeight = sz.height;

        // Start preview
        mCamera.startPreview();
        requestAutoFocus();
        
        Log.d(TAG, "surfaceChanged: startPreview");
    }
    
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case R.id.msg_auto_focus:
                // TODO
                mAutoFocusInProgress = false;
                break;
            case R.id.msg_preview_frame:
                //mPreviewCaptureInProgress = false;
                final byte[] yuv = (byte[])msg.obj;
                Thread t = new Thread() {
                    @Override
                    public void run() {
                        // TODO Auto-generated method stub

                        try {
                            // Dump raw camera input
                            FileOutputStream os = new FileOutputStream("/sdcard/in_dump" + dumpCount + ".yuv");
                            os.write(yuv);
                            os.close();
                        } catch (IOException e) { }


                        GrayImage img = new GrayImage(yuv, mPreviewWidth, mPreviewHeight);

                        // Temporary timing test
                        Log.i(TAG, "Start mean filtering");
                        long startTime = System.currentTimeMillis();
                        GrayImage meanImg = img.meanFilter(10);
                        Log.i(TAG, "meanFilter time: " + (System.currentTimeMillis() - startTime));

                        try {
                            // Dump mean filter output
                            FileOutputStream os = new FileOutputStream("/sdcard/mean_dump" + dumpCount + ".gray");
                            os.write(meanImg.getData());
                            os.close();
                        } catch (IOException e) { }

                        // mean & variance
                        Log.i(TAG, "Start mean");
                        startTime = System.currentTimeMillis();
                        float totalMean = img.mean();
                        Log.i(TAG, "mean time: " + (System.currentTimeMillis() - startTime) + " (value " + totalMean + ")");
                        startTime = System.currentTimeMillis();
                        float imgVariance = img.variance();
                        Log.i(TAG, "variance time: " + (System.currentTimeMillis() - startTime) + "(value " + imgVariance + ")");

                        // hist
                        Log.i(TAG, "Start new histogram");
                        startTime = System.currentTimeMillis();
                        int[] hist = img.histogram();
                        Log.i(TAG, "histogram time: " + (System.currentTimeMillis() - startTime));
                        float mean = 0;
                        int count = 0;
                        for (int i = 0; i < 256; i++) {
                            mean += i*hist[i];
                            count += hist[i];
                        }
                        mean /= count;
                        float var = 0;
                        for (int i = 0;  i < 256;  i++) {
                            var += hist[i]*(i - mean)*(i - mean);
                        }
                        var /= count;
                        Log.i(TAG, "count=" + count + " w*h=" + (mPreviewWidth*mPreviewHeight) + " mean=" + mean);
                        Log.i(TAG, "var=" + var + " stdev=" + Math.sqrt(var));
                        
                        // threshold
                        Log.i(TAG, "Start thresholding");
                        startTime = System.currentTimeMillis();
                        int threshOffset = (int)(0.1 * totalMean);
                        meanImg = img.adaptiveThreshold((byte)255, (byte)0, threshOffset, meanImg, meanImg);
                        Log.i(TAG, "thresholding time: " + (System.currentTimeMillis() - startTime));
                        
                        try {
                            // Dump thresholded output
                            FileOutputStream os = new FileOutputStream("/sdcard/bin_dump" + dumpCount + ".gray");
                            os.write(meanImg.getData());
                            os.close();
                        } catch (IOException e) { }
                                                
                        //Log.i(TAG, "Start ARGB conversion");
                        //startTime = System.currentTimeMillis();
                        //int[] tmp = new int[mPreviewWidth * mPreviewHeight];
                        //Log.i(TAG, "Allocated buffer in " + (System.currentTimeMillis() - startTime));
                        //startTime = System.currentTimeMillis();
                        //Bitmap b = meanImg.asBitmap();
                        //Log.i(TAG, "Conversion time: " + (System.currentTimeMillis() - startTime));
                        
                        ++dumpCount;

                        mPreviewCaptureInProgress = false; // FIXME - move back up
                    }
                };
                t.start();
                break;
            default:
                super.handleMessage(msg);
            }
        }
    };
}