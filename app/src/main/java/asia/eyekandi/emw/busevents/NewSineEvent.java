package asia.eyekandi.emw.busevents;

/**
 * Created by mitja on 26/02/16.
 * Copyright AMOK Products ApS
 */
public class NewSineEvent {
    public final int amp;
    public final int period;
    public final int center;
    public final int threadIndex;

    public NewSineEvent(int amp, int period, int center, int threadIndex) {
        this.amp = amp;
        this.period = period;
        this.center = center;
        this.threadIndex = threadIndex;
    }
}
