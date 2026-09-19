package com.lab.flametemp.balance;

import org.springframework.stereotype.Component;

import com.lab.flametemp.thermo.Species;

/**
 * Reactant/product balancing for a hydrocarbon C_n H_m burned in air.
 *
 * <p>Basis: 1 mol of fuel. Air is 0.21 O2 / 0.79 N2 by mole. The product set
 * is nailed to CO2, H2O, O2, N2 with no CO or H2 dissociation:
 * <ul>
 *   <li>lean / stoichiometric (phi &le; 1): all fuel burns to completion,
 *       excess oxygen carries through;</li>
 *   <li>rich (phi &gt; 1): only the fraction 1/phi that the available oxygen
 *       can oxidise burns; the unburned fuel fraction (1 - 1/phi) carries
 *       through as the only rich-side diluent. That is the model's
 *       "excess fuel dilution", and keeps C/H/O/N closed without CO or H2.</li>
 * </ul>
 */
@Component
public class Stoichiometry {

    public static final double AIR_O2_FRACTION = 0.21;
    public static final double AIR_N2_FRACTION = 0.79;

    public record Balance(Mixture reactants, Mixture products, double burnedFraction) {
    }

    public Balance balance(FuelDefinition fuel, double equivalenceRatio) {
        double nFuelCarbon = fuel.carbons();
        double nFuelHydrogen = fuel.hydrogens();
        double o2Stoich = fuel.stoichiometricOxygen();

        double o2In = o2Stoich / equivalenceRatio;
        double n2In = o2In * (AIR_N2_FRACTION / AIR_O2_FRACTION);

        Mixture reactants = new Mixture();
        reactants.add(fuel.species(), 1.0);
        reactants.add(Species.O2, o2In);
        reactants.add(Species.N2, n2In);

        double burned = Math.min(1.0, 1.0 / equivalenceRatio);

        Mixture products = new Mixture();
        products.add(Species.CO2, nFuelCarbon * burned);
        products.add(Species.H2O, nFuelHydrogen / 2.0 * burned);
        // Oxygen left after oxidising the burned fuel fraction.
        products.add(Species.O2, o2In - o2Stoich * burned);
        // Nitrogen is inert: product N2 equals inlet N2 exactly.
        products.add(Species.N2, n2In);
        if (burned < 1.0) {
            products.add(fuel.species(), 1.0 - burned);
        }

        return new Balance(reactants, products, burned);
    }
}
