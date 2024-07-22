package hex.tree.dt;

import hex.ModelBuilder;
import hex.ModelCategory;
import hex.ModelMetrics;
import hex.tree.dt.binning.SplitStatistics;
import hex.tree.dt.binning.BinningStrategy;
import hex.tree.dt.binning.Histogram;
import hex.tree.dt.mrtasks.GetClassCountsMRTask;
import hex.tree.dt.mrtasks.ScoreDTTask;
import org.apache.log4j.Logger;
import water.DKV;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.util.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static hex.tree.dt.binning.SplitStatistics.entropyMulticlass;

/**
 * Decision Tree
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
     * Current number of build leaves.
     */
    int _leavesCount;

    /**
     * List of nodes, for each node holds either split feature index and threshold or just decision value if it is list.
     * While building the tree nodes are being filled from index 0 iteratively
     */
    private AbstractCompressedNode[] _tree;

    private DTModel _model;
    transient Random _rand;
    public final static double EPSILON = 1e-6;
    public final static double MIN_IMPROVEMENT = 1e-6;
    private static final Logger LOG = Logger.getLogger(DT.class);


    public DT(DTModel.DTParameters parameters) {
        super(parameters);
        _min_rows = parameters._min_rows;
        _nodesCount = 0;
        _leavesCount = 0;
        _tree = null;
        init(false);
    }

    public DT(boolean startup_once) {
        super(new DTModel.DTParameters(), startup_once);
    }

    /**
     * Find best split for current node based on the histogram.
     *
     * @param histogram - histogram for relevant data
     * @return split info - holds the best split for current node, null if the split could not be found.
     */
    private AbstractSplittingRule findBestSplit(Histogram histogram) {
        int featuresNumber = histogram.featuresCount();
        AbstractSplittingRule currentMinCriterionSplittingRule = null;
        AbstractSplittingRule minCriterionSplittingRuleForFeature;
        int bestFeatureIndex = -1;
        for (int featureIndex = 0; featureIndex < featuresNumber; featureIndex++) {
            // skip constant features
            if (histogram.isConstant(featureIndex)) {
                continue;
            }
            // find best split for current feature based on the criterion value
            minCriterionSplittingRuleForFeature = findBestSplitForFeature(histogram, featureIndex);
            
            if (minCriterionSplittingRuleForFeature == null) {
                continue; // split could not be found for this feature
            }
            // update current minimum criteria pair
            if (currentMinCriterionSplittingRule == null
                    || minCriterionSplittingRuleForFeature._criterionValue < currentMinCriterionSplittingRule._criterionValue) {
                currentMinCriterionSplittingRule = minCriterionSplittingRuleForFeature;
                bestFeatureIndex = featureIndex;
            }
        }
        if (bestFeatureIndex == -1) {
            return null; // no split could be found
        }

        return currentMinCriterionSplittingRule;
    }


    private AbstractSplittingRule findBestSplitForFeature(Histogram histogram, int featureIndex) {
        return (_train.vec(featureIndex).isNumeric()
                ? histogram.calculateSplitStatisticsForNumericFeature(featureIndex, _nclass)
                : histogram.calculateSplitStatisticsForCategoricalFeature(featureIndex, _nclass))
                .stream()
                .filter(binStatistics -> ((binStatistics._leftCount >= _min_rows)
                        && (binStatistics._rightCount >= _min_rows)))
                .peek(binStatistics -> Log.debug("split: " + binStatistics._splittingRule + ", counts: "
                        + binStatistics._leftCount + " " + binStatistics._rightCount))
                // calculate criterion value for the splitting rule and fill the splitting rule with the rest of info
                .peek(binStatistics -> binStatistics.setCriterionValue(calculateCriterionOfSplit(binStatistics))
                        .setFeatureIndex(featureIndex))
                .map(binStatistics -> binStatistics._splittingRule)
                // get splitting rule with the lowest criterion value
                .min(Comparator.comparing(AbstractSplittingRule::getCriterionValue))
                .orElse(null);
    }
    


    private static double calculateCriterionOfSplit(SplitStatistics binStatistics) {
        return binStatistics.splitEntropy();
    }

    /**
     * Select decision value for leaf. Decision value is argmax of the array with counts of samples by class.
     *
     * @param countsByClass counts of samples of each class
     * @return decision value (in current case - 0 or 1)
     */
    private int selectDecisionValue(int[] countsByClass) {
        if (_nclass == 1) {
            return 0;
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
     * @param countsByClass counts of each class in a leaf
     * @return probabilities of each class
     */
    private double[] calculateProbabilities(int[] countsByClass) {
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
        _tree[nodeIndex] = new CompressedLeaf(selectDecisionValue(countsByClass), calculateProbabilities(countsByClass));
        _leavesCount++;
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
            Log.info(IntStream.range(0, countsByClass.length)
                    .mapToObj(i -> i + " - " + countsByClass[i])
                    .collect(Collectors.joining(", ", "Classes counts in dataset: ", "")));
        }
        // compute node depth
        int nodeDepth = (int) Math.floor(MathUtils.log2(nodeIndex + 1));
        // stop building from this node, the node will be a leaf if: 
        // - max depth is reached 
        // - there is only one non-zero count in the countsByClass 
        // - there are not enough data points in the node
        if ((nodeDepth >= _parms._max_depth) 
                || Arrays.stream(countsByClass).filter(c -> c > 0).count() < 2 
                || Arrays.stream(countsByClass).sum() < _min_rows) {
            // add imaginary left and right children to imitate valid tree structure
            // left child
            limitsQueue.add(null);
            // right child
            limitsQueue.add(null);
            makeLeafFromNode(countsByClass, nodeIndex);
            return;
        }

        Histogram histogram = new Histogram(_train, actualLimits, BinningStrategy.EQUAL_WIDTH, _nclass);

        AbstractSplittingRule bestSplittingRule = findBestSplit(histogram);
        double criterionForTheParentNode = entropyMulticlass(countsByClass, Arrays.stream(countsByClass).sum());
        // if no split could be found, make a list from current node
        // if the information gain is low, make a leaf from current node
        if (bestSplittingRule == null
                || Math.abs(criterionForTheParentNode - bestSplittingRule._criterionValue) < MIN_IMPROVEMENT) {
            // add imaginary left and right children to imitate right tree structure
            // left child
            limitsQueue.add(null);
            // right child
            limitsQueue.add(null);
            makeLeafFromNode(countsByClass, nodeIndex);
            return;
        }
        
        _tree[nodeIndex] = new CompressedNode(bestSplittingRule);
        
        int splitFeatureIndex = bestSplittingRule.getFeatureIndex();
        DataFeaturesLimits limitsLeft, limitsRight;
        if(_train.vec(splitFeatureIndex).isNumeric()) {
            // create left and right limits separated by threshold
            double threshold = ((NumericSplittingRule) bestSplittingRule).getThreshold();
            limitsLeft = actualLimits.updateMax(splitFeatureIndex, threshold);
            limitsRight = actualLimits.updateMin(splitFeatureIndex, threshold);
        } else {
            boolean[] mask = ((CategoricalSplittingRule) bestSplittingRule).getMask();
            limitsLeft = actualLimits.updateMask(splitFeatureIndex, mask);
            limitsRight = actualLimits.updateMaskExcluded(splitFeatureIndex, mask);
        }


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
                        .mapToObj(data::vec)
                        // decrease min as the minimum border is always excluded and real min value could be lost
                        .map(v -> v.isNumeric()
                                ? new NumericFeatureLimits(v.min() - EPSILON, v.max())
                                : new CategoricalFeatureLimits(v.cardinality()))
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
            if (!_response.isCategorical()) {
                error("_response", "Only categorical response is supported");
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
            CompressedDT compressedDT = new CompressedDT(_tree, _leavesCount);

            _model._output._treeKey = compressedDT._key;
            DKV.put(compressedDT);
            _job.update(1);
            _model.update(_job);
        }

        /**
         * Build the tree iteratively starting from the root node.
         */
        private void buildDTIteratively() {
            int treeLength = (int) Math.pow(2, _parms._max_depth + 1) - 1;
            _tree = new AbstractCompressedNode[treeLength];
            Queue<DataFeaturesLimits> limitsQueue = new LinkedList<>();
            limitsQueue.add(getInitialFeaturesLimits(_train));
            // build iteratively each node of the tree (each cell of the array) by picking limits from the queue
            // and storing children's limits to the queue.
            // Tree will not be perfect. Missing nodes are empty elements and their limits in queue are null.
            for (int nodeIndex = 0; nodeIndex < treeLength; nodeIndex++) {
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
                ModelCategory.Multinomial,
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
     * Count classes within samples satisfying given limits.
     *
     * @param featuresLimits limits
     * @return pair (count0, count1)
     */
    private int[] countClasses(final DataFeaturesLimits featuresLimits) {
        GetClassCountsMRTask task = new GetClassCountsMRTask(featuresLimits == null
                // create limits that are always fulfilled
                ? getInitialFeaturesLimits(_train).toDoubles()
                : featuresLimits.toDoubles(), _nclass);
        task.doAll(_train);

        return task._countsByClass;
    }

}
