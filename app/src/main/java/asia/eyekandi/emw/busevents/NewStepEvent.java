package asia.eyekandi.emw.busevents;

import asia.eyekandi.emw.CoordinateFeature;
import asia.eyekandi.emw.StepDownFeature;
import asia.eyekandi.emw.StepUpFeature;

/**
 * Created by mitja on 26/02/16.
 * Copyright AMOK Products ApS
 */
public class NewStepEvent {
    public final int start;
    public final int steep;
    public final int end;

    @Override
    public String toString() {
        return "NewStepEvent{" +
                "start=" + start +
                ", steep=" + steep +
                ", end=" + end+
                ", threadIndex=" + threadIndex +
                '}';
    }

    public final int threadIndex;

    public static final int START_AT_CURRENT_LEVEL = 0xee;

    public NewStepEvent(int start, int steep, int end, int threadIndex) {
        this.start = start;
        this.steep = steep;
        this.end = end;
        this.threadIndex = threadIndex;
    }

    public NewStepEvent(CoordinateFeature feature, int threadIndex) {
        this.threadIndex = threadIndex;
        if(feature instanceof StepUpFeature) {
            StepUpFeature stepUpFeature = (StepUpFeature) feature;
            this.start = (int) stepUpFeature.getAbsoluteGaussHeight();
            this.steep = (int) stepUpFeature.gaussSteep;
            this.end = (int) stepUpFeature.getAbsoluteGaussEnd();
        }else if(feature instanceof StepDownFeature) {
            StepDownFeature stepDownFeature = (StepDownFeature) feature;
            this.start = (int) stepDownFeature.getAbsoluteGaussHeight();
            this.steep = (int) stepDownFeature.gaussSteep;
            this.end = (int) stepDownFeature.getAbsoluteGaussEnd();
        }else{
            throw new IllegalArgumentException("Bad coordinate feature");
        }
    }

}
