package com.electromagnetism.core.model;

/** Single thermal mass with linear cooling; temperatures in K and energy in J. */
public final class LumpedThermalModel {
    private LumpedThermalModel() {}

    public record Parameters(double heatCapacity, double thermalConductance, double ambientKelvin) {
        public Parameters {
            Numbers.positive("heat capacity (J/K)", heatCapacity);
            Numbers.nonNegative("thermal conductance (W/K)", thermalConductance);
            Numbers.nonNegative("ambient temperature (K)", ambientKelvin);
            Numbers.nonNegative("cooling rate (1/s)", thermalConductance / heatCapacity);
        }
    }

    public record State(double temperatureKelvin) {
        public State { Numbers.nonNegative("temperature (K)", temperatureKelvin); }
    }

    /** Exact integration for constant nonnegative heating power during this interval. */
    public static State advance(Parameters p, State initial, double heatingWatts, double seconds) {
        Numbers.nonNegative("heating power (W)", heatingWatts);
        Numbers.positive("duration (s)", seconds);
        double rate = p.thermalConductance() / p.heatCapacity();
        double exponent = rate * seconds;
        double decay = -Math.expm1(-exponent);
        // For small exponents avoid dividing power by a tiny thermal conductance.
        double heating = exponent < 1e-5
                ? heatingWatts / p.heatCapacity() * seconds * (exponent == 0 ? 1 : decay / exponent)
                : heatingWatts / p.thermalConductance() * decay;
        return new State(initial.temperatureKelvin()
                + (p.ambientKelvin() - initial.temperatureKelvin()) * decay + heating);
    }
}
