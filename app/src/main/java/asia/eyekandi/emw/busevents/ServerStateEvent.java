package asia.eyekandi.emw.busevents;

/**
 * Created by mitja on 07/02/16.
 * Copyright(C) AMOK Products ApS Ltd
 */
public class ServerStateEvent {
    public static final int STATE_IDLE = 0;
    public static final int STATE_REPEATING_PATTERN = 1;
    public static final int STATE_NON_REPEATING = 2;
    public static final int STATE_ERROR = 3;
    public static final int PATTERN_ERROR_OR_IDLE = 0;
    public static final byte PATTERN_SERVER = (byte) 0xff;
    public final int moduleId;
    public final int messageId;
    public final int state;
    public final byte patternId;
    public final int MIC;

    public ServerStateEvent(final byte[] bytes) {
        if (bytes.length < 7) {
            moduleId = 0;
            messageId = 0;
            state = STATE_ERROR;
            patternId = PATTERN_ERROR_OR_IDLE;
            MIC = 0;
            return;
        }
        // first two bytes are version and length
        moduleId = bytes[2];
        messageId = bytes[3];
        state = bytes[4];
        patternId = bytes[5];
        MIC = bytes[6];
    }

    @Override
    public String toString() {
        return "moduleId=" + moduleId +
                "\nmessageId=" + messageId +
                "\nstate=" + getStateString() +
                "\npatternId=" + getPatternString() +
                "\nMIC=" + MIC;
    }

    public String getPatternString() {
        String patternStr;
        if (patternId == PATTERN_ERROR_OR_IDLE) {
            patternStr = "ERROR/IDLE";
        } else if (patternId == PATTERN_SERVER) {
            patternStr = "SERVER";
        } else {
            patternStr = String.format("0x%x", patternId);
        }
        return patternStr;
    }

    public String getStateString() {
        String stateStr;
        switch (state) {
            case STATE_IDLE:
                stateStr = "IDLE";
                break;
            case STATE_REPEATING_PATTERN:
                stateStr = "REPEATING";
                break;
            case STATE_NON_REPEATING:
                stateStr = "NON-REPEATING";
                break;
            case STATE_ERROR:
                stateStr = "ERROR";
                break;
            default:
                stateStr = Integer.toString(state);
                break;
        }
        return stateStr;
    }
}
