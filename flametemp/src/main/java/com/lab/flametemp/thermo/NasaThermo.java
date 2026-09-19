package com.lab.flametemp.thermo;

/**
 * NASA-7 thermodynamic table for one species: a low and a high segment with
 * their common join temperature.
 *
 * Coefficients are GRI-Mech 3.0 (Burke et al. compilation, distributed with
 * Cantera {@code data/gri30.yaml}): reactants and products draw from this
 * single source.
 */
public record NasaThermo(Species species, double tMin, double tJoin, double tMax,
                         NasaSegment low, NasaSegment high) {

    private NasaSegment segment(double t) {
        if (t < tMin || t > tMax) {
            throw new ThermoDomainException(species, t, tMin, tMax);
        }
        // Use the low segment up to and including the join temperature.
        return t <= tJoin ? low : high;
    }

    public double cp(double t) {
        return segment(t).cp(t, ThermoTable.R);
    }

    public double enthalpy(double t) {
        return segment(t).enthalpy(t, ThermoTable.R);
    }
}
