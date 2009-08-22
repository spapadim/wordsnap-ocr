/**
 * Copyright 2009 Spiros Papadimitriou <spapadim@cs.cmu.edu>
 * 
 * This file is part of WordSnap OCR.
 * 
 * WordSnap is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * WordSnap is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with WordSnap.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.bitquill.ocr;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;

import net.bitquill.ocr.image.GrayImage;
import net.bitquill.ocr.image.SimpleStructuringElement;
import net.bitquill.ocr.weocr.WeOCRClient;

public class WordCaptureActivity extends Activity implements SurfaceHolder.Callback {
    private static final String TAG = WordCaptureActivity.class.getSimpleName();
    
    private static final int TOUCH_BORDER = 20;  // How many pixels to ignore around edges
    private static final long AUTOFOCUS_MAX_WAIT_TIME = 2000L;  // How long to wait for touch-triggered AF to succeed
    
    private static final int MENU_SETTINGS_ID = Menu.FIRST;
    private static final int MENU_ABOUT_ID = Menu.FIRST + 1;
        
    private SurfaceView mPreview;
    private boolean mHasSurface;
    private int mPreviewWidth, mPreviewHeight;
    private Camera mCamera;
    private boolean mCameraPreviewing;

    private boolean mAutoFocusInProgress;
    private boolean mPreviewCaptureInProgress;
    private boolean mProcessingInProgress;

    private static final int AUTOFOCUS_UNKNOWN = 0;
    private static final int AUTOFOCUS_SUCCESS = 1;
    private static final int AUTOFOCUS_FAILURE = 2;
    private int mAutoFocusStatus;
    
    private TextView mStatusText;
    private TextView mResultText;
    
    private LinearLayout mButtonGroup;
    private Button mWebSearchButton;
    private Button mDictionaryButton;
    private Button mClipboardButton;
    
    private ClipboardManager mClipboardManager;
    
    private boolean mEnableDump;
   
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        requestWindowFeature(Window.FEATURE_NO_TITLE);      
    
        setContentView(R.layout.capture);
        
        mClipboardManager = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        
        mPreview = (SurfaceView)findViewById(R.id.capture_surface);
        
        mPreview.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                float x = event.getX();
                float y = event.getY();
                //int action = event.getAction();
                
                if (event.getEdgeFlags() == 0 &&
                        x > TOUCH_BORDER && y > TOUCH_BORDER &&
                        x < mPreviewWidth - TOUCH_BORDER &&
                        y < mPreviewHeight - TOUCH_BORDER) {
                    long timeSinceDown = event.getEventTime() - event.getDownTime();
                    if (mAutoFocusInProgress || mProcessingInProgress) {
                        return false;
                    }
                    if (mAutoFocusStatus == AUTOFOCUS_SUCCESS || timeSinceDown > AUTOFOCUS_MAX_WAIT_TIME) {
                        mButtonGroup.setVisibility(View.GONE);
                        requestPreviewFrame();
                    } else {
                        requestAutoFocus();
                    }
                    return true;
                }
                return false;
            }
        });
        
        mStatusText = (TextView)findViewById(R.id.status_text);
        mResultText = (TextView)findViewById(R.id.result_text);
        mResultText.setVisibility(View.INVISIBLE);
        
        mButtonGroup = (LinearLayout)findViewById(R.id.button_group);
        mWebSearchButton = (Button)findViewById(R.id.web_search_button);
        mDictionaryButton = (Button)findViewById(R.id.dictionary_button);
        mClipboardButton = (Button)findViewById(R.id.clipboard_button);
        mButtonGroup.setVisibility(View.GONE);
        mWebSearchButton.setOnClickListener(new View.OnClickListener () {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_WEB_SEARCH);
                intent.putExtra(SearchManager.QUERY, mResultText.getText());
                startActivity(intent);
                finish();
            }
        });
        mDictionaryButton.setOnClickListener(new View.OnClickListener () {
            @Override
            public void onClick(View v) {
                Uri url = Uri.parse("http://en.m.wikipedia.org/wiki?search=" + mResultText.getText());
                Intent intent = new Intent(Intent.ACTION_VIEW, url);
                startActivity(intent);
                finish();
            }
        });
        mClipboardButton.setOnClickListener(new View.OnClickListener () {
            @Override
            public void onClick(View v) {
                mClipboardManager.setText(mResultText.getText());
                finish();
            }
        });
    }
    
    private void loadPreferences () {
        SharedPreferences preferences = 
            PreferenceManager.getDefaultSharedPreferences(this);
        mEnableDump = preferences.getBoolean(OCRPreferences.PREF_DEBUG_DUMP, false);
        if (mEnableDump) {
            FileDumpUtil.init();
        }
    }
    
    private void startCamera () {
        SurfaceHolder holder = mPreview.getHolder();
        if (mHasSurface) {
            Log.d(TAG, "startCamera after pause");
            // Resumed after pause, surface already exists
            surfaceCreated(holder);
            startCameraPreview();
        } else {
            Log.d(TAG, "startCamera from scratch");
            // Install a SurfaceHolder.Callback so we get notified when the
            // underlying surface is created and destroyed.
            holder.addCallback(this);
            holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }
    }
    
    private void startCameraPreview () {
        if (!mCameraPreviewing) {
            mCamera.startPreview();
            mCameraPreviewing = true;
            //requestAutoFocus();  // Do one autofocus
        }
    }
    
    private void stopCameraPreview () {
        if (mCameraPreviewing) {
            mCamera.stopPreview();
            mCameraPreviewing = false;
        }
    }
    
    private void stopCamera () {
        if (mCamera != null) {
            stopCameraPreview();
            mCamera.release();
            mCamera = null;
        }
    }
    
    private void requestAutoFocus () {
        if (mAutoFocusInProgress) {
            return;
        }
        mAutoFocusStatus = AUTOFOCUS_UNKNOWN;
        mAutoFocusInProgress = true;
        mCamera.autoFocus(new Camera.AutoFocusCallback() { 
            @Override
            public void onAutoFocus(boolean success, Camera camera) {
                Message msg = mHandler.obtainMessage(R.id.msg_auto_focus, 
                        success ? 1 : AUTOFOCUS_SUCCESS, AUTOFOCUS_FAILURE);
                mHandler.sendMessage(msg);
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
    
    private void initStateVariables () {
        mAutoFocusStatus = AUTOFOCUS_UNKNOWN;
        mAutoFocusInProgress = false;
        mPreviewCaptureInProgress = false;
        mProcessingInProgress = false;
    }
    
    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        loadPreferences();
        initStateVariables();
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
    public boolean onCreateOptionsMenu(Menu menu) {
      super.onCreateOptionsMenu(menu);
      menu.add(0, MENU_SETTINGS_ID, 0, R.string.menu_settings)
          .setIcon(android.R.drawable.ic_menu_preferences);
      menu.add(0, MENU_ABOUT_ID, 0, R.string.menu_about)
          .setIcon(android.R.drawable.ic_menu_info_details);
      return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_FOCUS) {
            if (event.getRepeatCount() == 0) {
                mButtonGroup.setVisibility(View.GONE);
                requestAutoFocus();
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_CAMERA) {
            if (event.getRepeatCount() == 0) {
                requestPreviewFrame();
            }
            return true;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_SETTINGS_ID:
            startActivity(new Intent(this, OCRPreferences.class));
            return true;
        case MENU_ABOUT_ID:
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.about_url))));
            return true;
        }
        return super.onOptionsItemSelected(item);
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

        startCameraPreview();
        
        Log.d(TAG, "surfaceChanged: startPreview");
    }
    
    private final Handler mHandler = new Handler () {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case R.id.msg_auto_focus:
                mAutoFocusStatus = msg.arg1;
                mAutoFocusInProgress = false;
                break;
            case R.id.msg_preview_frame:
                //mPreviewCaptureInProgress = false;
                final byte[] yuv = (byte[])msg.obj;
                final Thread preprocessThread = new Thread() {
                    @Override
                    public void run() {
                        GrayImage img = new GrayImage(yuv, mPreviewWidth, mPreviewHeight);
                        long startTime = System.currentTimeMillis();
                        Rect ext = makeTargetRect();
                        GrayImage binImg = findWordExtent(img, ext);
                        Log.d(TAG, "Find word extent in " + (System.currentTimeMillis() - startTime) + " msec");
                        Log.d(TAG, "Extent is " + ext.top + "," + ext.left + "," + ext.bottom + "," + ext.right);

                        if (mEnableDump) {
                            FileDumpUtil.dump("bin", binImg);
                        }

                        startTime = System.currentTimeMillis();
                        Bitmap textBitmap = binImg.asBitmap(ext);
                        Log.d(TAG, "Converted to Bitmap in " + (System.currentTimeMillis() - startTime) + " msec");
                        
                        if (mEnableDump) {
                            FileDumpUtil.dump("word", textBitmap);
                        }

                        mPreviewCaptureInProgress = false; // FIXME - move back up
                        
                        Message msg = mHandler.obtainMessage(R.id.msg_text_bitmap, textBitmap);
                        mHandler.sendMessage(msg);
                    }
                };
                mButtonGroup.setVisibility(View.GONE);
                mResultText.setVisibility(View.INVISIBLE);
                mStatusText.setText(R.string.status_preprocessing_text);
                mProcessingInProgress = true;
                preprocessThread.start();
                break;
            case R.id.msg_text_bitmap:
                final Bitmap textBitmap = (Bitmap)msg.obj;
                final WeOCRClient weOCRClient = OCRApplication.getOCRClient();
                final Thread ocrThread = new Thread() {
                    @Override
                    public void run () {
                        synchronized (weOCRClient) {
                            try {
                                String ocrText = weOCRClient.doOCR(textBitmap);
                                Message msg = mHandler.obtainMessage(R.id.msg_ocr_result, ocrText);
                                mHandler.sendMessage(msg);
                            } catch (IOException ioe) {
                                // TODO
                                Log.e(TAG, "WeOCR failed", ioe);
                                mHandler.sendEmptyMessage(R.id.msg_ocr_fail);
                            }
                        }
                    }
                };
                mStatusText.setText(R.string.status_processing_text);
                ocrThread.start();
                break;
            case R.id.msg_ocr_result:
                final String ocrText = (String)msg.obj;
                Log.i(TAG, "OCR result text: " + ocrText);
                // Toast fails from this thread
                //Toast.makeText(WordCaptureActivity.this, "OCR result: " + ocrText, Toast.LENGTH_LONG)
                //     .show();
                mStatusText.setText(R.string.status_finished_text);
                mResultText.setText(ocrText);
                mResultText.setVisibility(View.VISIBLE);
                mButtonGroup.setVisibility(View.VISIBLE);
                mProcessingInProgress = false;
                mAutoFocusStatus = AUTOFOCUS_UNKNOWN;
                mHandler.sendEmptyMessageDelayed(R.id.msg_reset_status, 2000L);
                break;
            case R.id.msg_ocr_fail:
                mStatusText.setText(R.string.status_processing_error_text);
                mProcessingInProgress = false;
                mAutoFocusStatus = AUTOFOCUS_UNKNOWN;
                //mHandler.sendEmptyMessageDelayed(R.id.msg_reset_status, 5000L);
                break;
            case R.id.msg_reset_status:
                //mResultText.setVisibility(View.INVISIBLE);
                mStatusText.setText(R.string.status_guide_text);
                break;
            default:
                super.handleMessage(msg);
            }
        }
    };
    
    private static final float TARGET_HEIGHT_FRACTION = 0.033f;
    private static final float TARGET_WIDTH_FRACTION = 0.021f;
        
    private Rect makeTargetRect () {
        int halfWidth = (int)(TARGET_WIDTH_FRACTION * mPreviewWidth / 2.0f);
        int halfHeight = (int)(TARGET_HEIGHT_FRACTION * mPreviewHeight / 2.0f);
        int centerX = mPreviewHeight / 2;
        int centerY = mPreviewWidth / 2;
        return new Rect(centerY - halfWidth, centerX - halfHeight, 
                    centerY + halfWidth, centerX + halfHeight);
    }
    
    private static final SimpleStructuringElement sHStrel = SimpleStructuringElement.makeHorizontal(2);
    private static final SimpleStructuringElement sVStrel = SimpleStructuringElement.makeVertical(2);
    
    // FIXME make this return extracted Bitmap
    private static final GrayImage findWordExtent (GrayImage img, Rect ext) {
        
        FileDumpUtil.dump("in", img);  // XXX tmp
        
        // Contrast stretch
        int imgMin = img.min(), imgMax = img.max();
        Log.d(TAG, "Image min = " + imgMin + ", max = " + imgMax);
        GrayImage resultImg = img.contrastStretch((byte)imgMin, (byte)imgMax); // Temporarily store stretched image here

        FileDumpUtil.dump("stretch", resultImg);  // XXX tmp

        // Adaptive threshold
        float imgMean = resultImg.mean();
        Log.d(TAG, "Stretched image mean = " + imgMean);
        byte hi, lo;
        if (imgMean > 127) { // XXX Arbitrary threshold
            // Most likely dark text on light background
            hi = (byte)255; 
            lo = (byte)0;
        } else {
            // Most likely light text on dark background
            hi = (byte)0;
            lo = (byte)255;
        }
        GrayImage tmpImg = resultImg.meanFilter(10);  // Temporarily store local means here
        int threshOffset = (int)(0.33 * Math.sqrt(resultImg.variance()));  // 0.33 pulled out of my butt
        resultImg.adaptiveThreshold(hi, lo, threshOffset, tmpImg, resultImg);

        FileDumpUtil.dump("mean", tmpImg);  // XXX tmp
        FileDumpUtil.dump("bin", resultImg);  // XXX tmp

        // Dilate; it's grayscale, so we should use erosion instead
        resultImg.erode(sHStrel, tmpImg);
        GrayImage binImg = tmpImg.erode(sVStrel);

        FileDumpUtil.dump("strop", binImg);  // XXX tmp

        // Find word extents
        int left = ext.left, right = ext.right, top = ext.top, bottom = ext.bottom;
        int imgWidth = img.getWidth(), imgHeight = img.getHeight();
        boolean extended;
        do {
            extended = false;
            
            if ((top - 1 >= 0) && binImg.min(left, top - 1, right, top) == 0) {
                --top;
                extended = true;
            }
            if ((bottom + 1 < imgHeight) && binImg.min(left, bottom, right, bottom + 1) == 0) {
                ++bottom;
                extended = true;
            }
            if ((left - 1 >= 0) && binImg.min(left - 1, top, left, bottom) == 0) {
                --left;
                extended = true;
            }
            if ((right + 1 < imgWidth) && binImg.min(right, top, right + 1, bottom) == 0) {
                ++right;
                extended = true;
            }
        } while (extended);
        ext.set(left, top, right, bottom);            
        
        return resultImg;
    }

}