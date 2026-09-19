package com.lab.flametemp.solver;

/**
 * One recorded temperature iteration. The sequence a job accumulates contains
 * only steps that strictly lowered the absolute enthalpy residual, so the
 * curve is monotone by construction.
 *
 * @param stepNo 1-based index within the accepted sequence
 * @param temperatureK temperature evaluated, K
 * @param enthalpyResidualJPerMolFuel H_products - H_reactants, J per mol fuel
 */
public record IterationPoint(int stepNo, double temperatureK,
                             double enthalpyResidualJPerMolFuel) {
}
