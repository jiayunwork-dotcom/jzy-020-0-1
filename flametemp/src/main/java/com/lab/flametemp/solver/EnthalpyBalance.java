package com.lab.flametemp.solver;

import org.springframework.stereotype.Component;

import com.lab.flametemp.balance.Mixture;
import com.lab.flametemp.thermo.ThermoTable;

/**
 * Enthalpy bookkeeping on the 1 mol fuel basis.
 *
 * <p>At constant pressure, ignoring kinetic energy, the adiabatic balance is
 * H_products(T) = H_reactants(Tin). Every molar enthalpy is formation enthalpy
 * plus the Cp temperature integral, evaluated through the single
 * {@link ThermoTable} for both sides of the balance.
 */
@Component
public class EnthalpyBalance {

    private final ThermoTable thermoTable;

    public EnthalpyBalance(ThermoTable thermoTable) {
        this.thermoTable = thermoTable;
    }

    /** Total mixture enthalpy [J per mol of fuel basis] at temperature {@code t} K. */
    public double totalEnthalpy(Mixture mixture, double t) {
        double h = 0.0;
        for (var entry : mixture.moles().entrySet()) {
            h += entry.getValue() * thermoTable.enthalpy(entry.getKey(), t);
        }
        return h;
    }

    /** Reactant enthalpy at the inlet temperature, J per mol of fuel. */
    public double reactantEnthalpy(Mixture reactants, double inletTemperatureK) {
        return totalEnthalpy(reactants, inletTemperatureK);
    }

    /**
     * Enthalpy residual f(T) = H_products(T) - H_reactants(Tin), J per mol of
     * fuel. The adiabatic flame temperature is its root.
     */
    public java.util.function.DoubleUnaryOperator residual(Mixture products,
                                                           double reactantEnthalpyJ) {
        return t -> totalEnthalpy(products, t) - reactantEnthalpyJ;
    }
}
