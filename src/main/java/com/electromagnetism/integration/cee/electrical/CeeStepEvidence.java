package com.electromagnetism.integration.cee.electrical;

import com.george_vi.electroenergetics.simulation.SimulationResults;
import com.george_vi.electroenergetics.simulation.util.SparseMatrix;
import java.util.*;
import java.util.function.BiFunction;

/** Pinned CEE linear-system residual evidence, not nonlinear convergence or uniqueness proof.
 * Conservative batch gate: one failed network invalidates all heater samples in the batch.
 * Original sparse coefficients are copied because both pinned solvers mutate their inputs.
 */
public final class CeeStepEvidence {
    private CeeStepEvidence() {}
    private static final ThreadLocal<Boolean> VALID = ThreadLocal.withInitial(() -> false);
    private record Evidence(boolean valid, UUID batch) {}
    private static final ThreadLocal<UUID> BATCH = new ThreadLocal<>();
    private static final Map<SimulationResults, Evidence> RESULTS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<SimulationResults,CeeTransientEngine.Report> REPORTS=Collections.synchronizedMap(new WeakHashMap<>());
    public static void report(SimulationResults result,CeeTransientEngine.Report report) { REPORTS.put(result,report); }
    public static Optional<CeeTransientEngine.Report> report(SimulationResults result) { return Optional.ofNullable(REPORTS.get(result)); }
    public static void begin() { FAILURE.set("");VALID.set(true); BATCH.set(UUID.randomUUID()); }
    private static final ThreadLocal<String> FAILURE=ThreadLocal.withInitial(()->"");
    public static void reject(String reason) { VALID.set(false);FAILURE.set(reason); }
    public static String currentFailure() { return FAILURE.get(); }
    public static void clear() { VALID.remove();BATCH.remove();FAILURE.remove(); }
    public static boolean currentLinearSystemsValid() { return VALID.get(); }
    public static UUID currentBatch() { return BATCH.get(); }
    public static void finish(SimulationResults results) { RESULTS.put(Objects.requireNonNull(results), new Evidence(VALID.get(), BATCH.get())); VALID.remove(); BATCH.remove(); }
    public static boolean verified(SimulationResults results) { var evidence = RESULTS.get(results); return evidence != null && evidence.valid(); }
    public static boolean verified(SimulationResults results, UUID batch) {
        var evidence = RESULTS.get(results);
        return batch != null && evidence != null && evidence.valid() && batch.equals(evidence.batch());
    }
    public static double[] solve(SparseMatrix matrix, double[] rhs,
            BiFunction<SparseMatrix, double[], double[]> solver) {
        var models = NonlinearNetworkSolver.take();
        if (!models.isEmpty()) {
            var result = NonlinearNetworkSolver.solve(matrix, rhs, models);
            VALID.set(VALID.get() && result.accepted());
            return result.voltages();
        }
        int n = matrix.size;
        int[][] columns = new int[n][];
        double[][] coefficients = new double[n][];
        double[] originalRhs = rhs.clone();
        for (int i = 0; i < n; i++) {
            columns[i] = Arrays.copyOf(matrix.data[i].getFlatNz(), matrix.data[i].getFlatNzSize());
            coefficients[i] = new double[columns[i].length];
            for (int j = 0; j < columns[i].length; j++) coefficients[i][j] = matrix.getValue(i, columns[i][j]);
        }
        double[] x = solver.apply(matrix, rhs);
        boolean valid = x != null && x.length == n;
        if (valid) for (double value : x) valid &= Double.isFinite(value);
        if (valid) for (int i = 0; i < n; i++) {
            double sum = 0, scale = Math.abs(originalRhs[i]);
            for (int j = 0; j < columns[i].length; j++) {
                double term = coefficients[i][j] * x[columns[i][j]];
                sum += term; scale += Math.abs(term);
            }
            valid &= Double.isFinite(sum) && Double.isFinite(scale)
                    && Math.abs(sum - originalRhs[i]) <= 1e-9 + 1e-7 * scale;
        }
        VALID.set(VALID.get() && valid);
        return x;
    }
}
