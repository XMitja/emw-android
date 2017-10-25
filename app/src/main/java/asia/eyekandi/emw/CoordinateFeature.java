package asia.eyekandi.emw;

import android.annotation.SuppressLint;

import java.util.Random;

/**
 * Created by mitja on 30/03/16.
 * Copyright AMOK Products ApS
 */
public class CoordinateFeature {
    private static int seq;
    // width varies between features
    protected float width;
    // height is always the same
    protected static float height;
    private final String name;
    // in local coordinate 0, 0..height
    //protected float entryPoint = 20.0f;
    public float phaseChange = -0.8f;
    public float leftEdgePositionInParent = 0.0f;
    public float padding = 50;
    protected static volatile float mIntensity;
    protected float halfWidth;
    public static Random rng = new Random();

    @SuppressLint("DefaultLocale")
    public CoordinateFeature(String name) {
        this.name = String.format("%s-%d", name, ++seq);
    }

    public static void setIntensity(float intensity) {
        CoordinateFeature.mIntensity = intensity;
    }

    public void setWidth(float width) {
        this.width = width;
        this.halfWidth = width / 2;
    }

    public static void setHeight(float height) {
        CoordinateFeature.height = height;
    }

    // canvas has inverted Y coordinates
    public float getCanvasY(float canvasX) {
        float ret = height - calculateY(canvasX - leftEdgePositionInParent);
        return clamp(ret, 0, height);
    }

    public float getCanvasExitY() {
        float exitPoint = calculateY(width);
        float ret = height - exitPoint;
        return clamp(ret, 0, height);
    }

    public float getCanvasExitX() {
        return leftEdgePositionInParent + width;
    }

    public float getCanvasEntryY() {
        float entryPoint = calculateY(0);
        float ret = height - entryPoint;
        return clamp(ret, 0, height);
    }

    public float getCanvasEntryX() {
        return leftEdgePositionInParent;
    }

    // override this to customize shape
    protected float calculateY(float myX) {
        if (myX < width / 2) {
            return 2 * myX + getEntryPoint();
        }
        return height - myX + getEntryPoint();
    }

    public String getName() {
        return name;
    }

    public float getNextFeaturePositionInParentCoordinates() {
        return leftEdgePositionInParent + width + padding;
    }

    public float getEntryPoint() {
        return height * mIntensity;
    }

    public float getParentEntryPoint() {
        return height - getEntryPoint();
    }

    public boolean isParentCoordinateWithinFeature(float parentX) {
        return parentX > leftEdgePositionInParent && parentX < (leftEdgePositionInParent + width);
    }

    // if true, we can remove feature
    public boolean hasTravelledPastParent() {
        return (leftEdgePositionInParent + width + padding) < 0;
    }

    public void changePhase() {
        // "moves" the box to the left
        //Ln.d("DENSITY %f", WaveFormTextureView.density);
        leftEdgePositionInParent += phaseChange;
    }

    public static float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }

}
