package asia.eyekandi.emw;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;

import roboguice.util.Ln;

/**
 * Created by mitja on 15/02/16.
 * Copyright AMOK Products ApS
 */
public class WaveFormView extends View {
    // distance between peaks
    private static final float defaultFrequency = 1.5f;
    private static final float defaultAmplitude = 1.0f;
    private static final float defaultIdleAmplitude = 0.01f;
    private static final float defaultNumberOfWaves = 5.0f;
    // how slow the wave is
    private static final float defaultPhaseShift = -0.05f;
    private static final float defaultPrimaryLineWidth = 3.0f;
    private static final float defaultSecondaryLineWidth = 1.0f;

    private float phase;
    private float amplitude;
    private float frequency;
    private float idleAmplitude;
    private float numberOfWaves;
    private float phaseShift;
    static private float density;
    private float primaryWaveLineWidth;
    private float secondaryWaveLineWidth;
    private static Paint greyPaint;
    private static Paint darkGreyPaint;
    private static Paint bluePaint;
    private static int greyColor;
    private static int darkGreyColor;
    private static int blueColor;
    private Paint mPaintColor;
    private Path path = new Path();
    private float halfHeight;
    private float width;
    private float mid;
    private float maxAmplitude;


    public WaveFormView(Context context) {
        super(context);
        setUp();
    }


    public WaveFormView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setUp();
    }

    public WaveFormView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setUp();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public WaveFormView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setUp();
    }

    public void updateAmplitude(float ampli, boolean isSpeaking) {
        this.amplitude = Math.max(ampli, idleAmplitude);
    }

    void setUp() {
        mPaintColor = bluePaint;
        mPaintColor.setColor(blueColor);
        mPaintColor.setStyle(Paint.Style.STROKE);
        mPaintColor.setAntiAlias(true);

        this.frequency = defaultFrequency;

        this.amplitude = defaultAmplitude;
        this.idleAmplitude = defaultIdleAmplitude;

        this.numberOfWaves = defaultNumberOfWaves;
        this.phaseShift = defaultPhaseShift;

        this.primaryWaveLineWidth = defaultPrimaryLineWidth;
        this.secondaryWaveLineWidth = defaultSecondaryLineWidth;
    }

    @SuppressWarnings("deprecation")
    public static void setResources(Resources resources) {
        blueColor = resources.getColor(R.color.emwBlue);
        greyColor = resources.getColor(R.color.emwLightGrey);
        darkGreyColor = resources.getColor(R.color.emwDarkGrey);

        greyPaint = new Paint(resources.getColor(R.color.emwLightGrey));
        darkGreyPaint = new Paint(resources.getColor(R.color.emwDarkGrey));
        bluePaint = new Paint(resources.getColor(R.color.emwBlue));
        density = resources.getDisplayMetrics().density;
    }


    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        Ln.d("onSizeChanged %d %d %d %d", w, h, oldw, oldh);
        // 02-15 17:00:12.781 D/asia.eyekandi.emw/WaveFormView.java:110(21422): main onSizeChanged 568 72 0 0
        halfHeight = h / 2;
        width = w;
        mid = w / 2;
        maxAmplitude = halfHeight - 4.0f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        //Ln.d("ONDRAW %d %d", canvas.getWidth(), canvas.getHeight());
        canvas.drawColor(greyColor);
        for (int i = 0; i < numberOfWaves; i++) {
            mPaintColor.setStrokeWidth(i == 0 ? primaryWaveLineWidth : secondaryWaveLineWidth);
            float progress = 1.0f - (float) i / this.numberOfWaves;
            float normedAmplitude = (1.5f * progress - 0.5f) * this.amplitude;
            path.reset();

            //float multiplier = Math.min(1.0f, (progress / 3.0f * 2.0f) + (1.0f / 3.0f));

            for (float x = 0; x < width * density; x += density) {
                // We use a parable to scale the sinus wave, that has its peak in the middle of the view.
                float scaling = (float) (-Math.pow(1 / mid * (x - mid), 2) + 1);

                float y = (float) (scaling * maxAmplitude * normedAmplitude * Math.sin(2 * Math.PI * (x / width) * frequency + phase) + halfHeight);

                if (x == 0) {
                    path.moveTo(x, y);
                } else {
                    path.lineTo(x, y);
                }
            }
            canvas.drawPath(path, mPaintColor);
        }
        this.phase += phaseShift;
        invalidate();
    }
}
