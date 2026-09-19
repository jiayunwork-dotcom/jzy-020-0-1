package com.lab.flametemp.job;

import com.lab.flametemp.solver.IterationPoint;

/**
 * Full stored job, retrieved by id. The record deliberately keeps the
 * complete iteration curve and composition: a job is one balance, never a
 * bundle of unrelated single points.
 */
public record JobRecord(
        long id,
        String fuel,
        double equivalenceRatio,
        double inletTemperatureK,
        String status,
        Double finalTemperatureK,
        double enthalpyToleranceJPerMolFuel,
        double atomResidualC,
        double atomResidualH,
        double atomResidualO,
        double atomResidualN,
        double atomThreshold,
        String failureReason,
        String failureMessage,
        String createdAt,
        java.util.List<IterationPoint> iterations,
        java.util.List<SpeciesEntry> species,
        double reactantEnthalpyJPerMolFuel) {

    public boolean converged() {
        return "CONVERGED".equals(status);
    }

    /** One species line: where it sat and how much of it was present. */
    public record SpeciesEntry(String species, String phase, double moles, double moleFraction) {
    }
}
