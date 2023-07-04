package hex.tree.dt;

import hex.ModelBuilder;
import hex.ModelCategory;
import hex.ModelMetrics;
import hex.tree.dt.binning.BinAccumulatedStatistics;
import hex.tree.dt.binning.BinningStrategy;
import hex.tree.dt.binning.Histogram;
import hex.tree.dt.mrtasks.GetClassCountsMRTask;
import hex.tree.dt.mrtasks.ScoreDTTask;
import org.apache.commons.math3.util.Precision;
import org.apache.log4j.Logger;
import water.DKV;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.util.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Single Decision Tree
 */
public class DT extends ModelBuilder<DTModel, DTModel.DTParameters, DTModel.DTOutput> {

    /**
     * Minimum number of samples to split the set.
     */
    private int _min_rows;

    /**
     * Current number of build nodes.
     */
    int _nodesCount;

    /**
     * List of nodes, for each node holds either split feature index and threshold or just decision value if it is list.
     * Shape n x 2.
     * Values of second dimension: (feature index, threshold) or (-1, decision value).
     * While building the tree nodes are being filled from index 0 iteratively
     */
    private double[][] _tree;

    private DTModel _model;
    transient Random _rand;

    public final static double EPSILON = 1e-6;
    public final static double MIN_IMPROVEMENT = 1e-6; // todo - specify to each criterion when there will be more of them

    private static final Logger LOG = Logger.getLogger(DT.class);


    public DT(DTModel.DTParameters parameters) {
        super(parameters);
        _min_rows = parameters._min_rows;
        _nodesCount = 0;
        _tree = null;
        init(true);
    }

    public DT(boolean startup_once) {
        super(new DTModel.DTParameters(), startup_once);
    }

    /**
     * Use binning and update features limits for child nodes. todo - use several criteria (strategy)
     *
     * @param histogram - histogram for relevant data
     * @return split info - holds feature index and threshold, null if the split could not be found.
     */
    private SplitInfo findBestSplit(Histogram histogram) {
        int featuresNumber = histogram.featuresCount();
        Pair<Double, Double> currentMinCriterionPair = new Pair<>(-1., Double.MAX_VALUE);
        int bestFeatureIndex = -1;
        for (int featureIndex = 0; featureIndex < featuresNumber; featureIndex++) {
            // skip constant features
            if (histogram.isConstant(featureIndex)) {
                continue;
            }
            // iterate all bins
            Pair<Double, Double> minCriterionForFeature = histogram
                    .calculateBinsStatisticsForFeature(featureIndex)
                    .stream()
                    // todo - consider setting min count of samples in bin instead of filtering splits
                    .filter(binStatistics -> ((binStatistics._leftCount >= _min_rows)
                            && (binStatistics._rightCount >= _min_rows)))
                    .peek(binStatistics -> Log.debug("counts: " + binStatistics._maxBinValue + " "
                            + binStatistics._leftCount + " " + binStatistics._rightCount))
                    // map to pairs (maxBinValue, criterion)
                    .map(binStatistics -> new Pair<>(
                            binStatistics._maxBinValue, calculateCriterionOfSplit(binStatistics)))
                    .min(Comparator.comparing(Pair::_2))
                    .orElse(null);
            if (minCriterionForFeature == null) {
                continue; // split could not be found for this feature
            }
            // update current minimum criteria pair
            if (minCriterionForFeature._2() < currentMinCriterionPair._2()) {
                currentMinCriterionPair = minCriterionForFeature;
                bestFeatureIndex = featureIndex;
            }
        }
        if (bestFeatureIndex == -1) {
            return null; // no split could be found
        }
        double threshold = currentMinCriterionPair._1();
        double criterionValue = currentMinCriterionPair._2();
        return new SplitInfo(bestFeatureIndex, threshold, criterionValue);
    }

