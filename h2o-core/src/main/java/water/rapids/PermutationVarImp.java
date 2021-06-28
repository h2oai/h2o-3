
package water.rapids;

import hex.*;
import water.Futures;
import water.H2O;
import water.H2OError;
import water.exceptions.H2OFailException;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static water.util.RandomUtils.getRNG;

/**
 * Permutation Variable (feature) importance measures the increase in the prediction error of the model after permuting
 * the variables' values, which breaks the relationship between the variables and the true outcome.
 * https://christophm.github.io/interpretable-ml-book/feature-importance.html
 * <p>
 * Calculate permutation variables importance, by shuffling randomly each variable of the training Frame,
 * scoring the model with the newly created frame using One At a Time approach
 * and Morris method; creating TwoDimTable with relative, scaled, and percentage value
 * TwoDimTable with mean of the absolute value, and standard deviation of all features importance
 */

public class PermutationVarImp {
    private final Model _model;
    private final Frame _inputFrame;

    /**
     * Constructor that stores the model, frame
     *
     * @param model trained model
     * @param fr    training frame
     */
    public PermutationVarImp(Model model, Frame fr) {
        if (fr.numRows() < 2)
            throw new IllegalArgumentException("Frame must contain more than 1 rows to be used in permutation variable importance!");
        if (!ArrayUtils.contains(fr.names(), model._parms._response_column)) {
            throw new IllegalArgumentException("Frame must contain the response column for the use in permutation variable importance!");
        }
        _model = model;
        _inputFrame = fr;
    }

    /**
     * Returns the metric (loss function) selected by the user (mse is default)
     *
     * @throws IllegalArgumentException if metric could not be loaded
     */
    private static double getMetric(ModelMetrics mm, String metric) {
        assert mm != null;
        double metricValue = ModelMetrics.getMetricFromModelMetric(mm, metric);
        if (Double.isNaN(metricValue))
            throw new IllegalArgumentException("Model doesn't support the metric following metric " + metric);
        return metricValue;
    }

    private String inferAndValidateMetric(String metric) {
        Set allowed_metrics = ModelMetrics.getAllowedMetrics(_model._key);
        metric = metric.toLowerCase();
        if (metric.equals("auto")) {
            if (_model._output._training_metrics instanceof ModelMetricsBinomial)
                metric = "auc";
            else if (_model._output._training_metrics instanceof ModelMetricsRegression)
                metric = "rmse";
            else if (_model._output._training_metrics instanceof ModelMetricsMultinomial)
                metric = "logloss";
            else
                throw new IllegalArgumentException("Unable to infer metric. Please specify metric for permutation variable importance.");
        }

        if (!allowed_metrics.contains(metric))
            throw new IllegalArgumentException("Permutation Variable Importance doesn't support " + metric + " for model " + _model._key);

        return metric;
    }

    /**
     * Used for shuffling the next feature asynchronously while the previous one is being evaluated.
     */
    private Future<Vec> precomputeShuffledVec(ExecutorService executor, Frame fr, HashSet<String> featuresToCompute, String[] variables, int currentFeature, long seed) {
        for (int f = currentFeature + 1; f < fr.numCols(); f++) {
            if (!featuresToCompute.contains(variables[f]))
                continue;
            int finalF = f;
            return executor.submit(
                    () -> VecUtils.shuffleVec(fr.vec(finalF), seed)
            );
        }
        return null;
    }

