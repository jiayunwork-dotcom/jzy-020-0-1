package com.lab.flametemp.thermo;

import java.util.List;

/**
 * One species in the fixed calculation universe: the two hydrocarbons the
 * service knows about, the air constituents, and the four nailed-down product
 * species (CO2, H2O, O2, N2).
 *
 * <p>{@code atoms} gives elemental composition as {C, H, O, N}. Fuel species
 * double as the fuel-rich dilution product.
 */
public enum Species {

    CH4("methane", new double[]{1, 4, 0, 0}, true),
    C2H6("ethane", new double[]{2, 6, 0, 0}, true),
    O2("oxygen", new double[]{0, 0, 2, 0}, false),
    N2("nitrogen", new double[]{0, 0, 0, 2}, false),
    CO2("carbon dioxide", new double[]{1, 0, 2, 0}, false),
    H2O("water", new double[]{0, 2, 1, 0}, false);

    /** Order used everywhere for atom-balance vectors: carbon, hydrogen, oxygen, nitrogen. */
    public static final int C = 0, H = 1, O = 2, N = 3;

    private final String displayName;
    private final double[] atoms;
    private final boolean fuel;

    Species(String displayName, double[] atoms, boolean fuel) {
        this.displayName = displayName;
        this.atoms = atoms;
        this.fuel = fuel;
    }

    public String displayName() {
        return displayName;
    }

    /** Elemental composition vector {C, H, O, N} in atoms per mole. */
    public double[] atoms() {
        return atoms.clone();
    }

    public boolean isFuel() {
        return fuel;
    }

    public static Species fromFormula(String formula) {
        for (Species s : List.of(values())) {
            if (s.name().equals(formula)) {
                return s;
            }
        }
        return null;
    }
}
