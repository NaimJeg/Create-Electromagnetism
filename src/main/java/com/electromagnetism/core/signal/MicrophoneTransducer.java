package com.electromagnetism.core.signal;

import com.electromagnetism.core.model.Numbers;

/** Passive calibrated Thevenin microphone. Acoustic energy supplies the output. */
public record MicrophoneTransducer(double pascalsPerFullScale, double voltsPerPascal,
                                   double resistanceOhms, double maximumVolts) {
    public MicrophoneTransducer {
        Numbers.positive("calibration", pascalsPerFullScale);
        Numbers.positive("sensitivity", voltsPerPascal);
        Numbers.positive("resistance", resistanceOhms);
        Numbers.positive("maximum voltage", maximumVolts);
        Numbers.finite("full scale voltage", pascalsPerFullScale * voltsPerPascal);
    }
    public double openCircuitVoltage(double pcm) {
        Numbers.range("PCM", pcm, -1, 1);
        return Math.clamp(pcm * pascalsPerFullScale * voltsPerPascal, -maximumVolts, maximumVolts);
    }
    /** Positive current flows out of the positive terminal into the load. */
    public double outputCurrent(double pcm, double terminalVoltage) {
        Numbers.finite("terminal voltage", terminalVoltage);
        return Numbers.finite("output current", (openCircuitVoltage(pcm) - terminalVoltage) / resistanceOhms);
    }
}
