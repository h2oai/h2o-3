package water.rapids.ast.prims.models;

import hex.AUC2;
import hex.Model;
import org.apache.commons.math3.distribution.HypergeometricDistribution;
import org.apache.commons.math3.stat.inference.GTest;
import water.DKV;
import water.Key;
import water.MRTask;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValMapFrame;
import water.util.ArrayUtils;
import water.util.TwoDimTable;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

public class AstFairnessMetrics extends AstPrimitive {
    static public class FairnessMetrics {
        double tp;
        double fp;
        double tn;
        double fn;

        double total;
        double relativeSize;
        double accuracy;
        double precision;
        double f1;
        double tpr;
        double tnr;
        double fpr;
        double fnr;
        double auc;
        double aucpr;
        double gini;
        double selected;
        double selectedRatio;
        double logloss;

        public FairnessMetrics(double tp, double tn, double fp, double fn, double logLossSum, AUC2.AUCBuilder aucBuilder, double nrows) {
            this.tp = tp;
            this.tn = tn;
            this.fp = fp;
            this.fn = fn;
            total = tp + fp + tn + fn;
            logloss = logLossSum / total;
            relativeSize = total / nrows;
            accuracy = (tp + tn) / total;
            precision = tp / (fp + tp);
            f1 = (2 * tp) / (2 * tp + fp + fn);
            tpr = tp / (tp + fn);
            tnr = tn / (tn + fp);
            fpr = fp / (fp + tn);
            fnr = fn / (fn + tp);
            if (aucBuilder != null) {
                AUC2 auc2 = new AUC2(aucBuilder);
                auc = auc2._auc;
                aucpr = auc2._pr_auc;
                gini = auc2._gini;
            } else {
                auc = Double.NaN;
                aucpr = Double.NaN;
                gini = Double.NaN;
            }
            selected = tp + fp;
            selectedRatio = (tp + fp) / total;
        }
    }

    public static class FairnessMRTask extends MRTask {
        // Threshold to switch from Fisher's exact test to G-test.
        // Fisher's exact test gets slower with increasing size of the population
        // fortunately G-test approximates it very well for bigger population.
        // Recommendation is to use Fisher's test if the population size is lower than 1000
        // (http://www.biostathandbook.com/small.html) but nowadays even 10000 is computed in the blink of an eye
        public static final int GTEST_THRESHOLD = 10000;

        // The magic constant REL1+1e-7 is taken from the R implementation of fisher.test in order to make
        // the results the same => easier to test
        public static final double FISHER_TEST_REL_ERROR = (1 + 1e-7);
        int[] protectedColsIdx;
        int[] cardinalities;
        int responseIdx;
        int predictionIdx;
        final int tpIdx = 0;
        final int tnIdx = 1;
        final int fpIdx = 2;
        final int fnIdx = 3;
        final int llsIdx = 4; // Log Loss Sum
        final int essentialMetrics = 5;
        final int maxIndex;
        final int favourableClass;

        int[] _results;
        AUC2.AUCBuilder[] _aucs;

        public FairnessMRTask(int[] protectedColsIdx, int[] cardinalities, int responseIdx, int predictionIdx, int favourableClass) {
            super();
            this.protectedColsIdx = protectedColsIdx;
            this.cardinalities = cardinalities;
            this.responseIdx = responseIdx;
            this.predictionIdx = predictionIdx;
            this.favourableClass = favourableClass;
            double maxIndexDbl = Arrays.stream(cardinalities).asDoubleStream().reduce((a, b) -> a * b).getAsDouble();
            if (maxIndexDbl > Integer.MAX_VALUE)
                throw new RuntimeException("Too many combinations of categories! Maximum number of category combinations is " + Integer.MAX_VALUE + "!");

            this.maxIndex = (int) maxIndexDbl;
        }


        private int pColsToKey(Chunk[] cs, int row) {
            int[] indices = new int[protectedColsIdx.length];
            for (int i = 0; i < protectedColsIdx.length; i++) {
                if (cs[protectedColsIdx[i]].isNA(row))
                    indices[i] = (cardinalities[i] - 1);
                else
                    indices[i] += cs[protectedColsIdx[i]].at8(row);
            }
            return pColsToKey(indices);
        }

        public int pColsToKey(int[] indices) {
            int result = 0;
            int base = 1;
            for (int i = 0; i < protectedColsIdx.length; i++) {
                result += indices[i] * base;
                base *= cardinalities[i];
            }
            return result;
        }

        private double[] keyToPCols(int value) {
            double[] result = new double[cardinalities.length];
            for (int i = 0; i < cardinalities.length; i++) {
                final int tmp = value % cardinalities[i];
                value /= cardinalities[i];
                if (tmp == cardinalities[i] - 1)
                    result[i] = Double.NaN;
                else
                    result[i] = tmp;
            }
            return result;
        }

