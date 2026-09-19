package com.lab.flametemp.job;

import org.springframework.stereotype.Service;

import com.lab.flametemp.solver.FlameTempService;
import com.lab.flametemp.solver.SolveOutcome;

/**
 * Runs one balance and persists it as a single job. Validation rejections
 * (unknown fuel, non-positive inputs) propagate before any row is written and
 * before iteration starts, as required.
 */
@Service
public class JobService {

    private final FlameTempService solver;
    private final JobRepository repository;

    public JobService(FlameTempService solver, JobRepository repository) {
        this.solver = solver;
        this.repository = repository;
    }

    public long submit(String fuel, double equivalenceRatio, double inletTemperatureK) {
        SolveOutcome outcome = solver.solve(fuel, equivalenceRatio, inletTemperatureK);
        return repository.save(fuel, equivalenceRatio, inletTemperatureK, outcome);
    }

    public JobRecord get(long id) {
        return repository.findById(id);
    }

    public java.util.List<JobRecord> listSummaries() {
        return repository.findSummaries();
    }

    public long jobCount() {
        return repository.jobCount();
    }
}