    private HashMap<String, Double> calculatePermutationVarImp(String metric, long n_samples, final String[] features, long seed) {
        // Use random seed if set to -1
        if (-1 == seed) seed = new Random().nextLong();

        if (n_samples == 1)
            throw new IllegalArgumentException("Unable to permute one row. Please set n_samples to higher value or to -1 to use the whole dataset.");

        final String[] variables = _inputFrame.names();

        HashSet<String> featuresToCompute = new HashSet<>(Arrays.asList((null != features && features.length > 0) ? features : variables));
        featuresToCompute.removeAll(Arrays.asList(_model._parms.getNonPredictors()));
        if (_model._parms._ignored_columns != null)
            featuresToCompute.removeAll(Arrays.asList(_model._parms._ignored_columns));

        Frame fr = null;
        if (n_samples > 1) {
            if (n_samples > 1000) {
                fr = MRUtils.sampleFrame(_inputFrame, n_samples, _model._parms._weights_column, seed);
            } else {
                Random rand = getRNG(seed);
                fr = _inputFrame.deepSlice(rand.longs(n_samples, 0, _inputFrame.numRows()).toArray(), null);
            }
        } else {
            fr = _inputFrame;
        }
        _model.score(fr).remove();
        final double origMetric = getMetric(ModelMetrics.getFromDKV(_model, fr), metric);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Vec shuffledFeature = null;
        Future<Vec> shuffledFeatureFuture = precomputeShuffledVec(executor, fr, featuresToCompute, variables, -1, seed);
        HashMap<String, Double> result = new HashMap<>();
        try {
            for (int f = 0; f < fr.numCols(); f++) {
                if (!featuresToCompute.contains(variables[f]))
                    continue;

                // shuffle values of feature
                assert shuffledFeatureFuture != null;
                shuffledFeature = shuffledFeatureFuture.get();
                shuffledFeatureFuture = precomputeShuffledVec(executor, fr, featuresToCompute, variables, f, seed);
                final Vec origFeature = fr.replace(f, shuffledFeature);

                // score the model again and compute diff
                _model.score(fr).remove();

                // save the difference for the given variable
                result.put(variables[f], Math.abs(getMetric(ModelMetrics.getFromDKV(_model, fr), metric) - origMetric));

                // return the original data
                fr.replace(f, origFeature);

                shuffledFeature.remove();
                shuffledFeature = null;
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Unable to calculate the permutation variable importance.", e);
        } finally {
            if (null != fr && fr != _inputFrame) fr.remove();
            if (null != shuffledFeature) shuffledFeature.remove();
            if (null != shuffledFeatureFuture) shuffledFeatureFuture.cancel(true);
            executor.shutdownNow();
        }
        return result;
    }

    /**
     * Get PermutationVarImp
     *
     * @param metric    Metric to use to calculate the variable (feature) importance
     * @param n_samples Number of samples to use to calculate the variable (feature) importance; Use -1 to use the whole frame
     * @param features  Features to evaluate
     * @param seed      Seed for random generator
     * @return TwoDimTable of Permutation Feature Importance scores
     */
    public TwoDimTable getPermutationVarImp(String metric, final long n_samples, final String[] features, long seed) {
        metric = inferAndValidateMetric(metric);

        HashMap<String, Double> varImps = calculatePermutationVarImp(metric, n_samples, features, seed);

        String[] names = new String[varImps.size()];
        double[] importance = new double[varImps.size()];

        int i = 0;
        for(Map.Entry<String, Double> entry : varImps.entrySet()) {
            names[i] = entry.getKey();
            importance[i++] = entry.getValue();
        }

        // Create TwoDimTable having (Relative + Scaled + percentage) importance 
        return ModelMetrics.calcVarImp(importance, names);
    }

    /**
     * Get Repeated Permutation Variable Importance
     *
     * @param metric    Metric to use to calculate the variable (feature) importance
     * @param n_samples Number of samples to use to calculate the  variable (feature) importance; Use -1 to use the whole frame
     * @param n_repeats Number of repeats
     * @param features  Features to evaluate
     * @param seed      Seed for random generator
     * @return TwoDimTable of Permutation Feature Importance scores
     */
    public TwoDimTable getRepeatedPermutationVarImp(String metric, final long n_samples, final int n_repeats ,final String[] features, long seed) {
        metric = inferAndValidateMetric(metric);

        HashMap<String, Double>[] varImps =  new HashMap[n_repeats];
        for (int i = 0; i < n_repeats; i++) {
            varImps[i] = calculatePermutationVarImp(metric, n_samples, features, (seed == -1 ? -1 : seed + i));
        }

        String[] names = new String[varImps[0].size()];
        // One row per feature, one column per PVI evaluation
        double[/* features */][/* repeats */] importance = new double[varImps[0].size()][n_repeats];
        List<Map.Entry<String, Double>> sortedFeatures = new ArrayList<>(varImps[0].entrySet());
        sortedFeatures.sort(Map.Entry.comparingByValue(Collections.reverseOrder()));
        int i = 0;
        for(Map.Entry<String, Double> entry : sortedFeatures) {
            names[i] = entry.getKey();
            for (int j = 0; j < n_repeats; j++) {
                importance[i][j] = varImps[j].get(entry.getKey());
            }
            i++;
        }

        return new TwoDimTable(
                "Repeated Permutation Variable Importance",
                null,
                names,
                IntStream.range(0, n_repeats).mapToObj((run) -> "Run "+(run+1)).toArray(String[]::new),
                IntStream.range(0, n_repeats).mapToObj((run) -> "double").toArray(String[]::new),
                null,
                "Variable",
                new String[names.length][],
                importance
        );
    }

    /**
     * Get PermutationVarImp
     *
     * @param metric    Metric to use to calculate the feature importance
     * @return TwoDimTable of Permutation Feature Importance scores
     */
    public TwoDimTable getPermutationVarImp(String metric) {
        return getPermutationVarImp(metric, -1, null, -1);
    }

}
