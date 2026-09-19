package com.lab.flametemp.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.lab.flametemp.solver.SolverConstants;

/**
 * Built-in demonstration job: methane, stoichiometric, 298.15 K inlet.
 * Seeded once on a fresh (empty) database so the service always has a
 * retrievable worked example whose final step is below tolerance and whose
 * temperature lies in the pinned 2200-2400 K band.
 */
@Component
public class DemoJobSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoJobSeeder.class);

    private final JobService jobs;
    private final JobRepository repository;

    public DemoJobSeeder(JobService jobs, JobRepository repository) {
        this.jobs = jobs;
        this.repository = repository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        if (repository.jobCount() > 0) {
            return;
        }
        long id = jobs.submit("CH4", 1.0, SolverConstants.DEMO_INLET_TEMPERATURE_K);
        JobRecord demo = jobs.get(id);
        log.info("seeded methane stoichiometric demo job id={} status={} finalTemperatureK={}",
                id, demo.status(), demo.finalTemperatureK());
    }
}
