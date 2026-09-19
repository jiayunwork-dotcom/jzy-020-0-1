package com.lab.flametemp.solver;

/** Why a solve failed; stored on the job and returned with its type. */
public enum SolveFailureReason {

    /** Elemental balance did not close below the pinned atom threshold. */
    ATOM_BALANCE_NOT_CLOSED,

    /** The inlet temperature lies outside the pinned thermodynamic domain. */
    INLET_TEMPERATURE_OUT_OF_DOMAIN,

    /** A Cp polynomial was asked outside its pinned temperature range. */
    POLYNOMIAL_OUT_OF_DOMAIN,

    /**
     * No sign change brackets the root inside the domain, e.g. an inlet hot
     * enough that even at the upper bound the products stay enthalpy-poor.
     */
    ROOT_NOT_BRACKETED,

    /** Bisection exhausted its evaluation budget without reaching tolerance. */
    MAX_STEPS_EXCEEDED
}
