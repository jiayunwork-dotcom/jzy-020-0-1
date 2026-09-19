package com.lab.flametemp.thermo;

/**
 * One NASA-7 temperature segment. Molar Cp and molar enthalpy are:
 *
 * <pre>
 * Cp/R = a1 + a2*T + a3*T^2 + a4*T^3 + a5*T^4
 * H/(R*T) = a1 + a2*T/2 + a3*T^2/3 + a4*T^3/4 + a5*T^4/5 + a6/T
 * </pre>
 *
 * so that H(T) = R*(a1*T + a2*T^2/2 + ... + a5*T^5/5 + a6). The integration
 * constant a6 already contains the standard formation enthalpy at 298.15 K;
 * reactants and products use the very same coefficients, satisfying the
 * single-coefficient-set requirement.
 */
public record NasaSegment(double tMin, double tMax, double[] a) {

    public boolean contains(double t) {
        return t >= tMin && t <= tMax;
    }

    public double cp(double t, double r) {
        return r * (a[0] + a[1] * t + a[2] * t * t + a[3] * t * t * t
                + a[4] * t * t * t * t);
    }

    /** Absolute molar enthalpy [J/mol], formation enthalpy included via a6. */
    public double enthalpy(double t, double r) {
        double t2 = t * t;
        return r * (a[0] * t
                + a[1] * t2 / 2.0
                + a[2] * t2 * t / 3.0
                + a[3] * t2 * t2 / 4.0
                + a[4] * t2 * t2 * t / 5.0
                + a[5]);
    }
}
