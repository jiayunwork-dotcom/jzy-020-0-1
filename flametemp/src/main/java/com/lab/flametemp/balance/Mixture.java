package com.lab.flametemp.balance;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.lab.flametemp.thermo.Species;

/**
 * A molar mixture, expressed per 1 mol of fuel fed. Moles need not sum to a
 * constant between reactants and products: hydrocarbon combustion changes
 * total mole count, so closure is checked on atoms, never on total moles.
 */
public class Mixture {

    private final Map<Species, Double> moles = new LinkedHashMap<>();

    public void add(Species species, double amount) {
        if (amount < 0.0) {
            throw new IllegalArgumentException("negative mole amount for " + species);
        }
        moles.merge(species, amount, Double::sum);
    }

    public Map<Species, Double> moles() {
        return Collections.unmodifiableMap(moles);
    }

    public double molesOf(Species species) {
        return moles.getOrDefault(species, 0.0);
    }

    public double totalMoles() {
        return moles.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    /** Mole fractions keyed by species formula, in insertion order. */
    public Map<String, Double> moleFractions() {
        double total = totalMoles();
        Map<String, Double> out = new LinkedHashMap<>();
        moles.forEach((sp, n) -> out.put(sp.name(), total == 0.0 ? 0.0 : n / total));
        return out;
    }
}
