package com.lab.flametemp.balance;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.lab.flametemp.thermo.Species;

/**
 * Catalogue of fuels the service can balance. Adding a fuel means adding an
 * entry here plus its NASA table; an unknown fuel name fails the job before
 * any iteration starts.
 */
@Component
public class FuelRegistry {

    private final Map<String, FuelDefinition> fuels = new LinkedHashMap<>();

    public FuelRegistry() {
        register(new FuelDefinition(Species.CH4, 1, 4));
        register(new FuelDefinition(Species.C2H6, 2, 6));
    }

    private void register(FuelDefinition fuel) {
        fuels.put(fuel.formula(), fuel);
    }

    /** @return the fuel definition, or {@code null} for an unknown formula */
    public FuelDefinition find(String formula) {
        return formula == null ? null : fuels.get(formula);
    }
}
