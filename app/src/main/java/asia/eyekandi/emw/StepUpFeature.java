package asia.eyekandi.emw;

import roboguice.util.Ln;

/**
 * Created by mitja on 30/03/16.
 * Copyright AMOK Products ApS
 */
public class StepUpFeature extends CoordinateFeature {
    final float gaussHeight;
    // smaller number is steeper
    public final float gaussSteep;

    public StepUpFeature() {
        super("Gauss");
        gaussSteep = 40.0f + rng.nextInt(15);
        setWidth(150f + gaussSteep * 2.1f);
        float gaussStart = rng.nextFloat() / 2f + 0.2f;
        gaussHeight = height * gaussStart;
        padding = 250;
    }

    public float getAbsoluteGaussHeight() {
        return 100*calculateY(0)/height;
    }

    public float getAbsoluteGaussEnd() {
        return 100*calculateY(width)/height;
    }

    @Override
    protected float calculateY(float myX) {
        final float xPhase = myX - width;
        return (float) (gaussHeight *
                Math.exp(
                        -(xPhase * xPhase)
                                /
                                (2 * gaussSteep * gaussSteep)
                ))
                + getEntryPoint();
        //Ln.d("FEATDEBUG %s X: %f Y: %f", getName(), myX, y);
    }

    @Override
    public float getEntryPoint() {
        return height * mIntensity * 0.8f;
    }
}