        protected String keyToString(int value, Frame fr) {
            double[] pcolIdx = keyToPCols(value);
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < protectedColsIdx.length; i++) {
                if (i > 0) result.append(",");
                if (Double.isFinite(pcolIdx[i])) {
                    result.append(fr.vec(protectedColsIdx[i]).domain()[(int) pcolIdx[i]]);
                } else {
                    result.append("NaN");
                }
            }
            return result.toString().replaceAll("[^A-Za-z0-9,]", "_");
        }

        @Override
        public void map(Chunk[] cs) {
            assert _results == null;
            _results = new int[maxIndex * essentialMetrics];
            _aucs = new AUC2.AUCBuilder[maxIndex];
            for (int i = 0; i < cs[0]._len; i++) {
                final int key = pColsToKey(cs, i);
                final long response = favourableClass == 1 ? cs[responseIdx].at8(i) : 1 - cs[responseIdx].at8(i);
                final long prediction = favourableClass == 1 ? cs[predictionIdx].at8(i) : 1 - cs[predictionIdx].at8(i);
                final double predictionProb = favourableClass == 1 ? cs[predictionIdx + 2].atd(i) : cs[predictionIdx + 1].atd(i);
                if (response == prediction) {
                    if (response == 1)
                        _results[essentialMetrics * key + tpIdx]++;
                    else
                        _results[essentialMetrics * key + tnIdx]++;
                } else {
                    if (prediction == 1)
                        _results[essentialMetrics * key + fpIdx]++;
                    else
                        _results[essentialMetrics * key + fnIdx]++;
                }
                _results[essentialMetrics * key + llsIdx] += -(response * Math.log(predictionProb) + (1 - response) * Math.log(1 - predictionProb));
                if (_aucs[key] == null)
                    _aucs[key] = new AUC2.AUCBuilder(400);
                _aucs[key].perRow(predictionProb, (int) response, 1);
            }
        }

        @Override
        public void reduce(MRTask mrt) {
            FairnessMRTask other = (FairnessMRTask) mrt;
            if (this._results == other._results) return;
            for (int i = 0; i < _results.length; i++) {
                _results[i] += other._results[i];
            }
            for (int i = 0; i < maxIndex; i++) {
                if (_aucs[i] == null)
                    _aucs[i] = other._aucs[i];
                else if (other._aucs[i] != null)
                    _aucs[i].reduce(other._aucs[i]);
            }
        }

