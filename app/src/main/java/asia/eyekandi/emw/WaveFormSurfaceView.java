package asia.eyekandi.emw;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Build;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.squareup.otto.Subscribe;

import javax.inject.Inject;

import asia.eyekandi.emw.busevents.IntensityChangedEvent;
import asia.eyekandi.emw.busevents.RowClickedEvent;
import asia.eyekandi.emw.di.EventBus;
import roboguice.util.Ln;

/**
 * Created by mitja on 16/02/16.
 * Copyright AMOK Products ApS
 */
public class WaveFormSurfaceView extends SurfaceView implements SurfaceHolder.Callback {
    @Inject
    EventBus bus;

    // distance between peaks
    private static final float defaultFrequency = 1.5f;
    private static final float defaultAmplitude = 1.0f;
    private static final float defaultIdleAmplitude = 0.01f;
    private static final float defaultNumberOfWaves = 6.0f;
    // how slow the wave is
    private static final float defaultPhaseShift = -0.03f;
    private static final float defaultPrimaryLineWidth = 3.0f;
    private static final float defaultSecondaryLineWidth = 1.0f;


    private static final int WAVETYPE_INDEX_HIGH = 0;
    private static final int WAVETYPE_INDEX_MID = 1;
    private static final int WAVETYPE_INDEX_LOW = 2;
    private static final int WAVETYPE_INDEX_LINE = 3;

    private float amplitude;
    private float frequency;
    private float phaseShift;
    static public float density;
    private static Paint greyPaint;
    private float idleAmplitude;
    private static Paint darkGreyPaint;
    private static Paint bluePaint;
    private static int greyColor;
    private static int darkGreyColor;
    private static int blueColor;

    private static Paint primaryLinePaint;
    private static Paint secondaryLinePaint;
    private static Paint circlePaint;

    private Path path = new Path();
    // 0 is topmost
    private int index;

    public void setIndex(int index, MyApplication application) {
        Ln.d("SETTING INDEX %d", index);
        if (bus == null) {
            Ln.d("Injecting");
            application.component().inject(this);
        }
        setIndex(index);
    }

    private void setIndex(int index) {
        this.index = index;

        if (index == WAVETYPE_INDEX_MID) {
            // mid
            waveHeight = height * 0.5f;
            maxAmplitude = waveHeight - 4.0f;
        } else if (index == WAVETYPE_INDEX_LOW) {
            // low
            waveHeight = height * 0.75f;
            maxAmplitude = height * 0.25f - 4.0f;
        } else if (index == WAVETYPE_INDEX_HIGH) {
            // high
            waveHeight = height * 0.25f;
            maxAmplitude = waveHeight - 4.0f;
        } else if (index == WAVETYPE_INDEX_LINE) {
            waveHeight = height * 0.5f;
            maxAmplitude = 0;
        }
    }

    private float waveHeight;
    private float height;
    private float width;
    private float mid;
    private float maxAmplitude;
    private float phase;
    private float numberOfWaves;
    float centerPointYValue;

    class WaveThread extends Thread {
        private final SurfaceHolder mSurfaceHolder;
        private boolean mRun;
        private final Object mRunLock = new Object();

        public WaveThread(SurfaceHolder holder) {
            mSurfaceHolder = holder;
        }

        private void updateWave() {
        }

        @Override
        public void run() {
            while (mRun) {
                Canvas c = null;
                try {
                    c = mSurfaceHolder.lockCanvas(null);
                    synchronized (mSurfaceHolder) {
                        synchronized (mRunLock) {
                            if (mRun) {
                                doDraw(c, true);
                            }
                        }
                    }
                } finally {
                    if (c != null) {
                        mSurfaceHolder.unlockCanvasAndPost(c);
                    }
                }
            }
            Ln.d("RUN FINISHED");
        }

        public void setRunning(boolean b) {
            // Do not allow mRun to be modified while any canvas operations
            // are potentially in-flight. See doDraw().
            synchronized (mRunLock) {
                mRun = b;
            }
        }
    }

    /* Callback invoked when the surface dimensions change. */
    public void setSurfaceSize(int width, int height) {
        // synchronized to make sure these all change atomically
        synchronized (getHolder()) {
            this.height = height;
            setIndex(index);
            this.width = width;
            this.mid = width / 2;
        }
    }

    @Nullable
    private WaveThread thread;

    public WaveFormSurfaceView(Context context) {
        super(context);
        init();
    }

    public WaveFormSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WaveFormSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public WaveFormSurfaceView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        SurfaceHolder holder = getHolder();
        holder.addCallback(this);

        this.frequency = defaultFrequency;
        this.numberOfWaves = defaultNumberOfWaves;

        this.amplitude = defaultAmplitude;
        this.idleAmplitude = defaultIdleAmplitude;

        this.phaseShift = defaultPhaseShift;
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

        primaryLinePaint = new Paint();
        primaryLinePaint.setColor(blueColor);
        primaryLinePaint.setAntiAlias(true);
        primaryLinePaint.setStrokeWidth(defaultPrimaryLineWidth);
        primaryLinePaint.setStyle(Paint.Style.STROKE);

