package com.lab.flametemp.thermo;

/**
 * Thrown when a Cp/enthalpy polynomial is evaluated outside its pinned
 * temperature range. Per spec this fails the job rather than extrapolating
 * and emitting a composition at an out-of-range temperature.
 */
public class ThermoDomainException extends RuntimeException {

    public ThermoDomainException(Species species, double t, double tMin, double tMax) {
        super(String.format(
                "temperature %.3f K is outside the pinned Cp polynomial range "
                        + "[%.1f, %.1f] K for species %s", t, tMin, tMax, species.name()));
    }
}
