package asia.eyekandi.emw;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.Surface;
import android.view.TextureView;

import com.squareup.otto.Subscribe;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;

import asia.eyekandi.emw.busevents.IntensityChangedEvent;
import asia.eyekandi.emw.busevents.NewGaussEvent;
import asia.eyekandi.emw.busevents.NewSineEvent;
import asia.eyekandi.emw.busevents.NewStepEvent;
import asia.eyekandi.emw.busevents.RowClickedEvent;
import asia.eyekandi.emw.busevents.StopAnimEvent;
import asia.eyekandi.emw.busevents.StopWandEvent;
import asia.eyekandi.emw.di.EventBus;
import roboguice.util.Ln;

/**
 * Created by mitja on 16/02/16.
 * Copyright AMOK Products ApS
 */
public class WaveFormTextureView extends TextureView implements TextureView.SurfaceTextureListener {
    @Inject
    EventBus bus;

    // must always contain at least two features
    static private LinkedList<CoordinateFeature> featureList = new LinkedList<>();

    // distance between peaks
    private static final float defaultFrequency = 1.5f;
    private static final float defaultAmplitude = 1.0f;
    private static final float defaultNumberOfWaves = 6.0f;
    private static final float defaultPrimaryLineWidth = 3.0f;
    private static final float defaultSecondaryLineWidth = 1.0f;


    public static final int WAVETYPE_INDEX_NONE = -1;
    public static final int WAVETYPE_INDEX_LINE = 0;
    public static final int WAVETYPE_INDEX_GAUSS = 1;
    public static final int WAVETYPE_INDEX_HIGH = 2;
    public static final int WAVETYPE_INDEX_MID = 3;
    public static final int WAVETYPE_INDEX_LOW = 4;

    private float amplitude;
    private float frequency;
    // how slow the wave is
    static float phaseMultiplier = 0.0000000001f * 10.0f;
    float phaseDiff = 0.0f;
    public static float density;
    private static Paint darkGreyPaint;
    private static Paint bluePaint;
    private static int greyColor;
    private static int darkGreyColor;
    private static int blueColor;
    private PointF lastDrawnFeatureExitPoint = new PointF();
    private PointF nextFeatureEntryPoint = new PointF();

    private static Paint primaryLinePaint;
    private static Paint secondaryLinePaint;
    private static Paint circlePaint;
    private static TextPaint paintTextDark;
    private static TextPaint paintTextLight;
    private @Nullable CoordinateFeature activeFeature;

    private Path path = new Path();
    final Object mLock = new Object();
    // 0 is topmost
    int index;
    public static final ConcurrentHashMap<Integer, Boolean> runningThreadStates = new ConcurrentHashMap<>(4);

    public static int getRunningThreadType() {
        for (ConcurrentHashMap.Entry<Integer, Boolean> entry: runningThreadStates.entrySet()) {
            if(entry.getValue()) {
                return entry.getKey();
            }
        }
        return WAVETYPE_INDEX_NONE;
    }

    public static final int INVALID_THREAD_INDEX = -1;
    public static int getRunningThreadIndex() {
        for (ConcurrentHashMap.Entry<Integer, Boolean> entry: runningThreadStates.entrySet()) {
            if(entry.getValue()) {
                return entry.getKey();
            }
        }
        return INVALID_THREAD_INDEX;
    }

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

        if (index == WAVETYPE_INDEX_GAUSS) {
            waveHeight = height * 0.5f;
            maxAmplitude = waveHeight - 4.0f;
            numberOfWaves = defaultNumberOfWaves;

        } else if (index == WAVETYPE_INDEX_MID) {
            // mid
            waveHeight = height * 0.5f;
            maxAmplitude = waveHeight - 4.0f;
            numberOfWaves = defaultNumberOfWaves;
        } else if (index == WAVETYPE_INDEX_LOW) {
            // low
            waveHeight = height * 0.75f;
            maxAmplitude = height * 0.25f - 4.0f;
            numberOfWaves = defaultNumberOfWaves;
        } else if (index == WAVETYPE_INDEX_HIGH) {
            // high
            waveHeight = height * 0.25f;
            maxAmplitude = waveHeight - 4.0f;
            numberOfWaves = defaultNumberOfWaves;
        } else if (index == WAVETYPE_INDEX_LINE) {
            waveHeight = height * 0.5f;
            maxAmplitude = 0;
            numberOfWaves = 1;
        }

