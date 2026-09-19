package com.lab.flametemp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.lab.flametemp.balance.AtomBalance;
import com.lab.flametemp.balance.FuelDefinition;
import com.lab.flametemp.balance.FuelRegistry;
import com.lab.flametemp.balance.Mixture;
import com.lab.flametemp.balance.Stoichiometry;
import com.lab.flametemp.thermo.Species;

class StoichiometryTest {

    private final Stoichiometry stoich = new Stoichiometry();
    private final AtomBalance atoms = new AtomBalance();
    private final FuelRegistry fuels = new FuelRegistry();

    @Test
    void methaneStoichiometricCompositionAndMolesNeedNotBeConserved() {
        var b = stoich.balance(fuels.find("CH4"), 1.0);
        // CH4 + 2 O2 + 7.5238 N2 -> CO2 + 2 H2O + 7.5238 N2
        assertThat(b.products().molesOf(Species.CO2)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(b.products().molesOf(Species.H2O)).isCloseTo(2.0, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(b.products().molesOf(Species.O2)).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(b.products().molesOf(Species.N2))
                .as("inert N2: product N2 equals inlet N2")
                .isCloseTo(b.reactants().molesOf(Species.N2), org.assertj.core.data.Offset.offset(1e-12));

        // Closure is on atoms, not total moles. Here total moles do change:
        // lean CH4: 1+2.5+9.405 = 12.905 reactant -> 1+2+0.5+9.405 = 12.905
        // product (coincidentally equal). Use ethane stoich, where they differ:
        var ethane = stoich.balance(fuels.find("C2H6"), 1.0);
        // 1 + 3.5 + 13.167 = 17.667 -> 2 + 3 + 13.167 = 18.167
        assertThat(ethane.products().totalMoles())
                .as("hydrocarbon combustion may change total mole count")
                .isNotCloseTo(ethane.reactants().totalMoles(), org.assertj.core.data.Offset.offset(1e-6));
        // and the atom residual is still exactly zero
        assertThat(atoms.maxAbsoluteResidual(ethane.reactants(), ethane.products()))
                .isLessThan(1e-12);
    }

    @Test
    void ethaneStoichiometricComposition() {
        var b = stoich.balance(fuels.find("C2H6"), 1.0);
        // C2H6 + 3.5 O2 -> 2 CO2 + 3 H2O
        assertThat(b.products().molesOf(Species.CO2)).isCloseTo(2.0, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(b.products().molesOf(Species.H2O)).isCloseTo(3.0, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(b.products().molesOf(Species.O2)).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-12));
    }

    @Test
    void richSideCarriesUnburnedFuelAndStillCloses() {
        var b = stoich.balance(fuels.find("CH4"), 1.2);
        double burned = b.burnedFraction();
        assertThat(burned).isCloseTo(1.0 / 1.2, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(b.products().molesOf(Species.CH4)).isCloseTo(1.0 - burned,
                org.assertj.core.data.Offset.offset(1e-12));
        double max = atoms.maxAbsoluteResidual(b.reactants(), b.products());
        assertThat(max).isLessThan(1e-12);
    }

    @Test
    void atomClosureForAllRegimes() {
        for (FuelDefinition f : new FuelDefinition[]{fuels.find("CH4"), fuels.find("C2H6")}) {
            for (double phi : new double[]{0.5, 0.8, 1.0, 1.2, 2.0}) {
                var b = stoich.balance(f, phi);
                double[] r = atoms.residuals(b.reactants(), b.products());
                for (double v : r) {
                    assertThat(Math.abs(v))
                            .as("atom residual %s phi=%.1f", f.formula(), phi)
                            .isLessThan(1e-10);
                }
            }
        }
    }

    @Test
    void nitrogenRuleHoldsUnderExcessAir() {
        for (double phi : new double[]{0.6, 0.9, 1.1, 1.5}) {
            var b = stoich.balance(fuels.find("C2H6"), phi);
            assertThat(b.products().molesOf(Species.N2))
                    .isCloseTo(b.reactants().molesOf(Species.N2),
                            org.assertj.core.data.Offset.offset(1e-12));
            // no other N-bearing species exist: all N lives in N2
            double nReact = b.reactants().molesOf(Species.N2) * 2;
            double nProd = b.products().molesOf(Species.N2) * 2;
            assertThat(nProd - nReact).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-10));
        }
    }
}
