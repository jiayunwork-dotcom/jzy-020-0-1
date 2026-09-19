package com.lab.flametemp.solver;

import java.util.List;

import com.lab.flametemp.balance.Mixture;

/**
 * Outcome of one constant-pressure adiabatic balance. A failed outcome
 * deliberately carries no final temperature: the service never invents a
 * plausible-looking number.
 */
public record SolveOutcome(
        boolean converged,
        Double finalTemperatureK,
        List<IterationPoint> iterations,
        double reactantEnthalpyJPerMolFuel,
        double enthalpyToleranceJPerMolFuel,
        double[] atomResiduals,
        double atomThreshold,
        SolveFailureReason failureReason,
        String failureMessage,
        Mixture reactants,
        Mixture products) {

    public static SolveOutcome converged(double finalTemperatureK,
                                         List<IterationPoint> iterations,
                                         double reactantEnthalpy,
                                         double[] atomResiduals,
                                         Mixture reactants,
                                         Mixture products) {
        return new SolveOutcome(true, finalTemperatureK, List.copyOf(iterations),
                reactantEnthalpy, SolverConstants.ENTHALPY_TOLERANCE_J_PER_MOL_FUEL,
                atomResiduals, SolverConstants.ATOM_RESIDUAL_THRESHOLD,
                null, null, reactants, products);
    }

    public static SolveOutcome failed(SolveFailureReason reason, String message,
                                      List<IterationPoint> iterations,
                                      double reactantEnthalpy,
                                      double[] atomResiduals,
                                      Mixture reactants,
                                      Mixture products) {
        return new SolveOutcome(false, null,
                iterations == null ? List.of() : List.copyOf(iterations),
                reactantEnthalpy, SolverConstants.ENTHALPY_TOLERANCE_J_PER_MOL_FUEL,
                atomResiduals, SolverConstants.ATOM_RESIDUAL_THRESHOLD,
                reason, message, reactants, products);
    }

    public IterationPoint lastIteration() {
        return iterations.isEmpty() ? null : iterations.get(iterations.size() - 1);
    }
}
