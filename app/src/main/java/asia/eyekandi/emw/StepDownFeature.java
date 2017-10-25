package asia.eyekandi.emw;

import roboguice.util.Ln;

/**
 * Created by mitja on 30/03/16.
 * Copyright AMOK Products ApS
 */
public class StepDownFeature extends CoordinateFeature {
    public float gaussHeight;
    // smaller number is steeper
    public float gaussSteep;

    public float getAbsoluteGaussHeight() {
        return 100*calculateY(0)/height;
    }

    public float getAbsoluteGaussEnd() {
        return 100*calculateY(20000)/height;
    }

    public StepDownFeature(StepUpFeature fromStep) {
        super("Gauss");
        gaussSteep = fromStep.gaussSteep;
        setWidth(150f + gaussSteep * 2.1f);
        gaussHeight = fromStep.gaussHeight;
    }

    @Override
    protected float calculateY(float myX) {
        return (float) (gaussHeight *
                Math.exp(
                        -(myX * myX)
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
