package com.lab.flametemp.balance;

import org.springframework.stereotype.Component;

import com.lab.flametemp.thermo.Species;

/**
 * Atom bookkeeping for C, H, O, N.
 *
 * <p>The nitrogen rule lives here: nitrogen does not oxidise in this model,
 * so the product N2 mole count must equal the inlet N2 mole count; the
 * stoichiometry already sets it that way, and {@link #residuals} reports any
 * numeric gap.
 */
@Component
public class AtomBalance {

    /** Residual vector {dC, dH, dO, dN}: products minus reactants, mol of atoms. */
    public double[] residuals(Mixture reactants, Mixture products) {
        double[] in = atoms(reactants);
        double[] out = atoms(products);
        return new double[]{
                out[Species.C] - in[Species.C],
                out[Species.H] - in[Species.H],
                out[Species.O] - in[Species.O],
                out[Species.N] - in[Species.N]};
    }

    /** Largest absolute C/H/O/N residual, mol of atoms per mol of fuel. */
    public double maxAbsoluteResidual(Mixture reactants, Mixture products) {
        double[] r = residuals(reactants, products);
        double max = 0.0;
        for (double v : r) {
            max = Math.max(max, Math.abs(v));
        }
        return max;
    }

    private double[] atoms(Mixture mixture) {
        double[] total = new double[4];
        for (var entry : mixture.moles().entrySet()) {
            double[] a = entry.getKey().atoms();
            double n = entry.getValue();
            for (int i = 0; i < 4; i++) {
                total[i] += a[i] * n;
            }
        }
        return total;
    }
}
