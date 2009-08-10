package net.bitquill.ocr;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

public class GuideView extends View {
    
    private static final float OUTER_FRACTION = 0.125f;  //  1/8th
    private static final float INNER_FRACTION = 0.083f;  //  1/12th
    private static final float WIDTH_FRACTION = 0.250f;  //  1/4th

    private Paint mPaint;
    private Rect mRect;
    private int mDarkMaskColor;
    private int mLightMaskColor;
    private int mOuterColor;
    private int mInnerColor;
    
    public GuideView(Context context, AttributeSet attrs) {
        super(context, attrs);
        
        // Initialize these once for performance rather than calling them every time in onDraw().
        mPaint = new Paint();
        mRect = new Rect();
        Resources resources = getResources();
        mDarkMaskColor = resources.getColor(R.color.guide_mask_dark);
        mLightMaskColor = resources.getColor(R.color.guide_mask_light);
        mOuterColor = resources.getColor(R.color.guide_outer);
        mInnerColor = resources.getColor(R.color.guide_inner);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Copy into local variables for efficiency
        final Paint paint = mPaint;
        final Rect rect = mRect;
        
        int width = canvas.getWidth();
        int height = canvas.getHeight();
        
        int maskHeight = Math.round((1.0f - OUTER_FRACTION) * height / 2.0f);
        int guideGap = Math.round((OUTER_FRACTION - INNER_FRACTION) * height / 2.0f);
        int maskWidth = Math.round((1.0f - WIDTH_FRACTION) * width / 2.0f);

        // Draw the exterior (i.e. outside the framing guides) darkened
        paint.setColor(mDarkMaskColor);
        rect.set(0, 0, width, maskHeight);
        canvas.drawRect(rect, paint);
        rect.set(0, height - maskHeight, width, height);
        canvas.drawRect(rect, paint);
        
        // Draw lighter mask
        paint.setColor(mLightMaskColor);
        rect.set(0, maskHeight, maskWidth - 1, height - maskHeight);
        canvas.drawRect(rect, paint);
        rect.set(width - maskWidth, maskHeight, width, height - maskHeight);
        canvas.drawRect(rect, paint);

        // Draw outer and inner text line guides
        paint.setColor(mOuterColor);
        rect.set(0, maskHeight, width, maskHeight + 2);
        canvas.drawRect(rect, paint);
        rect.set(0, height - maskHeight - 2, width, height - maskHeight);
        canvas.drawRect(rect, paint);
        paint.setColor(mInnerColor);
        rect.set(0, maskHeight + guideGap, width, maskHeight + guideGap + 1);
        canvas.drawRect(rect, paint);
        rect.set(0, height - maskHeight - guideGap - 1, width, height - maskHeight - guideGap);
        canvas.drawRect(rect, paint);
        
        // Draw width guides
        paint.setColor(mOuterColor);
        rect.set(maskWidth - 1, maskHeight, maskWidth, height - maskHeight);
        canvas.drawRect(rect, paint);
        rect.set(width - maskWidth - 1, maskHeight, width - maskWidth, height - maskHeight);
        canvas.drawRect(rect, paint);
    }
}
