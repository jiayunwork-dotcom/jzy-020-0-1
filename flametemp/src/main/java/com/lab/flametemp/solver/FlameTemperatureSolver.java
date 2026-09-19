package com.lab.flametemp.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

import org.springframework.stereotype.Component;

import com.lab.flametemp.thermo.ThermoTable;

/**
 * Monotone bisection on the enthalpy residual.
 *
 * <p>The initial guess is the midpoint of the pinned domain; subsequent
 * guesses are the midpoint of the sign-bracketing interval. A probe whose
 * absolute residual is not smaller than the best seen so far only narrows the
 * bracket and is not appended: the sequence handed back therefore contains
 * only strictly decreasing |residual| values. Once a probe enters the
 * tolerance the sequence ends there; the iteration never continues toward a
 * second root hundreds of kelvin away.
 *
 * <p>If the residual never changes sign in the domain, or the evaluation
 * budget is spent before tolerance, the solve fails rather than returning a
 * temperature.
 */
@Component
public class FlameTemperatureSolver {

    /**
     * @return a successful outcome (list of accepted points, final T), or a
     *         failed outcome carrying the reason
     */
    public SolveOutcome solve(DoubleUnaryOperator enthalpyResidual,
                              double tolerance,
                              double[] atomResiduals,
                              com.lab.flametemp.balance.Mixture reactants,
                              com.lab.flametemp.balance.Mixture products,
                              double reactantEnthalpyJ) {
        double lo = ThermoTable.DOMAIN_T_MIN;
        double hi = ThermoTable.DOMAIN_T_MAX;
        double fLo = enthalpyResidual.applyAsDouble(lo);
        double fHi = enthalpyResidual.applyAsDouble(hi);

        if (!(Double.isFinite(fLo) && Double.isFinite(fHi)) || fLo > 0.0 || fHi < 0.0) {
            String msg = String.format(
                    "enthalpy residual does not bracket a root in [%.0f, %.0f] K: "
                            + "f(%.0f)=%.3g, f(%.0f)=%.3g J/mol fuel",
                    lo, hi, lo, fLo, hi, fHi);
            return SolveOutcome.failed(SolveFailureReason.ROOT_NOT_BRACKETED, msg,
                    List.of(), reactantEnthalpyJ, atomResiduals, reactants, products);
        }

        List<IterationPoint> accepted = new ArrayList<>();
        double bestAbs = Double.POSITIVE_INFINITY;
        int probes = 0;

        while (probes < SolverConstants.MAX_TEMPERATURE_PROBES
                && accepted.size() < SolverConstants.MAX_ACCEPTED_ITERATIONS) {
            double t = lo + (hi - lo) / 2.0;
            double f = enthalpyResidual.applyAsDouble(t);
            probes++;
            if (!Double.isFinite(f)) {
                return SolveOutcome.failed(SolveFailureReason.POLYNOMIAL_OUT_OF_DOMAIN,
                        "non-finite enthalpy residual at T=" + t + " K",
                        accepted, reactantEnthalpyJ, atomResiduals, reactants, products);
            }

            if (Math.abs(f) < bestAbs) {
                accepted.add(new IterationPoint(accepted.size() + 1, t, f));
                bestAbs = Math.abs(f);
            }

            if (Math.abs(f) <= tolerance) {
                return SolveOutcome.converged(t, accepted, reactantEnthalpyJ,
                        atomResiduals, reactants, products);
            }

            if (f < 0.0) {
                lo = t;
                fLo = f;
            } else {
                hi = t;
                fHi = f;
            }
        }

        return SolveOutcome.failed(SolveFailureReason.MAX_STEPS_EXCEEDED,
                String.format("enthalpy not closed after %d probes (best residual %.3g J/mol fuel)",
                        probes, bestAbs),
                accepted, reactantEnthalpyJ, atomResiduals, reactants, products);
    }
}
