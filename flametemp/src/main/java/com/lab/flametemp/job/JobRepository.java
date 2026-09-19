package com.lab.flametemp.job;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.lab.flametemp.balance.Mixture;
import com.lab.flametemp.solver.IterationPoint;
import com.lab.flametemp.solver.SolveOutcome;

/**
 * SQLite persistence for jobs. In-process file database only, no external
 * container. A job and all its child rows are written in one transaction so
 * parallel jobs each accumulate their own curve and never interleave.
 */
@Repository
public class JobRepository {

    private final JdbcTemplate jdbc;

    public JobRepository(JdbcTemplate JdbcTemplate) {
        this.jdbc = JdbcTemplate;
    }

    @Transactional
    public long save(String fuel, double equivalenceRatio, double inletTemperatureK,
                     SolveOutcome outcome) {
        jdbc.update("""
                        INSERT INTO jobs (fuel, equivalence_ratio, inlet_temperature, status,
                            final_temperature, enthalpy_tolerance,
                            atom_residual_c, atom_residual_h, atom_residual_o, atom_residual_n,
                            atom_threshold, reactant_enthalpy, failure_reason, failure_message,
                            created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now'))
                        """,
                fuel, equivalenceRatio, inletTemperatureK,
                outcome.converged() ? "CONVERGED" : "FAILED",
                outcome.finalTemperatureK(),
                outcome.enthalpyToleranceJPerMolFuel(),
                outcome.atomResiduals()[0], outcome.atomResiduals()[1],
                outcome.atomResiduals()[2], outcome.atomResiduals()[3],
                outcome.atomThreshold(),
                Double.isNaN(outcome.reactantEnthalpyJPerMolFuel())
                        ? null : outcome.reactantEnthalpyJPerMolFuel(),
                outcome.failureReason() == null ? null : outcome.failureReason().name(),
                outcome.failureMessage());

        Long id = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
        long jobId = id == null ? -1L : id;

        for (IterationPoint p : outcome.iterations()) {
            jdbc.update("""
                            INSERT INTO job_iterations (job_id, step_no, temperature, enthalpy_residual)
                            VALUES (?, ?, ?, ?)
                            """,
                    jobId, p.stepNo(), p.temperatureK(), p.enthalpyResidualJPerMolFuel());
        }

        saveSpecies(jobId, "REACTANT", outcome.reactants());
        if (outcome.products() != null) {
            saveSpecies(jobId, "PRODUCT", outcome.products());
        }
        return jobId;
    }

    private void saveSpecies(long jobId, String phase, Mixture mixture) {
        if (mixture == null) {
            return;
        }
        for (var entry : mixture.moles().entrySet()) {
            double moles = entry.getValue();
            double total = mixture.totalMoles();
            jdbc.update("""
                            INSERT INTO job_species (job_id, species, phase, moles, mole_fraction)
                            VALUES (?, ?, ?, ?, ?)
                            """,
                    jobId, entry.getKey().name(), phase, moles,
                    total == 0.0 ? 0.0 : moles / total);
        }
    }

    public JobRecord findById(long id) {
        List<JobRecord> rows = jdbc.query(SELECT_JOBS + " WHERE j.id = ?", JOB_MAPPER, id);
        if (rows.isEmpty()) {
            return null;
        }
        return loadChildren(rows.get(0));
    }

    public List<JobRecord> findSummaries() {
        return jdbc.query(SELECT_JOBS + " ORDER BY j.id", JOB_MAPPER);
    }

    public long jobCount() {
        Long c = jdbc.queryForObject("SELECT COUNT(*) FROM jobs", Long.class);
        return c == null ? 0L : c;
    }

    private JobRecord loadChildren(JobRecord head) {
        List<IterationPoint> iterations = jdbc.query("""
                        SELECT step_no, temperature, enthalpy_residual
                        FROM job_iterations WHERE job_id = ? ORDER BY step_no
                        """,
                (rs, n) -> new IterationPoint(rs.getInt("step_no"),
                        rs.getDouble("temperature"), rs.getDouble("enthalpy_residual")),
                head.id());

        List<JobRecord.SpeciesEntry> species = jdbc.query("""
                        SELECT species, phase, moles, mole_fraction
                        FROM job_species WHERE job_id = ? ORDER BY phase, id
                        """,
                (rs, n) -> new JobRecord.SpeciesEntry(rs.getString("species"),
                        rs.getString("phase"), rs.getDouble("moles"),
                        rs.getDouble("mole_fraction")),
                head.id());

        return new JobRecord(head.id(), head.fuel(), head.equivalenceRatio(),
                head.inletTemperatureK(), head.status(), head.finalTemperatureK(),
                head.enthalpyToleranceJPerMolFuel(),
                head.atomResidualC(), head.atomResidualH(), head.atomResidualO(),
                head.atomResidualN(), head.atomThreshold(),
                head.failureReason(), head.failureMessage(), head.createdAt(),
                iterations, species, head.reactantEnthalpyJPerMolFuel());
    }

    private static final String SELECT_JOBS = """
            SELECT j.id, j.fuel, j.equivalence_ratio, j.inlet_temperature, j.status,
                   j.final_temperature, j.enthalpy_tolerance,
                   j.atom_residual_c, j.atom_residual_h, j.atom_residual_o, j.atom_residual_n,
                   j.atom_threshold, j.reactant_enthalpy, j.failure_reason, j.failure_message,
                   j.created_at
            FROM jobs j
            """;

    private static final RowMapper<JobRecord> JOB_MAPPER = (rs, n) -> {
        double tFinal = rs.getDouble("final_temperature");
        Double finalT = rs.wasNull() ? null : tFinal;
        double rH = rs.getDouble("reactant_enthalpy");
        Double reactantH = rs.wasNull() ? Double.NaN : rH;
        return new JobRecord(
                rs.getLong("id"),
                rs.getString("fuel"),
                rs.getDouble("equivalence_ratio"),
                rs.getDouble("inlet_temperature"),
                rs.getString("status"),
                finalT,
                rs.getDouble("enthalpy_tolerance"),
                rs.getDouble("atom_residual_c"),
                rs.getDouble("atom_residual_h"),
                rs.getDouble("atom_residual_o"),
                rs.getDouble("atom_residual_n"),
                rs.getDouble("atom_threshold"),
                rs.getString("failure_reason"),
                rs.getString("failure_message"),
                rs.getString("created_at"),
                List.of(), List.of(), reactantH);
    };
}
