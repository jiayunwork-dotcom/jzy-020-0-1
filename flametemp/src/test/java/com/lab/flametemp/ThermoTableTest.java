package com.lab.flametemp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

import com.lab.flametemp.thermo.Species;
import com.lab.flametemp.thermo.ThermoTable;

/**
 * The single NASA-7 coefficient set must reproduce tabulated formation
 * enthalpies at 298.15 K and sensible-enthalpy checkpoints. Reactants and
 * products both go through this table.
 */
class ThermoTableTest {

    private final ThermoTable table = new ThermoTable();

    @Test
    void formationEnthalpiesAtReferenceTemperature() {
        // kJ/mol, standard thermochemical reference values
        assertThat(table.enthalpy(Species.CH4, 298.15) / 1000.0).isCloseTo(-74.6, within(0.2));
        assertThat(table.enthalpy(Species.C2H6, 298.15) / 1000.0).isCloseTo(-84.0, within(0.3));
        assertThat(table.enthalpy(Species.CO2, 298.15) / 1000.0).isCloseTo(-393.51, within(0.2));
        assertThat(table.enthalpy(Species.H2O, 298.15) / 1000.0).isCloseTo(-241.83, within(0.2));
        assertThat(table.enthalpy(Species.O2, 298.15) / 1000.0).isCloseTo(0.0, within(0.05));
        assertThat(table.enthalpy(Species.N2, 298.15) / 1000.0).isCloseTo(0.0, within(0.05));
    }

    @Test
    void sensibleEnthalpyCheckpoints() {
        // (H(T) - H(298.15)) in kJ/mol vs standard tables
        checkSensible("N2", 1000, 21.46, 0.1);
        checkSensible("O2", 1000, 22.70, 0.1);
        checkSensible("CO2", 1000, 33.40, 0.2);
        checkSensible("CO2", 2000, 91.44, 0.3);
        checkSensible("H2O", 1000, 26.0, 0.2);
    }

    private void checkSensible(String species, double t, double expectedKj, double tol) {
        double sensible = (table.enthalpy(Species.valueOf(species), t)
                - table.enthalpy(Species.valueOf(species), 298.15)) / 1000.0;
        assertThat(sensible).as("sensible enthalpy %s@%.0f", species, t)
                .isCloseTo(expectedKj, within(tol));
    }

    @Test
    void cpIsContinuousAcrossJoin() {
        for (Species sp : Species.values()) {
            double cpLo = table.cp(sp, 1000.0 - 1e-6);
            double cpHi = table.cp(sp, 1000.0 + 1e-6);
            assertThat(cpHi).as("Cp continuity at 1000 K for %s", sp)
                    .isCloseTo(cpLo, within(Math.abs(cpLo) * 1e-3 + 1e-6));
        }
    }

    @Test
    void outOfDomainEvaluationThrows() {
        org.junit.jupiter.api.Assertions.assertThrows(
                com.lab.flametemp.thermo.ThermoDomainException.class,
                () -> table.enthalpy(Species.CO2, 3600.0));
        org.junit.jupiter.api.Assertions.assertThrows(
                com.lab.flametemp.thermo.ThermoDomainException.class,
                () -> table.enthalpy(Species.O2, 100.0));
    }
}
