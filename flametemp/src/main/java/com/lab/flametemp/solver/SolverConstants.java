package com.lab.flametemp.solver;

import com.lab.flametemp.thermo.ThermoTable;

/**
 * Pinned numerical criteria of the balance service. They are constants, not
 * request parameters: every job reports the values it was judged against.
 */
public final class SolverConstants {

    private SolverConstants() {
    }

    /**
     * Enthalpy closure tolerance: |H_products(T) - H_reactants(Tin)| must not
     * exceed this, in joules per mole of fuel fed.
     */
    public static final double ENTHALPY_TOLERANCE_J_PER_MOL_FUEL = 10.0;

    /**
     * Atom closure threshold: each of C/H/O/N (products minus reactants) must
     * stay below this, in mol of atoms per mole of fuel.
     */
    public static final double ATOM_RESIDUAL_THRESHOLD = 1.0e-9;

    /** Cap on recorded (strictly residual-decreasing) iterations. */
    public static final int MAX_ACCEPTED_ITERATIONS = 50;

    /** Cap on bracket evaluations including probes that did not improve the best residual. */
    public static final int MAX_TEMPERATURE_PROBES = 200;

    /** Pinned acceptance band for the methane stoichiometric demo, K. */
    public static final double METHANE_STOICH_BAND_MIN_K = 2200.0;
    public static final double METHANE_STOICH_BAND_MAX_K = 2400.0;

    /** Reference inlet temperature of the built-in demo, K. */
    public static final double DEMO_INLET_TEMPERATURE_K = 298.15;

    public static double domainMin() {
        return ThermoTable.DOMAIN_T_MIN;
    }

    public static double domainMax() {
        return ThermoTable.DOMAIN_T_MAX;
    }
}
