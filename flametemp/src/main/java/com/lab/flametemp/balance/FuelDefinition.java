package com.lab.flametemp.balance;

import com.lab.flametemp.thermo.Species;

/**
 * One supported hydrocarbon fuel, described per mole of fuel by its carbon
 * and hydrogen atom counts. Oxygen-containing fuels are intentionally absent.
 *
 * @param species fuel species (also the unburned diluent on the rich side)
 * @param carbons number of carbon atoms per mole of fuel
 * @param hydrogens number of hydrogen atoms per mole of fuel
 */
public record FuelDefinition(Species species, int carbons, int hydrogens) {

    /** Stoichiometric oxygen demand, mol O2 per mol fuel: C + H/4. */
    public double stoichiometricOxygen() {
        return carbons + hydrogens / 4.0;
    }

    public String formula() {
        return species.name();
    }
}
