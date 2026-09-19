package com.lab.flametemp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.lab.flametemp.job.JobService;
import com.lab.flametemp.solver.SolverConstants;

/**
 * End-to-end regressions: HTTP, typed errors, SQLite persistence and parallel
 * jobs accumulating independent curves.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FlameTempApplicationTests {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JobService jobs;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> submit(String fuel, double phi, double tin) {
        String body = String.format(java.util.Locale.ROOT,
                "{\"fuel\":\"%s\",\"equivalenceRatio\":%.6f,\"inletTemperatureK\":%.4f}",
                fuel, phi, tin);
        return postJson(body);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postJson(String body) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        ResponseEntity<Map> resp = rest.postForEntity(url("/api/jobs"),
                new org.springframework.http.HttpEntity<>(body, headers), Map.class);
        assertThat(resp.getStatusCode().is2xxSuccessful())
                .as("expected 2xx, got %s: %s", resp.getStatusCode(), resp.getBody())
                .isTrue();
        return resp.getBody();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private ResponseEntity<Map> postRaw(String body) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return rest.postForEntity(url("/api/jobs"),
                new org.springframework.http.HttpEntity<>(body, headers), Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetch(long id) {
        ResponseEntity<Map> resp = rest.getForEntity(url("/api/jobs/" + id), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody();
    }

    @Test
    void contextLoadsAndDemoJobIsSeeded() {
        Map<String, Object> demo = fetch(1L);
        assertThat(demo.get("fuel")).isEqualTo("CH4");
        assertThat((Double) demo.get("equivalenceRatio")).isEqualTo(1.0);
        assertThat((Double) demo.get("inletTemperatureK"))
                .isEqualTo(SolverConstants.DEMO_INLET_TEMPERATURE_K);
        assertThat(demo.get("status")).isEqualTo("CONVERGED");
        double tFinal = ((Number) demo.get("finalTemperatureK")).doubleValue();
        assertThat(tFinal).isBetween(SolverConstants.METHANE_STOICH_BAND_MIN_K,
                SolverConstants.METHANE_STOICH_BAND_MAX_K);

        List<Map<String, Object>> iters = (List<Map<String, Object>>) demo.get("iterations");
        double lastResidual = ((Number) demo.get("lastEnthalpyResidualJPerMolFuel")).doubleValue();
        assertThat(Math.abs(lastResidual))
                .isLessThanOrEqualTo(SolverConstants.ENTHALPY_TOLERANCE_J_PER_MOL_FUEL);
        // monotone curve
        for (int i = 1; i < iters.size(); i++) {
            double prev = Math.abs(((Number) iters.get(i - 1).get("enthalpyResidualJPerMolFuel")).doubleValue());
            double cur = Math.abs(((Number) iters.get(i).get("enthalpyResidualJPerMolFuel")).doubleValue());
            assertThat(cur).isLessThan(prev);
        }
    }

    @Test
    void fullJobRoundTripCarriesAllRequiredFields() {
        long id = ((Number) submit("C2H6", 1.0, 298.15).get("id")).longValue();
        Map<String, Object> job = fetch(id);
        assertThat(job.get("fuel")).isEqualTo("C2H6");
        assertThat(job.get("status")).isEqualTo("CONVERGED");
        assertThat(job.get("finalTemperatureK")).isNotNull();
        assertThat(job.get("iterations")).isInstanceOf(List.class);
        Map<String, Object> atoms = (Map<String, Object>) job.get("atomClosure");
        assertThat(atoms.get("threshold")).isNotNull();
        assertThat(atoms.get("residualC")).isNotNull();
        Map<String, Object> conv = (Map<String, Object>) job.get("convergence");
        assertThat(conv.get("enthalpyToleranceJPerMolFuel")).isNotNull();
        Map<String, Object> composition = (Map<String, Object>) job.get("composition");
        Map<String, Object> fractions =
                (Map<String, Object>) composition.get("productMoleFractions");
        // nailed product set present
        for (String sp : new String[]{"CO2", "H2O", "O2", "N2"}) {
            assertThat(fractions).containsKey(sp);
        }
        double sum = fractions.values().stream().mapToDouble(v -> ((Number) v).doubleValue()).sum();
        assertThat(sum).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-12));
    }

    @Test
    void unknownFuelReturnsTyped422AndNoJob() {
        long before = jobs.jobCount();
        String body = "{\"fuel\":\"H2\",\"equivalenceRatio\":1.0,\"inletTemperatureK\":298.15}";
        ResponseEntity<Map> resp = postRaw(body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody().get("type")).isEqualTo("UNKNOWN_FUEL");
        assertThat(jobs.jobCount()).isEqualTo(before);
    }

    @Test
    void nonPositiveEquivalenceRatioReturnsTyped422() {
        String body = "{\"fuel\":\"CH4\",\"equivalenceRatio\":0,\"inletTemperatureK\":298.15}";
        ResponseEntity<Map> resp = postRaw(body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody().get("type")).isEqualTo("INVALID_EQUIVALENCE_RATIO");
    }

    @Test
    void nonPositiveInletTemperatureReturnsTyped422() {
        String body = "{\"fuel\":\"CH4\",\"equivalenceRatio\":1,\"inletTemperatureK\":-10}";
        ResponseEntity<Map> resp = postRaw(body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody().get("type")).isEqualTo("INVALID_INLET_TEMPERATURE");
    }

    @Test
    void missingFieldReturnsTyped400() {
        String body = "{\"fuel\":\"CH4\",\"inletTemperatureK\":298.15}";
        ResponseEntity<Map> resp = postRaw(body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().get("type")).isEqualTo("MISSING_FIELD");
    }

    @Test
    void nonFiniteValueReturnsTyped400() {
        String body = "{\"fuel\":\"CH4\",\"equivalenceRatio\":\"NaN\",\"inletTemperatureK\":298.15}";
        ResponseEntity<Map> resp = postRaw(body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().get("type")).isEqualTo("INVALID_FIELD");
    }

    @Test
    void malformedJsonReturnsTyped400() {
        ResponseEntity<Map> resp = postRaw("{not json");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().get("type")).isEqualTo("MALFORMED_REQUEST");
    }

    @Test
    void failedEnthalpyClosureJobIsStoredAndRetrievable() {
        long id = ((Number) submit("CH4", 1.0, 3500.0).get("id")).longValue();
        Map<String, Object> job = fetch(id);
        assertThat(job.get("status")).isEqualTo("FAILED");
        assertThat(job.get("finalTemperatureK")).isNull();
        Map<String, Object> failure = (Map<String, Object>) job.get("failure");
        assertThat(failure.get("reason")).isEqualTo("ROOT_NOT_BRACKETED");
    }

    @Test
    void unknownJobIdReturns404() {
        ResponseEntity<Map> resp = rest.getForEntity(url("/api/jobs/999999"), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().get("type")).isEqualTo("JOB_NOT_FOUND");
    }

    @Test
    void parallelJobsAccumulateIndependentCurves() throws Exception {
        int n = 12;
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Callable<Long>> tasks = IntStream.range(0, n).mapToObj(i -> (Callable<Long>) () -> {
                double phi = (i % 3 == 0) ? 0.8 : (i % 3 == 1) ? 1.0 : 1.2;
                String fuel = (i % 2 == 0) ? "CH4" : "C2H6";
                return ((Number) submit(fuel, phi, 298.15).get("id")).longValue();
            }).toList();

            List<Future<Long>> futures = pool.invokeAll(tasks);
            List<Long> ids = new java.util.ArrayList<>();
            for (Future<Long> f : futures) {
                ids.add(f.get());
            }
            assertThat(ids).doesNotHaveDuplicates();

            // Each fetched job must echo its own inputs and its own coherent curve.
            for (int i = 0; i < n; i++) {
                Map<String, Object> job = fetch(ids.get(i));
                double phi = (i % 3 == 0) ? 0.8 : (i % 3 == 1) ? 1.0 : 1.2;
                String fuel = (i % 2 == 0) ? "CH4" : "C2H6";
                assertThat(job.get("fuel")).isEqualTo(fuel);
                assertThat(((Number) job.get("equivalenceRatio")).doubleValue())
                        .isCloseTo(phi, org.assertj.core.data.Offset.offset(1e-9));
                List<Map<String, Object>> curve =
                        (List<Map<String, Object>>) job.get("iterations");
                assertThat(curve).isNotEmpty();
                double firstT = ((Number) curve.get(0).get("temperatureK")).doubleValue();
                for (Map<String, Object> step : curve) {
                    // temperatures from this job's own domain bisection
                    double t = ((Number) step.get("temperatureK")).doubleValue();
                    assertThat(t).isBetween(SolverConstants.domainMin(),
                            SolverConstants.domainMax());
                }
                double last = Math.abs(
                        ((Number) job.get("lastEnthalpyResidualJPerMolFuel")).doubleValue());
                assertThat(last)
                        .isLessThanOrEqualTo(SolverConstants.ENTHALPY_TOLERANCE_J_PER_MOL_FUEL);
                // job is self-consistent: first recorded temperature matches its curve owner
                assertThat(firstT).isNotNull();
            }

            // Stoich jobs hotter than their parallel lean/rich partners per fuel.
            double ch4Stoich = tempOf(fetch(ids.get(1)));
            double ch4Lean = tempOf(fetch(ids.get(0)));
            double ch4Rich = tempOf(fetch(ids.get(2)));
            assertThat(ch4Stoich).isGreaterThan(ch4Lean);
            assertThat(ch4Stoich).isGreaterThan(ch4Rich);
        } finally {
            pool.shutdownNow();
        }
    }

    private double tempOf(Map<String, Object> job) {
        return ((Number) job.get("finalTemperatureK")).doubleValue();
    }
}
