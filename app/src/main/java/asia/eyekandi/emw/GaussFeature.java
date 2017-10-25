package asia.eyekandi.emw;

/**
 * Created by mitja on 30/03/16.
 * Copyright AMOK Products ApS
 */
public class GaussFeature extends CoordinateFeature {
    float gaussHeight;
    // smaller number is steeper
    float gaussSteep;

    public GaussFeature() {
        super("Gauss");
        gaussSteep = 40.0f + rng.nextInt(15);
        setWidth(150f + gaussSteep * 2.1f);
        float heightCoefficient = rng.nextFloat() / 2f + 0.2f;
        gaussHeight = height * heightCoefficient;
    }

    @Override
    protected float calculateY(float myX) {
        final float xPhase = myX - halfWidth;
        return (float) (gaussHeight *
                Math.exp(
                        -(xPhase * xPhase)
                                /
                                (2 * gaussSteep * gaussSteep * (1 + mIntensity * 2)
                                )))
                + getEntryPoint();
        //Ln.d("FEATDEBUG %s X: %f Y: %f", getName(), myX, y);
    }

    @Override
    public float getEntryPoint() {
        return height * mIntensity * 0.8f;
    }
}