    private Double binaryEntropy(int leftCount, int leftCount0, int rightCount, int rightCount0) {
        double a1 = (entropyBinarySplit(leftCount0 * 1.0 / leftCount)
                * leftCount / (leftCount + rightCount));
        double a2 = (entropyBinarySplit(rightCount0 * 1.0 / rightCount)
                * rightCount / (leftCount + rightCount));
        double value = a1 + a2;
        return value;
    }

    private double entropyBinarySplit(final double oneClassFrequency) {
        return -1 * ((oneClassFrequency < Precision.EPSILON ? 0 : (oneClassFrequency * Math.log(oneClassFrequency)))
                + ((1 - oneClassFrequency) < Precision.EPSILON ? 0 : ((1 - oneClassFrequency) * Math.log(1 - oneClassFrequency))));
    }

    private Double calculateCriterionOfSplit(BinAccumulatedStatistics binStatistics) {
        return binaryEntropy(binStatistics._leftCount, binStatistics._leftCount0,
                binStatistics._rightCount, binStatistics._rightCount0);
    }

    /**
     * Select decision value for leaf. Decision value is argmax of the array with counts of samples by class.
     *
     * @param countsByClass counts of samples of each class
     * @return decision value (in current case - 0 or 1)
     */
    private int selectDecisionValue(int[] countsByClass) {
        if (_nclass == 1) {
            return countsByClass[0];
        }
        int currentMaxClass = 0;
        int currentMax = countsByClass[currentMaxClass];
        for (int c = 1; c < _nclass; c++) {
            if (countsByClass[c] > currentMax) {
                currentMaxClass = c;
                currentMax = countsByClass[c];
            }
        }
        return currentMaxClass;
    }

    /**
     * Calculates probabilities of each class for a leaf.
     *
     * @param countsByClass counts of 0 and 1 in a leaf
     * @return probabilities of 0 or 1
     */
    private double[] calculateProbability(int[] countsByClass) {
        int samplesCount = Arrays.stream(countsByClass).sum();
        return Arrays.stream(countsByClass).asDoubleStream().map(n -> n / samplesCount).toArray();
    }


    /**
     * Set decision value to the node.
     *
     * @param countsByClass counts of samples of each class
     * @param nodeIndex     node index
     */
    public void makeLeafFromNode(int[] countsByClass, int nodeIndex) {
        _tree[nodeIndex][0] = 1; // indicates leaf
        _tree[nodeIndex][1] = selectDecisionValue(countsByClass);
        _tree[nodeIndex][2] = calculateProbability(countsByClass)[0]; // for now keep only prob. of class 0. will be improved later
        // nothing to return, node is modified inplace
    }


