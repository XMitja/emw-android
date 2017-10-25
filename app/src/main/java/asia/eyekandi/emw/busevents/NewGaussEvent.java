package asia.eyekandi.emw.busevents;

import asia.eyekandi.emw.CoordinateFeature;
import asia.eyekandi.emw.StepUpFeature;

/**
 * Created by mitja on 26/02/16.
 * Copyright AMOK Products ApS
 */
public class NewGaussEvent {
    public final int start;
    public final int end;
    public final int steep;
    public final int threadIndex;

    public NewGaussEvent(int start, int end, int steep, int threadIndex) {
        this.start = start;
        this.end = end;
        this.steep = steep;
        this.threadIndex = threadIndex;
    }

}
