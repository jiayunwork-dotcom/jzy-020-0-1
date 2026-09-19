package com.lab.flametemp.thermo;

import java.util.EnumMap;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Built-in temperature polynomials and the pinned service temperature domain.
 *
 * <p>NASA-7 coefficients, GRI-Mech 3.0 (as distributed by Cantera,
 * {@code data/gri30.yaml}). This is the one coefficient set used for both
 * reactants and products; there is deliberately no second table.
 *
 * <p>The service pins evaluation to [{@value #DOMAIN_T_MIN} K,
 * {@value #DOMAIN_T_MAX} K]: asking a polynomial for Cp outside the table
 * raises {@link ThermoDomainException} and fails the job.
 */
@Component
public class ThermoTable {

    /** Universal gas constant, J mol^-1 K^-1 (CODATA). */
    public static final double R = 8.314462618;

    /** Pinned thermodynamic temperature domain of the service, K. */
    public static final double DOMAIN_T_MIN = 200.0;
    public static final double DOMAIN_T_MAX = 3500.0;

    private final Map<Species, NasaThermo> tables = new EnumMap<>(Species.class);

    public ThermoTable() {
        put(Species.CH4, 200, 1000, 3500,
                new double[]{5.14987613, -0.0136709788, 4.91800599e-05, -4.84743026e-08, 1.66693956e-11, -1.02466476e+04, -4.64130376},
                new double[]{0.074851495, 0.0133909467, -5.73285809e-06, 1.22292535e-09, -1.01815235e-13, -9468.34459, 18.437318});
        put(Species.C2H6, 200, 1000, 3500,
                new double[]{4.29142492, -5.5015427e-03, 5.99438288e-05, -7.08466285e-08, 2.68685771e-11, -1.15222055e+04, 2.66682316},
                new double[]{1.0718815, 0.0216852677, -1.00256067e-05, 2.21412001e-09, -1.9000289e-13, -1.14263932e+04, 15.1156107});
        put(Species.O2, 200, 1000, 3500,
                new double[]{3.78245636, -2.99673416e-03, 9.84730201e-06, -9.68129509e-09, 3.24372837e-12, -1063.94356, 3.65767573},
                new double[]{3.28253784, 1.48308754e-03, -7.57966669e-07, 2.09470555e-10, -2.16717794e-14, -1088.45772, 5.45323129});
        // N2 low segment is tabulated from 300 K in GRI-Mech; [200,300) lies
        // within the global domain and the low-segment polynomial covers it.
        put(Species.N2, 200, 1000, 5000,
                new double[]{3.298677, 1.4082404e-03, -3.963222e-06, 5.641515e-09, -2.444854e-12, -1020.8999, 3.950372},
                new double[]{2.92664, 1.4879768e-03, -5.68476e-07, 1.0097038e-10, -6.753351e-15, -922.7977, 5.980528});
        put(Species.CO2, 200, 1000, 3500,
                new double[]{2.35677352, 8.98459677e-03, -7.12356269e-06, 2.45919022e-09, -1.43699548e-13, -4.83719697e+04, 9.90105222},
                new double[]{3.85746029, 4.41437026e-03, -2.21481404e-06, 5.23490188e-10, -4.72084164e-14, -4.8759166e+04, 2.27163806});
        put(Species.H2O, 200, 1000, 3500,
                new double[]{4.19864056, -2.0364341e-03, 6.52040211e-06, -5.48797062e-09, 1.77197817e-12, -3.02937267e+04, -0.849032208},
                new double[]{3.03399249, 2.17691804e-03, -1.64072518e-07, -9.7041987e-11, 1.68200992e-14, -3.00042971e+04, 4.9667701});
    }

    private void put(Species species, int tMin, int tJoin, int tMax, double[] low, double[] high) {
        NasaSegment lo = new NasaSegment(tMin, tJoin, low);
        NasaSegment hi = new NasaSegment(tJoin, tMax, high);
        tables.put(species, new NasaThermo(species, tMin, tJoin, tMax, lo, hi));
    }

    public NasaThermo forSpecies(Species species) {
        return tables.get(species);
    }

    /** Constant-pressure molar heat capacity [J mol^-1 K^-1] at {@code t} K. */
    public double cp(Species species, double t) {
        return tables.get(species).cp(t);
    }

    /**
     * Absolute molar enthalpy [J/mol] at {@code t} K: standard formation
     * enthalpy plus the Cp integral from 298.15 K, both encoded by the
     * NASA polynomial (reactants and products use this same call).
     */
    public double enthalpy(Species species, double t) {
        return tables.get(species).enthalpy(t);
    }

    /** True if a temperature lies inside the pinned service domain. */
    public static boolean inDomain(double t) {
        return Double.isFinite(t) && t >= DOMAIN_T_MIN && t <= DOMAIN_T_MAX;
    }
}
