package com.lab.flametemp.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.lab.flametemp.job.JobNotFoundException;
import com.lab.flametemp.job.JobRecord;
import com.lab.flametemp.job.JobService;

/**
 * HTTP surface for the balance service. One POST is one balance -> one job;
 * there is deliberately no batch endpoint that would bundle unrelated points.
 */
@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobs;

    public JobController(JobService jobs) {
        this.jobs = jobs;
    }

    @PostMapping
    public Map<String, Object> submit(@RequestBody JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new BadRequestException("INVALID_REQUEST", "request body must be a JSON object");
        }
        String fuel = textField(body, "fuel");
        double phi = numberField(body, "equivalenceRatio");
        double tin = numberField(body, "inletTemperatureK");
        long id = jobs.submit(fuel, phi, tin);
        return Map.of("id", id, "status", "ACCEPTED");
    }

    @GetMapping("/{id}")
    public Map<String, Object> getOne(@PathVariable long id) {
        JobRecord job = jobs.get(id);
        if (job == null) {
            throw new JobNotFoundException(id);
        }
        return toView(job);
    }

    /** Lightweight listing for operators/retrieval; full content stays on the id route. */
    @GetMapping
    public List<Map<String, Object>> list() {
        return jobs.listSummaries().stream().map(this::toSummary).toList();
    }

    private String textField(JsonNode body, String name) {
        JsonNode node = body.get(name);
        if (node == null || node.isNull()) {
            throw new BadRequestException("MISSING_FIELD", "missing field: " + name);
        }
        if (!node.isTextual() || node.asText().isBlank()) {
            throw new BadRequestException("INVALID_FIELD", "field '" + name + "' must be a non-empty string");
        }
        return node.asText();
    }

    private double numberField(JsonNode body, String name) {
        JsonNode node = body.get(name);
        if (node == null || node.isNull()) {
            throw new BadRequestException("MISSING_FIELD", "missing field: " + name);
        }
        if (!node.isNumber()) {
            throw new BadRequestException("INVALID_FIELD",
                    "field '" + name + "' must be a JSON number");
        }
        double value = node.asDouble();
        if (!Double.isFinite(value)) {
            throw new BadRequestException("NON_FINITE_NUMBER",
                    "field '" + name + "' must be finite");
        }
        return value;
    }

    private Map<String, Object> toSummary(JobRecord j) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", j.id());
        m.put("fuel", j.fuel());
        m.put("equivalenceRatio", j.equivalenceRatio());
        m.put("inletTemperatureK", j.inletTemperatureK());
        m.put("status", j.status());
        m.put("finalTemperatureK", j.finalTemperatureK());
        m.put("failureReason", j.failureReason());
        m.put("createdAt", j.createdAt());
        return m;
    }

    private Map<String, Object> toView(JobRecord j) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", j.id());
        m.put("fuel", j.fuel());
        m.put("equivalenceRatio", j.equivalenceRatio());
        m.put("inletTemperatureK", j.inletTemperatureK());
        m.put("status", j.status());
        m.put("finalTemperatureK", j.finalTemperatureK());
        m.put("createdAt", j.createdAt());

        Map<String, Object> convergence = new LinkedHashMap<>();
        convergence.put("criterion",
                "abs(H_products(T) - H_reactants(Tin)) <= tolerance, "
                        + "enthalpy in J per mol of fuel, constant pressure, kinetic energy neglected");
        convergence.put("enthalpyToleranceJPerMolFuel", j.enthalpyToleranceJPerMolFuel());
        convergence.put("reactantEnthalpyJPerMolFuel",
                Double.isNaN(j.reactantEnthalpyJPerMolFuel()) ? null : j.reactantEnthalpyJPerMolFuel());
        convergence.put("converged", j.converged());
        m.put("convergence", convergence);

        List<Map<String, Object>> iterations = j.iterations().stream().map(p -> {
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("step", p.stepNo());
            step.put("temperatureK", p.temperatureK());
            step.put("enthalpyResidualJPerMolFuel", p.enthalpyResidualJPerMolFuel());
            step.put("absEnthalpyResidualJPerMolFuel", Math.abs(p.enthalpyResidualJPerMolFuel()));
            return step;
        }).toList();
        m.put("iterations", iterations);
        if (!iterations.isEmpty()) {
            var last = j.iterations().get(j.iterations().size() - 1);
            m.put("lastEnthalpyResidualJPerMolFuel", last.enthalpyResidualJPerMolFuel());
        }

        Map<String, Object> atoms = new LinkedHashMap<>();
        atoms.put("threshold", j.atomThreshold());
        atoms.put("residualC", j.atomResidualC());
        atoms.put("residualH", j.atomResidualH());
        atoms.put("residualO", j.atomResidualO());
        atoms.put("residualN", j.atomResidualN());
        atoms.put("closed",
                Math.max(Math.max(Math.abs(j.atomResidualC()), Math.abs(j.atomResidualH())),
                        Math.max(Math.abs(j.atomResidualO()), Math.abs(j.atomResidualN())))
                        < j.atomThreshold());
        m.put("atomClosure", atoms);

        Map<String, Object> products = new LinkedHashMap<>();
        Map<String, Object> reactants = new LinkedHashMap<>();
        for (JobRecord.SpeciesEntry s : j.species()) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("molesPerMolFuel", s.moles());
            e.put("moleFraction", s.moleFraction());
            ("PRODUCT".equals(s.phase()) ? products : reactants).put(s.species(), e);
        }
        Map<String, Object> composition = new LinkedHashMap<>();
        composition.put("productMoleFractions",
                products.entrySet().stream().collect(LinkedHashMap::new,
                        (mm, e) -> mm.put(e.getKey(), ((Map<?, ?>) e.getValue()).get("moleFraction")),
                        LinkedHashMap::putAll));
        composition.put("products", products);
        composition.put("reactants", reactants);
        m.put("composition", composition);

        if (j.failureReason() != null) {
            Map<String, Object> failure = new LinkedHashMap<>();
            failure.put("reason", j.failureReason());
            failure.put("message", j.failureMessage());
            m.put("failure", failure);
        }
        return m;
    }
}
