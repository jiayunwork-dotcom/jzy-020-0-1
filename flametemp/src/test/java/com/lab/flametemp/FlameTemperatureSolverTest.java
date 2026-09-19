package com.lab.flametemp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.lab.flametemp.balance.AtomBalance;
import com.lab.flametemp.balance.FuelRegistry;
import com.lab.flametemp.balance.Stoichiometry;
import com.lab.flametemp.solver.EnthalpyBalance;
import com.lab.flametemp.solver.FlameTempService;
import com.lab.flametemp.solver.FlameTemperatureSolver;
import com.lab.flametemp.solver.InvalidJobInputException;
import com.lab.flametemp.solver.IterationPoint;
import com.lab.flametemp.solver.SolveFailureReason;
import com.lab.flametemp.solver.SolveOutcome;
import com.lab.flametemp.solver.SolverConstants;
import com.lab.flametemp.thermo.ThermoTable;

/**
 * Physics-level regressions on the full solve pipeline (no HTTP, no DB).
 */
class FlameTemperatureSolverTest {

    private final FlameTempService service = new FlameTempService(
            new FuelRegistry(), new Stoichiometry(), new AtomBalance(),
            new EnthalpyBalance(new ThermoTable()), new FlameTemperatureSolver());

    @Test
    void methaneStoichiometricLandsInPinned2200Band() {
        SolveOutcome o = service.solve("CH4", 1.0, 298.15);
        assertThat(o.converged()).isTrue();
        assertThat(o.finalTemperatureK())
                .as("methane stoichiometric adiabatic flame temperature")
                .isBetween(SolverConstants.METHANE_STOICH_BAND_MIN_K,
                        SolverConstants.METHANE_STOICH_BAND_MAX_K);
    }

    @Test
    void leanAndRichBothBelowStoichiometric() {
        double stoich = service.solve("CH4", 1.0, 298.15).finalTemperatureK();
        double lean = service.solve("CH4", 0.8, 298.15).finalTemperatureK();
        double rich = service.solve("CH4", 1.2, 298.15).finalTemperatureK();
        assertThat(lean).isLessThan(stoich);
        assertThat(rich).isLessThan(stoich);
    }

    @Test
    void hotterInletRaisesFlameTemperatureByLessThanTheInletRise() {
        double base = service.solve("CH4", 1.0, 298.15).finalTemperatureK();
        double hotInlet = 400.0;
        double hot = service.solve("CH4", 1.0, hotInlet).finalTemperatureK();
        assertThat(hot).isGreaterThan(base);
        assertThat(hot - base)
                .as("flame temperature rise must be damped relative to inlet rise")
                .isLessThan(hotInlet - 298.15);
    }

    @Test
    void atomResidualsNegligible() {
        SolveOutcome o = service.solve("CH4", 1.0, 298.15);
        for (double r : o.atomResiduals()) {
            assertThat(Math.abs(r)).isLessThan(SolverConstants.ATOM_RESIDUAL_THRESHOLD);
        }
        SolveOutcome rich = service.solve("C2H6", 1.3, 300.0);
        for (double r : rich.atomResiduals()) {
            assertThat(Math.abs(r)).isLessThan(SolverConstants.ATOM_RESIDUAL_THRESHOLD);
        }
    }

    @Test
    void iterationCurveIsMonotoneAndLastStepBelowTolerance() {
        SolveOutcome o = service.solve("CH4", 1.0, 298.15);
        assertThat(o.iterations()).isNotEmpty();
        for (int i = 1; i < o.iterations().size(); i++) {
            double prev = Math.abs(o.iterations().get(i - 1).enthalpyResidualJPerMolFuel());
            double cur = Math.abs(o.iterations().get(i).enthalpyResidualJPerMolFuel());
            assertThat(cur).as("residual must strictly decrease at step %d", i + 1)
                    .isLessThan(prev);
        }
        IterationPoint last = o.lastIteration();
        assertThat(Math.abs(last.enthalpyResidualJPerMolFuel()))
                .isLessThanOrEqualTo(SolverConstants.ENTHALPY_TOLERANCE_J_PER_MOL_FUEL);
    }

    @Test
    void ethaneStoichiometricDiffersFromMethane() {
        double methane = service.solve("CH4", 1.0, 298.15).finalTemperatureK();
        double ethane = service.solve("C2H6", 1.0, 298.15).finalTemperatureK();
        assertThat(ethane).isNotEqualTo(methane);
        assertThat(Math.abs(ethane - methane)).isGreaterThan(10.0);
    }

    @Test
    void enthalpyCannotCloseAtTooHotInletJobFails() {
        // At the upper domain boundary products remain enthalpy-poor: no root,
        // the service must fail rather than print a fabricated temperature.
        SolveOutcome o = service.solve("CH4", 1.0, 3500.0);
        assertThat(o.converged()).isFalse();
        assertThat(o.finalTemperatureK()).isNull();
        assertThat(o.failureReason()).isEqualTo(SolveFailureReason.ROOT_NOT_BRACKETED);
    }

    @Test
    void unknownFuelRejectedBeforeIteration() {
        assertThatThrownBy(() -> service.solve("PROPANE", 1.0, 298.15))
                .isInstanceOf(InvalidJobInputException.class)
                .hasMessageContaining("unknown fuel");
    }

    @Test
    void nonPositiveEquivalenceRatioRejected() {
        for (double phi : new double[]{0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThatThrownBy(() -> service.solve("CH4", phi, 298.15))
                    .isInstanceOf(InvalidJobInputException.class);
        }
    }

    @Test
    void nonPositiveInletTemperatureRejected() {
        for (double tin : new double[]{0.0, -300.0, Double.NaN, Double.NEGATIVE_INFINITY}) {
            assertThatThrownBy(() -> service.solve("CH4", 1.0, tin))
                    .isInstanceOf(InvalidJobInputException.class);
        }
    }

    @Test
    void inletAbovePinnedDomainFailsJob() {
        SolveOutcome o = service.solve("CH4", 1.0, 3500.1);
        assertThat(o.converged()).isFalse();
        assertThat(o.failureReason()).isEqualTo(SolveFailureReason.INLET_TEMPERATURE_OUT_OF_DOMAIN);
    }

    @Test
    void productMoleFractionsSumToOne() {
        for (String fuel : new String[]{"CH4", "C2H6"}) {
            for (double phi : new double[]{0.7, 1.0, 1.4}) {
                SolveOutcome o = service.solve(fuel, phi, 298.15);
                double sum = o.products().moleFractions().values().stream()
                        .mapToDouble(Double::doubleValue).sum();
                assertThat(sum).as("sum of product mole fractions %s phi=%.1f", fuel, phi)
                        .isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-12));
            }
        }
    }
}