    /**
     * Build next node from the first limits in queue. The queue is updated with children here.
     *
     * @param limitsQueue queue with feature limits for nodes
     * @param nodeIndex   index of node in the tree array
     */
    public void buildNextNode(Queue<DataFeaturesLimits> limitsQueue, int nodeIndex) {
        // take limits for actual node
        DataFeaturesLimits actualLimits = limitsQueue.poll();
        // if the element is null, then the node should not be built. Nulls exist to keep the array building straightforward
        if (actualLimits == null) {
            // don't save anything to tree (no node is created)
            // add imaginary left and right children to imitate right tree structure
            // left child
            limitsQueue.add(null);
            // right child
            limitsQueue.add(null);
            return;
        }

        // [count0, count1, ...]
        int[] countsByClass = countClasses(actualLimits);
        if (nodeIndex == 0) {
            Log.info("Classes counts in dataset: 0 - " + countsByClass[0] + ", 1 - " + countsByClass[1]);
        }
        // compute node depth
        int nodeDepth = (int) Math.floor(MathUtils.log2(nodeIndex + 1));
        // stop building from this node, the node will be a leaf
        if ((nodeDepth >= _parms._max_depth)
                || (countsByClass[0] <= _min_rows)
                || (countsByClass[1] <= _min_rows)
//                || zeroRatio > 0.999 || zeroRatio < 0.001
        ) {
            // add imaginary left and right children to imitate valid tree structure
            // left child
            limitsQueue.add(null);
            // right child
            limitsQueue.add(null);
            makeLeafFromNode(countsByClass, nodeIndex);
            return;
        }

        Histogram histogram = new Histogram(_train, actualLimits, BinningStrategy.EQUAL_WIDTH/*, minNumSamplesInBin - todo consider*/);

        SplitInfo bestSplitInfo = findBestSplit(histogram);
        double criterionForTheParentNode = entropyBinarySplit(1.0 * countsByClass[0] / (countsByClass[0] + countsByClass[1]));
        // if no split could be found, make a leaf from current node
        // if the information gain is low, make a leaf from current node
        if (bestSplitInfo == null 
                || Math.abs(criterionForTheParentNode - bestSplitInfo._criterionValue) < MIN_IMPROVEMENT) {
            // add imaginary left and right children to imitate right tree structure
            // left child
            limitsQueue.add(null);
            // right child
            limitsQueue.add(null);
            makeLeafFromNode(countsByClass, nodeIndex);
            return;
        }

        // flag that node is not a leaf
        _tree[nodeIndex][0] = 0;
        _tree[nodeIndex][1] = bestSplitInfo._splitFeatureIndex;
        _tree[nodeIndex][2] = bestSplitInfo._threshold;

        DataFeaturesLimits limitsLeft = actualLimits.updateMax(bestSplitInfo._splitFeatureIndex, bestSplitInfo._threshold);
        DataFeaturesLimits limitsRight = actualLimits.updateMin(bestSplitInfo._splitFeatureIndex, bestSplitInfo._threshold);
        Log.debug("root: " + Arrays.toString(countClasses(actualLimits)) + ", left: "
                + Arrays.toString(countClasses(limitsLeft)) + ", right: " + Arrays.toString(countClasses(limitsRight))
                + ", best feature: " + bestSplitInfo._splitFeatureIndex
                + ", threshold: " + bestSplitInfo._threshold);

        Log.debug("feature: " + bestSplitInfo._splitFeatureIndex + ", threshold: " + bestSplitInfo._threshold);
        Log.debug("Left min-max: " + limitsLeft.getFeatureLimits(bestSplitInfo._splitFeatureIndex)._min +
                " " + limitsLeft.getFeatureLimits(bestSplitInfo._splitFeatureIndex)._max);
        Log.debug("Right min-max: " + limitsRight.getFeatureLimits(bestSplitInfo._splitFeatureIndex)._min +
                " " + limitsRight.getFeatureLimits(bestSplitInfo._splitFeatureIndex)._max);

        // store limits for left child
        limitsQueue.add(limitsLeft);
        // store limits for right child
        limitsQueue.add(limitsRight);
    }


    /**
     * Compute initial features limits.
     *
     * @return features limits
     */
    public static DataFeaturesLimits getInitialFeaturesLimits(Frame data) {
        return new DataFeaturesLimits(
                IntStream.range(0, data.numCols() - 1 /*exclude the last prediction column*/)
                        .mapToObj(i -> data.vec(i))
                        // decrease min as the minimum border is always excluded and real min value could be lost
                        .map(v -> new FeatureLimits(v.min() - EPSILON, v.max()))
                        .collect(Collectors.toList()));
    }


    private class DTDriver extends Driver {

        private void dtChecks() {
            if (_parms._max_depth < 1) {
                error("_parms._max_depth", "Max depth has to be at least 1");
            }
            if (_train.hasNAs()) {
                error("_train", "NaNs are not supported yet");
            }
            if (_train.hasInfs()) {
                error("_train", "Infs are not supported");
            }
            if (IntStream.range(0, _train.numCols() - 1) // ignore prediction column
                    .mapToObj(index -> _train.vec(index).isCategorical()).anyMatch(i -> i)) {
                error("_train", "Categorical features are not supported yet");
            }
            if (!_response.isCategorical()) {
                error("_response", "Only categorical response is supported");
            }
            if (!_response.isBinary()) {
                error("_response", "Only binary response is supported");
            }
        }