        if (thread == null) {
            lockAndDrawOnce();
        }
    }

    private float waveHeight;
    private float height;
    private float width;
    private float mid;
    private float maxAmplitude;
    private float phase;
    private float lineCirclePos;
    private float numberOfWaves;
    float centerPointYValue;
    SurfaceTexture mSurfaceTexture;

    static class RenderHandler extends Handler {
        private WeakReference<WaveThread> mThread;
        private static final int SHUTDOWN = 1;

        RenderHandler(WaveThread thread) {
            mThread = new WeakReference<>(thread);
        }

        public void handleMessage(Message msg) {
            Ln.d("Handling message %s", msg);
            WaveThread thread = mThread.get();
            if (thread == null) {
                Ln.e("No thread for handler");
                return;
            }
            if (msg.what == SHUTDOWN) {
                Ln.d("Shutting down looper at next frame update");
                thread.shutdown();
            }
        }

        public void sendShutdown() {
            sendMessage(obtainMessage(SHUTDOWN));
        }
    }

    class WaveThread extends Thread implements Choreographer.FrameCallback {
        private Surface surface;
        private volatile RenderHandler mHandler;
        private volatile boolean shuttingDown;
        private long previousFrameTime = 0;

        public WaveThread() {
        }

        @Override
        public void run() {
            runningThreadStates.put(index, Boolean.TRUE);
            synchronized (mLock) {
                SurfaceTexture surfaceTexture = mSurfaceTexture;
                if (surfaceTexture == null) {
                    Ln.e("ERROR: No surface texture");
                    return;
                }
                surface = new Surface(surfaceTexture);
            }
            bus.postOnMain(new IntensityChangedEvent(mIntensity));
            Looper.prepare();
            mHandler = new RenderHandler(this);
            Choreographer.getInstance().postFrameCallback(this);
            Looper.loop();
            Choreographer.getInstance().removeFrameCallback(this);
            surface.release();
            Ln.d("RUN FINISHED");
            runningThreadStates.put(index, Boolean.FALSE);
        }

        public RenderHandler getHandler() {
            return mHandler;
        }

        @Override
        public void doFrame(long frameTimeNanos) {
            if (shuttingDown == true) {
                Ln.d("Got shutdown");
                Looper looper = Looper.myLooper();
                if (looper != null) {
                    looper.quit();
                }
                return;
            }

            // at most skip 4 frames
            phaseDiff = Math.min((frameTimeNanos - previousFrameTime) * phaseMultiplier, 0.03f * 4.0f);
            //Ln.d("doFrame %f", phaseDiff);
            previousFrameTime = frameTimeNanos;
            Canvas c = null;
            float ret = 0.0f;
            try {
                c = surface.lockCanvas(null);
                ret = doDraw(c, true);
            } finally {
                if (c != null) {
                    surface.unlockCanvasAndPost(c);
                }
            }
            Choreographer.getInstance().postFrameCallback(this);
            /*
            if (BLEScanner2.isValueWriteInProgress() == false) {
                bus.postOnMain(new NewValueEvent(ret, index));
            }
            */
            //Ln.d("VAL: %f", ret);
        }

        public void shutdown() {
            shuttingDown = true;
        }
    }

    /* Callback invoked when the surface dimensions change. */
    public void setSurfaceSize(int width, int height) {
        Ln.d("setSurfaceSize %d %d", width, height);
        // synchronized to make sure these all change atomically
        synchronized (mLock) {
            Ln.d("FEATDEBUG setting width %d", width);
            this.width = width;
            this.height = height;
            this.mid = width / 2;
            this.lineCirclePos = width / 2;
            CoordinateFeature.setHeight(height);
            // setIndex draws so do it last
            setIndex(index);
        }
    }

    @Nullable
    private WaveThread thread;

    public WaveFormTextureView(Context context) {
        super(context);
        init();
    }

    public WaveFormTextureView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WaveFormTextureView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        this.setSurfaceTextureListener(this);
        this.frequency = defaultFrequency;
        this.numberOfWaves = defaultNumberOfWaves;
        this.amplitude = defaultAmplitude;
    }

    @SuppressWarnings("deprecation")
    public static void setResources(Resources resources) {
        blueColor = resources.getColor(R.color.emwBlue);
        greyColor = resources.getColor(R.color.emwLightGrey);
        darkGreyColor = resources.getColor(R.color.emwDarkGrey);
        darkGreyPaint = new Paint(resources.getColor(R.color.emwDarkGrey));
        bluePaint = new Paint(resources.getColor(R.color.emwBlue));
        density = resources.getDisplayMetrics().density;

        primaryLinePaint = new Paint();
        primaryLinePaint.setColor(blueColor);
        primaryLinePaint.setAntiAlias(true);
        primaryLinePaint.setStrokeWidth(defaultPrimaryLineWidth * density);
        primaryLinePaint.setStyle(Paint.Style.STROKE);

        secondaryLinePaint = new Paint();
        secondaryLinePaint.setColor(blueColor);
        secondaryLinePaint.setAntiAlias(true);
        secondaryLinePaint.setStrokeWidth(defaultSecondaryLineWidth * density);
        secondaryLinePaint.setStyle(Paint.Style.STROKE);

        circlePaint = new Paint();
        circlePaint.setColor(blueColor);
        circlePaint.setAntiAlias(true);
        circlePaint.setStyle(Paint.Style.FILL);

        paintTextDark = new TextPaint();
        paintTextDark.setTextAlign(Paint.Align.CENTER);
        paintTextDark.setTextSize(16 * density);
        paintTextDark.setColor(darkGreyColor);
        Typeface currentTypeFace = paintTextDark.getTypeface();

        Typeface bold = Typeface.create(currentTypeFace, Typeface.BOLD);
        paintTextDark.setTypeface(bold);

        paintTextLight = new TextPaint();
        paintTextLight.setTextAlign(Paint.Align.CENTER);
        paintTextLight.setTextSize(16 * density);
        paintTextLight.setColor(greyColor);
        paintTextLight.setTypeface(bold);

        indexNames.add(resources.getString(R.string.LINE));
        indexNames.add(resources.getString(R.string.TRIP));
        indexNames.add(resources.getString(R.string.HIGH));
        indexNames.add(resources.getString(R.string.MID));
        indexNames.add(resources.getString(R.string.LOW));
    }

    /*
    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        Ln.i("onWindowFocusChanged %b", hasWindowFocus);
        if (!hasWindowFocus) {
            //if(thread != null) thread.doPause();
        }
    }
    */

    private static volatile float mIntensity = 1.0f;
    static IntensityChangedEvent intensityChangedEvent = new IntensityChangedEvent(0);

    public static void setIntensity(float intensity, EventBus bus) {
        CoordinateFeature.setIntensity(intensity);
        intensityChangedEvent.intensity = intensity;
        mIntensity = intensity;
        bus.post(intensityChangedEvent);
    }

    public void startAnim() {
        if (thread != null) {
            Ln.e("Thread not null");
            return;
        }
        //bus.post(new StartWandEvent());
        thread = new WaveThread();
        try {
            Ln.d("Starting thread");
            thread.start();
        } catch (IllegalThreadStateException ex) {
            Ln.e(ex, "thread Already started");
        }
    }

    private void stopAnimWithoutStoppingWand() {
        Ln.d("Stopping anim without stopping wand");
        if (thread == null) {
            Ln.d("Already stopped");
            return;
        }
        boolean retry = true;
        thread.getHandler().sendShutdown();
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
        Ln.d("Stopped without stopping wand");
        lockAndDrawOnce();
    }

    private void stopAnim() {
        Ln.d("Stopping anim");
        if (thread == null) {
            Ln.d("Already stopped");
            return;
        }
        bus.post(new StopWandEvent());
        boolean retry = true;
        thread.getHandler().sendShutdown();
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

        Surface surface;
        synchronized (mLock) {
            SurfaceTexture surfaceTexture = mSurfaceTexture;
            if (surfaceTexture == null) {
                Ln.d("No surfacetexture yet");
                return;
            }
            surface = new Surface(surfaceTexture);
        }

        Canvas c = null;
        try {
            c = surface.lockCanvas(null);
            doDraw(c, false);
        } finally {
            if (c != null) {
                surface.unlockCanvasAndPost(c);
            }
        }
        surface.release();
    }

    float doDraw(Canvas canvas, boolean changePhase) {
        if (canvas == null) {
            Ln.e("No canvas");
            return mIntensity;
        }
        if (thread == null) {
            canvas.drawColor(darkGreyColor);
        } else {
            canvas.drawColor(greyColor);
        }
        path.reset();

        float ret;
        if (index == WAVETYPE_INDEX_LINE) {
            ret = doDrawLine(canvas, changePhase);
        } else if (index == WAVETYPE_INDEX_GAUSS) {
            ret = doDrawFeatures(canvas, changePhase);
        } else {
            ret = doDrawSin(canvas, changePhase);
        }
        drawText(canvas, getNameForIndex());
        return ret;
    }

    private static ArrayList<String> indexNames = new ArrayList<>();

    private String getNameForIndex() {
        return indexNames.get(index);
    }

    private float doDrawFeatures(Canvas canvas, boolean changePhase) {
        // initial position, box to the right of middle, with midpoint being the left edge of box
        centerPointYValue = 0.0f;

        if (featureList.isEmpty()) {
            StepUpFeature seed = new StepUpFeature();
            CoordinateFeature feature = new StepDownFeature(seed);
            feature.leftEdgePositionInParent = -feature.padding;
            featureList.add(feature);

            feature = addFeatureToRightOf(feature);
            while (feature != null) {
                Ln.d("FEATDEBUG another feature");
                feature = addFeatureToRightOf(feature);
            }

            /*
            Ln.d("FEATDEBUG First feature in position %f/%f", feature.getNextFeaturePositionInParentCoordinates(), this.width);

            CoordinateFeature feature2 = new GaussFeature();
            feature2.leftEdgePositionInParent = feature.getNextFeaturePositionInParentCoordinates();
            featureList.add(feature2);
            feature = feature2;
            Ln.d("FEATDEBUG Next feature in position %f/%f", feature.getNextFeaturePositionInParentCoordinates(), this.width);
            */

        }

        //Ln.d("FEATDEBUG Drawing %d features", featureList.size());

        CoordinateFeature firstFeature = featureList.getFirst();
        if(activeFeature != firstFeature) {
            activeFeature = firstFeature;
            Ln.i("Active feature changed %s", activeFeature);
            bus.postOnMain(new NewStepEvent(activeFeature, index));
        }
        lastDrawnFeatureExitPoint.set(firstFeature.getCanvasExitX(), firstFeature.getCanvasExitY());
        nextFeatureEntryPoint.set(-1.0f, -1.0f);

        CoordinateFeature lastDrawnFeature = null;
        CoordinateFeature feature = null;
        ListIterator<CoordinateFeature> iterator;

        float y = 0.0f;
        boolean firstPoint = true;
        for (float x = 0; x < width * density; x += density) {
            iterator = featureList.listIterator();

            while (iterator.hasNext()) {
                feature = iterator.next();

                if (feature.isParentCoordinateWithinFeature(x)) {
                    y = feature.getCanvasY(x);
                    lastDrawnFeature = feature;
                    break;
                } else {
                    // between features
                    if (feature == lastDrawnFeature) {
                        // means this is the first point to be drawn after exiting a feature

                        //canvas.drawCircle(feature.getCanvasExitX(), feature.getCanvasExitY(), 5*density, circlePaint);
                        lastDrawnFeatureExitPoint.set(feature.getCanvasExitX(), feature.getCanvasExitY());
                        lastDrawnFeature = null;
                        // y should gradually change to next feature's entrypoint
                        if (iterator.hasNext()) {
                            feature = iterator.next();
                            nextFeatureEntryPoint.set(feature.getCanvasEntryX(), feature.getCanvasEntryY());
                            // dumb since it goes forward next anyway
                            //Ln.d("FEATDEBUG going from point %s to %s", lastDrawnFeatureExitPoint, nextFeatureEntryPoint);

                            //feature =
                            iterator.previous();
                        }
                    } else if (nextFeatureEntryPoint.x == -1.0f) {
                        // before the first feature
                        if (iterator.hasNext()) {
                            feature = iterator.next();
                            nextFeatureEntryPoint.set(feature.getCanvasEntryX(), feature.getCanvasEntryY());
                            iterator.previous();
                        }
                        //Ln.d("FEATDEBUG before first feature from %f -> %f", lastDrawnFeatureExitPoint.y, nextFeatureEntryPoint.y);
                    }
                    y = getLineYbasedOnPoints(x, lastDrawnFeatureExitPoint, nextFeatureEntryPoint);
                }
            }

            if (y < 2) {
                // leave space for the "ball" to be drawn
                if (firstPoint) {
                    path.moveTo(x, 2);
                    firstPoint = false;
                } else {
                    path.lineTo(x, 2);
                }
            } else if (firstPoint) {
                path.moveTo(x, y);
                firstPoint = false;
            } else {
                path.lineTo(x, y);
            }

            if (centerPointYValue == 0.0f && x > mid) {
                centerPointYValue = 1 - (y / height);
                //canvas.drawCircle(x, y, 5 * density, circlePaint);
            }
        }

        if (changePhase) {
            iterator = featureList.listIterator();
            while (iterator.hasNext()) {
                feature = iterator.next();
                feature.changePhase();
                if (feature.hasTravelledPastParent()) {
                    //Ln.d("FEATDEBUG removing feature");
                    iterator.remove();
                }
            }
            addFeatureToRightOf(feature);
        }
        canvas.drawPath(path, primaryLinePaint);
        return centerPointYValue;
    }

    // return added feature if one was added
    @Nullable
    private CoordinateFeature addFeatureToRightOf(CoordinateFeature lastFeature) {
        if (lastFeature.getNextFeaturePositionInParentCoordinates() < width + lastFeature.padding) {
            Ln.d("FEATDEBUG adding new feature since last feature %s was close enough: %f", lastFeature.getName(), lastFeature.getNextFeaturePositionInParentCoordinates());
            if (false && CoordinateFeature.rng.nextBoolean()) {
                CoordinateFeature newFeature = new GaussFeature();
                newFeature.leftEdgePositionInParent = lastFeature.getNextFeaturePositionInParentCoordinates();
                featureList.add(newFeature);
                return newFeature;
            } else {
                StepUpFeature stepup = new StepUpFeature();
                stepup.leftEdgePositionInParent = lastFeature.getNextFeaturePositionInParentCoordinates();
                featureList.add(stepup);

                CoordinateFeature stepdown = new StepDownFeature(stepup);
                stepdown.leftEdgePositionInParent = stepup.getNextFeaturePositionInParentCoordinates();
                featureList.add(stepdown);
                return stepdown;
            }
        }
        return null;
    }

    private float doDrawSin(Canvas canvas, boolean changePhase) {
        float ret = 0.0f;
        centerPointYValue = 0.0f;
        for (int i = 0; i < numberOfWaves; i++) {
            float progress = 1.0f - (float) i / this.numberOfWaves;
            float normedAmplitude = (1.5f * progress - 0.5f) * WaveFormTextureView.this.amplitude;
            path.reset();

            //float multiplier = Math.min(1.0f, (progress / 3.0f * 2.0f) + (1.0f / 3.0f));

            for (float x = 0; x < width * density; x += density) {
                // parable to scale the wave, so it has its peak in the middle
                float scaling = (float) (-Math.pow(1 / mid * (x - mid), 2) + 1) *
                        (mIntensity * 0.9f + 0.1f) // prevent it from going into a "line"
                        ;
                float y = (float) (scaling * maxAmplitude * normedAmplitude * Math.sin(2 * Math.PI * (x / width) * frequency + phase));// + waveHeight);
                y += waveHeight;

                if (x == 0) {
                    path.moveTo(x, y);
                } else {
                    path.lineTo(x, y);
                    if (centerPointYValue == 0.0f && i == 0 && x > mid) {
                        centerPointYValue = 1 - (y / height);
                        //canvas.drawCircle(x, y, 5 * density, circlePaint);
                        ret = centerPointYValue;
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
            //this.phase += 0.03;
            this.phase += phaseDiff;
            //Ln.d("PHASEdiff %f", phaseDiff);
        }
        return ret;
    }


    float doDrawLine(Canvas canvas, boolean changePhase) {
        float y = (1 - mIntensity) * (height - 5 * density) + (3 * density);
        path.moveTo(0, y);
        path.lineTo(width * density, y);
        canvas.drawCircle(lineCirclePos, y, 5 * density, circlePaint);

        if (changePhase) {
            lineCirclePos += density * 2;
            if (lineCirclePos > width) {
                lineCirclePos = 0;
            }
        }
        canvas.drawPath(path, primaryLinePaint);
        return mIntensity;
    }

    @Subscribe
    public void shouldStopAnim(StopAnimEvent event) {
        Ln.d("Should stop anim");
        stopAnimWithoutStoppingWand();
    }

    @Subscribe
    public void rowClicked(RowClickedEvent event) {
        Ln.d("ROW CLICKED: %d, we are %d", event.row, index);
        if (event.row == index) {
            if (thread != null) {
                Ln.d("Toggling to stop");
                stopAnim();
            } else {
                Ln.d("toggling to start");
                startAnim();
            }
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

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Ln.d("onSurfaceTextureAvailable %d %d", width, height);
        synchronized (mLock) {
            mSurfaceTexture = surface;
        }
        bus.register(this);
        setSurfaceSize(width, height);
        lockAndDrawOnce();

    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Ln.d("onSurfaceTextureSizeChanged %d %d", width, height);
        setSurfaceSize(width, height);
        lockAndDrawOnce();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Ln.d("Surface %d destroyed", index);
        bus.unregister(this);
        stopAnim();
        synchronized (mLock) {
            mSurfaceTexture = null;
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    /*
    fast floating point exp function
     must initialize table with buildexptable before using

    Based on
     A Fast, Compact Approximation of the Exponential Function
     Nicol N. Schraudolph 1999
    Adapted to single precision to improve speed and added adjustment table to improve accuracy.
    Alrecenk 2014
     i = ay + b
     a = 2^(mantissa bits) / ln(2)   ~ 12102203
     b = (exponent bias) * 2^ ( mantissa bits) ~ 1065353216
     */
    public static float fastexp(float x) {
        final int temp = (int) (12102203 * x + 1065353216);
        return Float.intBitsToFloat(temp) * expadjust[(temp >> 15) & 0xff];
    }

    static float expadjust[];

    //build correction table to improve result in region of interest
    //if region of interest is large enough then improves result everywhere
    public static void buildexptable(double min, double max, double step) {
        expadjust = new float[256];
        int amount[] = new int[256];
        //calculate what adjustments should have been for values in region
        for (double x = min; x < max; x += step) {
            double exp = Math.exp(x);
            int temp = (int) (12102203 * x + 1065353216);
            int index = (temp >> 15) & 0xff;
            double fexp = Float.intBitsToFloat(temp);
            expadjust[index] += exp / fexp;
            amount[index]++;
        }
        //average them out to get adjustment table
        for (int k = 0; k < amount.length; k++) {
            expadjust[k] /= amount[k];
        }
    }

    private static float getLineYbasedOnPoints(float X, PointF p1, PointF p2) {
        final float denom = p2.x - p1.x;
        if (denom == 0.0f) {
            return p1.y;
        }
        // https://en.wikipedia.org/wiki/Linear_equation#Two-point_form
        // Y = y1 + ((y2 - y1)/(x2 - x1))*(X - x1)
        return p1.y + ((p2.y - p1.y) / (denom)) * (X - p1.x);
    }

    // must be accessed from one thread only
    private static Rect measureRect = new Rect();

    private void drawText(Canvas canvas, @NonNull String text) {
        if (thread == null) {
            paintTextLight.getTextBounds(text, 0, text.length(), measureRect);
            measureRect.inset(Math.round(-5 * density), Math.round(-5 * density));
            canvas.drawText(text, measureRect.width() / 2, (measureRect.height() / 2) + (6 * density), paintTextLight);
        } else {
            paintTextDark.getTextBounds(text, 0, text.length(), measureRect);
            measureRect.inset(Math.round(-5 * density), Math.round(-5 * density));
            canvas.drawText(text, measureRect.width() / 2, (measureRect.height() / 2) + (6 * density), paintTextDark);
        }
    }

    public static CoordinateFeature getFirstFeature() {
        return featureList.getFirst();
    }
}