        secondaryLinePaint = new Paint();
        secondaryLinePaint.setColor(blueColor);
        secondaryLinePaint.setAntiAlias(true);
        secondaryLinePaint.setStrokeWidth(defaultSecondaryLineWidth);
        secondaryLinePaint.setStyle(Paint.Style.STROKE);

        circlePaint = new Paint();
        circlePaint.setColor(blueColor);
        circlePaint.setAntiAlias(true);
        circlePaint.setStyle(Paint.Style.FILL);
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        Ln.i("onWindowFocusChanged %b", hasWindowFocus);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Ln.d("Surface %d created", index);
        bus.register(this);
        lockAndDrawOnce();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        setSurfaceSize(width, height);
        lockAndDrawOnce();
    }

    /*
    * Callback invoked when the Surface has been destroyed and must no longer
    * be touched. WARNING: after this method returns, the Surface/Canvas must
    * never be touched again!
    */
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // we have to tell thread to shut down & wait for it to finish, or else
        // it might touch the Surface after we return and explode
        Ln.d("Surface %d destroyed", index);
        bus.unregister(this);
        stopAnim();
    }

    private static volatile float mIntensity = 1.0f;
    private static IntensityChangedEvent intensityChangedEvent = new IntensityChangedEvent(0);

    public static void setIntensity(float intensity, EventBus bus) {
        mIntensity = intensity;
        intensityChangedEvent.intensity = intensity;
        bus.post(intensityChangedEvent);
    }

    public void startAnim() {
        if (thread != null) {
            Ln.e("Thread not null");
            return;
        }

        thread = new WaveThread(getHolder());
        thread.setRunning(true);
        try {
            Ln.d("Starting thread");
            thread.start();
        } catch (IllegalThreadStateException ex) {
            Ln.e(ex, "thread Already started");
        }
    }

    public void toggleAnim() {
        if (thread != null) {
            Ln.d("Toggling to stop");
            stopAnim();
        } else {
            Ln.d("toggling to start");
            startAnim();
        }
    }

    public void stopAnim() {
        Ln.d("Stopping anim");
        if (thread == null) {
            Ln.d("Already stopped");
            return;
        }
        boolean retry = true;
        thread.setRunning(false);
        while (retry) {
            try {
                Ln.d("Joining thread");
                thread.join();
                retry = false;
                Ln.d("Joined");
            } catch (InterruptedException e) {
                Ln.e(e, "Interrupted");
            }
        }
        thread = null;
        Ln.d("Stopped");
        lockAndDrawOnce();
    }

    void lockAndDrawOnce() {
        if (thread != null) {
            Ln.e("Thread already running");
            return;
        }
        Canvas c = null;
        SurfaceHolder holder = getHolder();
        try {
            c = holder.lockCanvas(null);
            doDraw(c, false);
        } finally {
            if (c != null) {
                holder.unlockCanvasAndPost(c);
            }
        }
    }

    void doDraw(Canvas canvas, boolean changePhase) {
        if (canvas == null) {
            Ln.e("No canvas");
            return;
        }
        if (thread == null) {
            canvas.drawColor(darkGreyColor);
        } else {
            canvas.drawColor(greyColor);
        }
        centerPointYValue = 0.0f;
        for (int i = 0; i < numberOfWaves; i++) {
            float progress = 1.0f - (float) i / this.numberOfWaves;
            float normedAmplitude = (1.5f * progress - 0.5f) * WaveFormSurfaceView.this.amplitude;
            path.reset();

            //float multiplier = Math.min(1.0f, (progress / 3.0f * 2.0f) + (1.0f / 3.0f));

            for (float x = 0; x < width * density; x += density) {
                // We use a parable to scale the sinus wave, that has its peak in the middle of the view.
                float scaling = (float) (-Math.pow(1 / mid * (x - mid), 2) + 1) * mIntensity;
                float y = (float) (scaling * maxAmplitude * normedAmplitude * Math.sin(2 * Math.PI * (x / width) * frequency + phase));// + waveHeight);
                if (index == WAVETYPE_INDEX_LINE) {
                    y += mIntensity * height;
                } else {
                    y += waveHeight;
                }
                if (x == 0) {
                    path.moveTo(x, y);
                } else {
                    path.lineTo(x, y);
                    if (i == 0 && x > width / 2 && centerPointYValue == 0.0f) {
                        centerPointYValue = y / height;
                        canvas.drawCircle(x, y, 5, circlePaint);
                        //Ln.d("Y: %f", centerPointYValue);

                    }
                }
            }
            if (i == 0) {
                canvas.drawPath(path, primaryLinePaint);
            } else {
                canvas.drawPath(path, secondaryLinePaint);
            }
        }
        if (changePhase) {
            this.phase += phaseShift;
        }
    }


    @Subscribe
    public void rowClicked(RowClickedEvent event) {
        Ln.d("ROW CLICKED: %d", event.row);
        if (event.row == index) {
            toggleAnim();
        } else {
            stopAnim();
        }
    }

    @Subscribe
    public void intensityChanged(IntensityChangedEvent event) {
        if (thread == null) {
            lockAndDrawOnce();
        }
    }
}