        public Frame getMetrics(String[] protectedCols, Frame fr, Model model, String[] reference, final String frName) {
            // Calculate additional metrics
            FairnessMetrics[] results = new FairnessMetrics[maxIndex];
            final long nrows = fr.numRows();
            for (int i = 0; i < maxIndex; i++) {
                results[i] = new FairnessMetrics(
                        _results[i * essentialMetrics + tpIdx],
                        _results[i * essentialMetrics + tnIdx],
                        _results[i * essentialMetrics + fpIdx],
                        _results[i * essentialMetrics + fnIdx],
                        _results[i * essentialMetrics + llsIdx],
                        _aucs[i],
                        nrows
                );
            }

            // Get reference - If not specified, use the biggest group as a reference.
            int referenceIdx = 0;
            if (reference != null) {
                int[] indices = new int[protectedCols.length];
                for (int i = 0; i < protectedCols.length; i++) {
                    indices[i] = ArrayUtils.find(fr.vec(protectedCols[i]).domain(), reference[i]);
                }
                referenceIdx = pColsToKey(indices);
            } else {
                double max = 0;
                for (int key = 0; key < maxIndex; key++) {
                    if (results[key].total > max) {
                        max = results[key].total;
                        referenceIdx = key;
                    }
                }
            }

            int emptyResults = 0;
            for (FairnessMetrics fm : results)
                emptyResults += fm.total == 0 ? 1 : 0;

            // Fill in a frame
            final String[] skipAIR = new String[]{"total", "relativeSize"};
            Field[] metrics = FairnessMetrics.class.getDeclaredFields();
            final int protectedColsCnt = protectedCols.length;
            final int metricsCount = metrics.length + (metrics.length - skipAIR.length) + 1/*p-value*/;
            double[][] resultCols = new double[protectedColsCnt + metricsCount][results.length - emptyResults];
            FairnessMetrics ref = results[referenceIdx];
            int nonEmptyKey = 0;
            for (int key = 0; key < maxIndex; key++) {
                if (results[key].total == 0) continue;
                int counter = 0;
                double[] decodedKey = keyToPCols(key);
                for (int i = 0; i < protectedCols.length; i++) {
                    resultCols[i][nonEmptyKey] = decodedKey[i];
                }
                for (int i = 0; i < metrics.length; i++) {
                    try {
                        resultCols[protectedColsCnt + i][nonEmptyKey] = metrics[i].getDouble(results[key]);
                        if (!ArrayUtils.contains(skipAIR, metrics[i].getName())) {
                            final double air = metrics[i].getDouble(results[key]) / metrics[i].getDouble(ref);
                            resultCols[protectedColsCnt + metrics.length + i - counter][nonEmptyKey] = air;

                        } else
                            counter++;
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
                try {
                    resultCols[resultCols.length - 1][nonEmptyKey] = getPValue(ref, results[key]);
                } catch (Exception e) {
                    resultCols[resultCols.length - 1][nonEmptyKey] = Double.NaN;
                }
                nonEmptyKey++;
            }
            String[] colNames = new String[protectedColsCnt + metricsCount];
            System.arraycopy(protectedCols, 0, colNames, 0, protectedCols.length);
            int counter = 0;
            for (int i = 0; i < metrics.length; i++) {
                colNames[protectedColsCnt + i] = metrics[i].getName();
                if (!ArrayUtils.contains(skipAIR, metrics[i].getName())) {
                    colNames[protectedColsCnt + metrics.length + i - counter] = "AIR_" + metrics[i].getName();
                } else
                    counter++;
            }
            colNames[colNames.length - 1] = "p.value";

            Vec[] vecs = new Vec[protectedColsCnt + metricsCount];
            for (int i = 0; i < protectedColsCnt; i++) {
                vecs[i] = Vec.makeVec(resultCols[i], fr.domains()[protectedColsIdx[i]], Vec.newKey());
            }
            for (int i = 0; i < metricsCount; i++) {
                vecs[protectedColsCnt + i] = Vec.makeVec(resultCols[protectedColsCnt + i], Vec.newKey());
            }
            return new Frame(Key.make("fairness_metrics_" + frName + "_for_model_" + model._key), colNames, vecs);
        }

        public Map<String, Frame> getROCInfo(Model model, Frame fr, final String frName) {
            Map<String, Frame> result = new HashMap<>();
            for (int id = 0; id < maxIndex; id++) {
                if (_aucs[id] == null) continue;
                AUC2 auc = new AUC2(_aucs[id]);
                // Fill TwoDimTable
                String[] thresholds = new String[auc._nBins];
                for (int i = 0; i < auc._nBins; i++)
                    thresholds[i] = Double.toString(auc._ths[i]);
                AUC2.ThresholdCriterion crits[] = AUC2.ThresholdCriterion.VALUES;
                String[] colHeaders = new String[crits.length + 2];
                String[] types = new String[crits.length + 2];
                String[] formats = new String[crits.length + 2];
                colHeaders[0] = "Threshold";
                types[0] = "double";
                formats[0] = "%f";
                int i;
                for (i = 0; i < crits.length; i++) {
                    colHeaders[i + 1] = crits[i].toString();
                    types[i + 1] = crits[i]._isInt ? "long" : "double";
                    formats[i + 1] = crits[i]._isInt ? "%d" : "%f";
                }
                colHeaders[i + 1] = "idx";
                types[i + 1] = "int";
                formats[i + 1] = "%d";
                TwoDimTable thresholdsByMetrics = new TwoDimTable("Metrics for Thresholds", "Binomial metrics as a function of classification thresholds", new String[auc._nBins], colHeaders, types, formats, null);
                for (i = 0; i < auc._nBins; i++) {
                    int j = 0;
                    thresholdsByMetrics.set(i, j, Double.valueOf(thresholds[i]));
                    for (j = 0; j < crits.length; j++) {
                        double d = crits[j].exec(auc, i); // Note: casts to Object are NOT redundant
                        thresholdsByMetrics.set(i, 1 + j, crits[j]._isInt ? (Object) ((long) d) : d);
                    }
                    thresholdsByMetrics.set(i, 1 + j, i);
                }
                String groupName = keyToString(id, fr);
                Frame f = thresholdsByMetrics.asFrame(Key.make("thresholds_and_metrics_" + groupName + "_for_model_" + model._key + "_for_frame_" + frName));
                DKV.put(f);
                result.put("thresholds_and_metrics_" + groupName, f);
            }
            return result;
        }

        /**
         * Calculate p-value using Fisher's exact test
         * <p>
         * |              | Protected Group | Reference |
         * |--------------+-----------------+-----------|
         * | Selected     | a               | b         |
         * | Not Selected | c               | d         |
         *
         * @param a
         * @param b
         * @param c
         * @param d
         * @return
         */
        private static double fishersTest(long a, long b, long c, long d) {
            long popSize = a + b + c + d;
            if (popSize > Integer.MAX_VALUE) return Double.NaN; // Make sure we don't get stuck on p-value computation
            HypergeometricDistribution hgd = new HypergeometricDistribution((int) popSize, (int) (a + b), (int) (a + c));
            double p = hgd.probability((int) a);
            double pValue = 0;
            // sum up pValues in all more extreme cases - like in R, sum all less probable cases in to the p-value
            for (int i = (int) Math.max(a - d, 0); i <= Math.min(a + b, a + c); i++) {
                final double proposal = hgd.probability(i);
                if (proposal <= p * FISHER_TEST_REL_ERROR) pValue += proposal;
            }
            return pValue;
        }


        /**
         * Calculate p-value. If there is a high number of instances use G-test as approximation of Fisher's exact test,
         * otherwise use Fisher's exact test.
         *
         * @param ref     Metrics for the reference group
         * @param results Metrics for a protected group
         * @return p-value
         */
        private static double getPValue(FairnessMetrics ref, FairnessMetrics results) {
            long a = (long) results.selected;
            long b = (long) ref.selected;
            long c = (long) (results.total - results.selected);
            long d = (long) (ref.total - ref.selected);
            if ((ref.total < GTEST_THRESHOLD && results.total < GTEST_THRESHOLD) || a == 0 || b == 0 || c == 0 || d == 0) {
                // fisher's exact test
                return fishersTest(a, b, c, d);
            } else {
                return new GTest().gTestDataSetsComparison(
                        new long[]{a, c},
                        new long[]{b, d}
                );
            }
        }
    }

    @Override
    public String[] args() {
        return new String[]{"model", "test_frame", "protected_columns", "reference", "favourable_class"};
    }

    @Override
    public int nargs() {
        return 1 + 5;
    }

    @Override
    public String str() {
        return "fairnessMetrics";
    }


    @Override
    public ValMapFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
        Model model = stk.track(asts[1].exec(env)).getModel();
        Frame fr = stk.track((asts[2].exec(env)).getFrame());
        String[] protectedCols = stk.track(asts[3].exec(env)).getStrs();
        String[] reference = stk.track(asts[4].exec(env)).getStrs();
        String favourableClass = stk.track(asts[5].exec(env)).getStr();
        final String frameName = asts[2].str(); // Used only for naming the derived metrics

        final int responseIdx = fr.find(model._parms._response_column);
        if (!model._output.isBinomialClassifier()) {
            throw new H2OIllegalArgumentException("Model has to be a binomial model!");
        }
        for (String pc : protectedCols) {
            if (fr.find(pc) == -1)
                throw new RuntimeException(pc + " was not found in the frame!");
            if (!fr.vec(pc).isCategorical())
                throw new H2OIllegalArgumentException(pc + " has to be a categorical column!");
        }

        if (reference.length != protectedCols.length)
            reference = null;
        else
            for (int i = 0; i < protectedCols.length; i++) {
                if (!ArrayUtils.contains(fr.vec(protectedCols[i]).domain(), reference[i])) {
                    throw new RuntimeException("Reference group is not present in the protected column");
                }
            }
        if (!ArrayUtils.contains(fr.vec(responseIdx).domain(), favourableClass))
            throw new RuntimeException("Favourable class is not present in the response!");

        final int favorableClassId = ArrayUtils.find(fr.vec(responseIdx).domain(), favourableClass);
        final int[] protectedColsIdx = fr.find(protectedCols);
        final int[] cardinalities = IntStream.of(protectedColsIdx).map(colId -> fr.vec(colId).cardinality() + 1).toArray(); // +1 for missing value

        // Sanity check - the number of subgroups grows very quickly and higher values are practically unexplainable
        // but I don't want to limit the user too much
        if (Arrays.stream(cardinalities).asDoubleStream().reduce((a, b) -> a * b).orElse(Double.MAX_VALUE) > 1e6)
            throw new RuntimeException("Too many combinations of categories! Maximum number of category combinations is 1e6.");

        Frame predictions = new Frame(fr).add(model.score(fr));
        DKV.put(predictions);
        try {
            FairnessMRTask fairnessMRTask = (FairnessMRTask) new FairnessMRTask(
                    protectedColsIdx,
                    cardinalities,
                    responseIdx,
                    fr.numCols(),
                    favorableClassId
            ).doAll(predictions);
            Frame metrics = fairnessMRTask.getMetrics(protectedCols, fr, model, reference, frameName);
            Map<String, Frame> results = fairnessMRTask.getROCInfo(model, fr, frameName);
            DKV.put(metrics);
            results.put("overview", metrics);
            return new ValMapFrame(results);
        } finally {
            DKV.remove(predictions.getKey());
        }
    }
}
