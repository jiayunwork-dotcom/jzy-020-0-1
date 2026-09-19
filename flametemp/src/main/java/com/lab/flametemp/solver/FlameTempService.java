package com.lab.flametemp.solver;

import org.springframework.stereotype.Service;

import com.lab.flametemp.balance.AtomBalance;
import com.lab.flametemp.balance.FuelDefinition;
import com.lab.flametemp.balance.FuelRegistry;
import com.lab.flametemp.balance.Stoichiometry;
import com.lab.flametemp.thermo.ThermoDomainException;
import com.lab.flametemp.thermo.ThermoTable;

/**
 * Orchestrates one balance: input validation happens here before iteration,
 * then stoichiometry -> atom check -> reactant enthalpy -> temperature solve.
 *
 * <p>This class contains no persistence; job storage wraps it.
 */
@Service
public class FlameTempService {

    private final FuelRegistry fuelRegistry;
    private final Stoichiometry stoichiometry;
    private final AtomBalance atomBalance;
    private final EnthalpyBalance enthalpyBalance;
    private final FlameTemperatureSolver solver;

    public FlameTempService(FuelRegistry fuelRegistry,
                            Stoichiometry stoichiometry,
                            AtomBalance atomBalance,
                            EnthalpyBalance enthalpyBalance,
                            FlameTemperatureSolver solver) {
        this.fuelRegistry = fuelRegistry;
        this.stoichiometry = stoichiometry;
        this.atomBalance = atomBalance;
        this.enthalpyBalance = enthalpyBalance;
        this.solver = solver;
    }

    /**
     * Solves one constant-pressure adiabatic flame temperature.
     *
     * @param fuelFormula fuel name (CH4, C2H6, ...)
     * @param equivalenceRatio positive; 1.0 is stoichiometric
     * @param inletTemperatureK positive thermodynamic temperature
     * @throws IllegalArgumentException on invalid input (typed upstream as 422)
     */
    public SolveOutcome solve(String fuelFormula, double equivalenceRatio, double inletTemperatureK) {
        FuelDefinition fuel = fuelRegistry.find(fuelFormula);
        if (fuel == null) {
            throw new InvalidJobInputException("UNKNOWN_FUEL",
                    "unknown fuel '" + fuelFormula + "'; supported: CH4, C2H6");
        }
        if (!Double.isFinite(equivalenceRatio) || equivalenceRatio <= 0.0) {
            throw new InvalidJobInputException("INVALID_EQUIVALENCE_RATIO",
                    "equivalence ratio must be positive and finite, got " + equivalenceRatio);
        }
        if (!Double.isFinite(inletTemperatureK) || inletTemperatureK <= 0.0) {
            throw new InvalidJobInputException("INVALID_INLET_TEMPERATURE",
                    "inlet temperature must be a positive thermodynamic temperature, got "
                            + inletTemperatureK);
        }

        Stoichiometry.Balance balanced = stoichiometry.balance(fuel, equivalenceRatio);
        double[] atomResiduals = atomBalance.residuals(balanced.reactants(), balanced.products());
        double maxAtomResidual = maxAbs(atomResiduals);
        if (maxAtomResidual >= SolverConstants.ATOM_RESIDUAL_THRESHOLD) {
            return SolveOutcome.failed(SolveFailureReason.ATOM_BALANCE_NOT_CLOSED,
                    String.format("atom residual %.3e exceeds pinned threshold %.0e",
                            maxAtomResidual, SolverConstants.ATOM_RESIDUAL_THRESHOLD),
                    java.util.List.of(), Double.NaN, atomResiduals,
                    balanced.reactants(), balanced.products());
        }

        if (!ThermoTable.inDomain(inletTemperatureK)) {
            return SolveOutcome.failed(SolveFailureReason.INLET_TEMPERATURE_OUT_OF_DOMAIN,
                    String.format("inlet temperature %.3f K is outside the pinned Cp domain [%.0f, %.0f] K",
                            inletTemperatureK,
                            ThermoTable.DOMAIN_T_MIN, ThermoTable.DOMAIN_T_MAX),
                    java.util.List.of(), Double.NaN, atomResiduals,
                    balanced.reactants(), balanced.products());
        }

        double reactantH;
        try {
            reactantH = enthalpyBalance.reactantEnthalpy(balanced.reactants(), inletTemperatureK);
        } catch (ThermoDomainException e) {
            return SolveOutcome.failed(SolveFailureReason.INLET_TEMPERATURE_OUT_OF_DOMAIN,
                    e.getMessage(), java.util.List.of(), Double.NaN, atomResiduals,
                    balanced.reactants(), balanced.products());
        }

        try {
            var residual = enthalpyBalance.residual(balanced.products(), reactantH);
            return solver.solve(residual,
                    SolverConstants.ENTHALPY_TOLERANCE_J_PER_MOL_FUEL,
                    atomResiduals, balanced.reactants(), balanced.products(), reactantH);
        } catch (ThermoDomainException e) {
            return SolveOutcome.failed(SolveFailureReason.POLYNOMIAL_OUT_OF_DOMAIN,
                    e.getMessage(), java.util.List.of(), reactantH, atomResiduals,
                    balanced.reactants(), balanced.products());
        }
    }

    private static double maxAbs(double[] v) {
        double m = 0.0;
        for (double x : v) {
            m = Math.max(m, Math.abs(x));
        }
        return m;
    }
}