        @Override
        public void computeImpl() {
            _model = null;
            try {
                init(true);
                dtChecks();
                if (error_count() > 0) {
                    throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(DT.this);
                }
                _rand = RandomUtils.getRNG(_parms._seed);
                _model = new DTModel(dest(), _parms,
                        new DTModel.DTOutput(DT.this));
                _model.delete_and_lock(_job);
                buildDT();
                LOG.info(_model.toString());
            } finally {
                if (_model != null)
                    _model.unlock(_job);
            }
        }

        /**
         * Build SDT and update infrastructure.
         */
        private void buildDT() {
            buildDTIteratively();
            Log.debug("depth: " + _parms._max_depth + ", nodes count: " + _nodesCount);

            CompressedDT compressedDT = new CompressedDT(_tree);

            _model._output._treeKey = compressedDT._key;
            DKV.put(compressedDT);
            _job.update(1);
            _model.update(_job);
            makeModelMetrics();
//            System.out.println("Tree: " + compressedDT.toString());
//            System.out.println("Rules: " + String.join("\n", compressedDT.getListOfRules()));
            Log.debug("Tree:");
            Log.debug(Arrays.deepToString(_tree));
        }

        /**
         * Build the tree iteratively starting from the root node.
         */
        private void buildDTIteratively() {
            _tree = new double[(int) Math.pow(2, _parms._max_depth + 1) - 1][3];
            Queue<DataFeaturesLimits> limitsQueue = new LinkedList<>();
            limitsQueue.add(getInitialFeaturesLimits(_train));
            // build iteratively each node of the tree (each cell of the array) by picking limits from the queue
            // and storing children's limits to the queue.
            // Tree will not be perfect. Missing nodes are empty elements and their limits in queue are null.
            for (int nodeIndex = 0; nodeIndex < _tree.length; nodeIndex++) {
                buildNextNode(limitsQueue, nodeIndex);
            }
        }


    }


    @Override
    protected Driver trainModelImpl() {
        return new DTDriver();
    }

    @Override
    public BuilderVisibility builderVisibility() {
        return BuilderVisibility.Experimental;
    }

    @Override
    public ModelCategory[] can_build() {
        return new ModelCategory[]{
                ModelCategory.Binomial,
//                ModelCategory.Multinomial,
//                                            ModelCategory.Ordinal,
//                ModelCategory.Regression
        };
    }

    @Override
    public boolean isSupervised() {
        return true;
    }

    protected final void makeModelMetrics() {
        ModelMetrics.MetricBuilder metricsBuilder = new ScoreDTTask(_model).doAll(_train).getMetricsBuilder();
        ModelMetrics modelMetrics = metricsBuilder.makeModelMetrics(_model, _parms.train(), null, null);
        _model._output._training_metrics = modelMetrics;
        // Score again on validation data
        if( _parms._valid != null) {
            Frame v = new Frame(valid());
            metricsBuilder = new ScoreDTTask(_model).doAll(v).getMetricsBuilder();
            _model._output._validation_metrics = metricsBuilder.makeModelMetrics(_model, v, null, null);
        }
        
//            out._model_summary = createModelSummaryTable(out._ntrees, out._treeStats);
//            out._scoring_history = createScoringHistoryTable();
        }
        


    /**
     * Count classes withing samples satisfying given limits.
     *
     * @param featuresLimits limits
     * @return pair (count0, count1)
     */
    private int[] countClasses(final DataFeaturesLimits featuresLimits) {
        GetClassCountsMRTask task = new GetClassCountsMRTask(featuresLimits == null
                // create limits that are always fulfilled
                ? Stream.generate(() -> new double[]{(-1) * Double.MAX_VALUE, Double.MAX_VALUE})
                .limit(_train.numCols() - 1 /*exclude the last prediction column*/).toArray(double[][]::new)
                : featuresLimits.toDoubles(), _nclass);
        task.doAll(_train);

        return task._countsByClass;
    }

}
