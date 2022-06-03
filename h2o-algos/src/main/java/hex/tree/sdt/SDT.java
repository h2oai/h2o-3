package hex.tree.sdt;

import hex.ModelBuilder;
import hex.ModelCategory;
import hex.tree.sdt.binning.BinAccumulatedStatistics;
import hex.tree.sdt.binning.BinningStrategy;
import hex.tree.sdt.binning.Histogram;
import hex.tree.sdt.mrtasks.CountSplitValuesMRTask;
import hex.tree.sdt.mrtasks.GetClassCountsMRTask;
import hex.tree.sdt.mrtasks.SplitFrameMRTask;
import org.apache.log4j.Logger;
import water.DKV;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.util.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Single Decision Tree
 */
public class SDT extends ModelBuilder<SDTModel, SDTModel.SDTParameters, SDTModel.SDTOutput> {
    private int maxDepth;
    int nodesCount;

    private double[][] compressedTree;

    private Integer actualDepth;

    private Node root;

    private SDTModel model;
    transient Random rand;

    // todo - create file with constants ?
    private final static int LIMIT_NUM_ROWS_FOR_SPLIT = 3;

    private static final Logger LOG = Logger.getLogger(SDT.class);


    public SDT(SDTModel.SDTParameters parameters) {
        super(parameters);
        this.maxDepth = parameters.depth;
        this.actualDepth = 0;
        this.nodesCount = 0;
        this.compressedTree = null;
        init(false);
    }

    public SDT(boolean startup_once) {
        super(new SDTModel.SDTParameters(), startup_once);
    }

    public double[][] compress() {
        //  if parent node is at index i in the array then the left child of that node is at index (2*i + 1) 
        //  and right child is at index (2*i + 2) in the array. 
        System.out.println("Nodes count when compressing: " + nodesCount);
        // 2^k - 1 is max count of nodes, where k is depth
        compressedTree = new double[(int) Math.pow(2, actualDepth)][2];
        writeSubtreeStartingFromIndex(root, 0);
        return compressedTree;
    }

    private void writeSubtreeStartingFromIndex(final Node actualNode, final int actualIndex) {
        if (actualNode == null) {
            return;
        }
        compressedTree[actualIndex][0] = actualNode.getFeature() == null ? -1
                : actualNode.getFeature().doubleValue();
        compressedTree[actualIndex][1] = actualNode.getThreshold() == null ? actualNode.getDecisionValue()
                : actualNode.getThreshold();
        writeSubtreeStartingFromIndex(actualNode.getLeft(), 2 * actualIndex + 1);
        writeSubtreeStartingFromIndex(actualNode.getRight(), 2 * actualIndex + 2);
    }

    public double[][] getCompressedTree() {
        if (compressedTree == null) {
            compress();
        }
        return compressedTree;
    }

    /**
     * When we split data in each node.
     *
     * @param data
     * @param featuresLimits
     * @return
     */
    private SplitInfo findBestSplit(final Frame data, DataFeaturesLimits featuresLimits) {
        // find split (feature and threshold)
        int featuresNumber = data.numCols();
        Pair<Double, Double> currentMinEntropyPair = new Pair<>(-1., Double.MAX_VALUE);
        int bestFeatureIndex = -1;
        for (int featureIndex = 0; featureIndex < featuresNumber - 1 /*last column is prediction*/; featureIndex++) {
            final int featureIndexForLambda = featureIndex;
            // iterate all candidate values of threshold
            Pair<Double, Double> minEntropyForFeature = featuresLimits.getFeatureRange(featureIndex)
                    .map(candidateValue -> new Pair<>(
                            candidateValue, calculateEntropyOfSplit(data, featureIndexForLambda, candidateValue)))
                    .min(Comparator.comparing(Pair::_2))
                    .get();
            if (minEntropyForFeature._2() < currentMinEntropyPair._2()) {
                currentMinEntropyPair = minEntropyForFeature;
                bestFeatureIndex = featureIndex;
            }
        }
        double threshold = currentMinEntropyPair._1();
        return new SplitInfo(bestFeatureIndex, threshold);
    }

    /**
     * When we don't split the frame, but update features limits and use binning
     *
     * @return
     */
    private SplitInfo findBestSplit(Histogram histogram) {
        // find split (feature and threshold)
        int featuresNumber = histogram.featuresCount();
        Pair<Double, Double> currentMinEntropyPair = new Pair<>(-1., Double.MAX_VALUE);
        int bestFeatureIndex = -1;
        for (int featureIndex = 0; featureIndex < featuresNumber - 1 /*last column is prediction*/; featureIndex++) {
            // skip constant features
            if (histogram.isConstant(featureIndex)) {
                continue;
            }
            // iterate all bins
            Pair<Double, Double> minEntropyForFeature = histogram.calculateBinsStatisticsForFeature(featureIndex).stream()
                    .peek(binStatistics -> {
                        System.out.println(binStatistics._leftCount + " " + binStatistics._rightCount);
                    })
                    .map(binStatistics -> new Pair<>(
                            binStatistics._maxBinValue, calculateEntropyOfSplit(binStatistics)))
                    .min(Comparator.comparing(Pair::_2))
                    .get();
            if (minEntropyForFeature._2() < currentMinEntropyPair._2()) {
                currentMinEntropyPair = minEntropyForFeature;
                bestFeatureIndex = featureIndex;
            }
        }
        double threshold = currentMinEntropyPair._1();
        return new SplitInfo(bestFeatureIndex, threshold);
    }

    private Double binaryEntropy(int leftCount, int leftCount0, int rightCount, int rightCount0) {
        double a1 = (entropyBinarySplit(leftCount0 * 1.0 / leftCount)
                * leftCount / (leftCount + rightCount));
        double a2 = (entropyBinarySplit(rightCount0 * 1.0 / rightCount)
                * rightCount / (leftCount + rightCount));
        double value = a1 + a2;
//        System.out.println("value: " + value + ", t.l " + task.countLeft + ", t.l0 " + task.countLeft0 + ", t.r " + task.countRight + ", t.r0 " + task.countRight0);
        return value;
    }

    private double entropyBinarySplit(final double oneClassFrequency) {
        return -1 * ((oneClassFrequency < 0.01 ? 0 : (oneClassFrequency * Math.log(oneClassFrequency)))
                + (oneClassFrequency > 0.99 ? 0 : ((1 - oneClassFrequency) * Math.log(1 - oneClassFrequency))));
    }

    private Double calculateEntropyOfSplit(BinAccumulatedStatistics binStatistics) {
        return binaryEntropy(binStatistics._leftCount, binStatistics._leftCount0,
                binStatistics._rightCount, binStatistics._rightCount0);
    }

    public double calculateEntropyOfSplit(final Frame data, final int featureIndex, final double threshold) {
        CountSplitValuesMRTask task = new CountSplitValuesMRTask(featureIndex, threshold);
        task.doAll(data);
        return binaryEntropy(task.countLeft, task.countLeft0, task.countRight, task.countRight0);
    }

    public Node buildSubtree(final Frame data, DataFeaturesLimits featuresLimits, int nodeDepth) {
        Node subtreeRoot = new Node();
        nodesCount++;
        if (actualDepth < nodeDepth) {
            actualDepth = nodeDepth;
        }
        // todo - add limit by information gain (at least because of ideal split for example 11111)
        double zeroRatio = getZeroRatio(data);
        if (actualDepth >= maxDepth || data.numRows() <= LIMIT_NUM_ROWS_FOR_SPLIT || zeroRatio > 0.9 || zeroRatio < 0.1) {
            System.out.println("actualDepth: " + actualDepth + ", data.numRows(): " + data.numRows() + ", zeroRatio: " + zeroRatio);
            if (zeroRatio >= 0.5) {
                subtreeRoot.setDecisionValue(0);
            } else if (zeroRatio < 0.5) {
                subtreeRoot.setDecisionValue(1);
            }
            return subtreeRoot;
        }

        // find split (feature and threshold)
        Pair<Double, Double> currentMinEntropyPair = new Pair<>(-1., Double.MAX_VALUE);
        int bestFeatureIndex = -1;
        for (int featureIndex = 0; featureIndex < featuresNumber - 1 /*last column is prediction*/; featureIndex++) {
            final int featureIndexForLambda = featureIndex;
            // iterate all candidate values of threshold
            Pair<Double, Double> minEntropyForFeature = featuresLimits.getFeatureRange(featureIndex)
                    .map(candidateValue -> new Pair<>(
                            candidateValue, calculateEntropyOfSplit(featureIndexForLambda, candidateValue)))
                    .min(Comparator.comparing(Pair::_2))
                    .get();
            if (minEntropyForFeature._2() < currentMinEntropyPair._2()) {
                currentMinEntropyPair = minEntropyForFeature;
                bestFeatureIndex = featureIndex;
            }
        }
        
        double threshold = currentMinEntropyPair._1();
        subtreeRoot.setFeature(bestFeatureIndex);
        subtreeRoot.setThreshold(threshold);

        // split data
        DataSplit split = splitData(bestFeatureIndex, threshold);
//        String[] outputColNamesLeft = Arrays.stream(_train.names()).map(n -> n + "Left").toArray(String[]::new);
//        String[] outputColNamesRight = Arrays.stream(_train.names()).map(n -> n + "Right").toArray(String[]::new);
        subtreeRoot.setLeft(buildSubtree(split.leftSplit,
                featuresLimits.updateMax(bestFeatureIndex, threshold), nodeDepth + 1));
        subtreeRoot.setRight(buildSubtree(split.rightSplit,
                featuresLimits.updateMin(bestFeatureIndex, threshold), nodeDepth + 1));
        return subtreeRoot;
    }

    private double entropyBinarySplit(final double oneClassFrequency) {
//        System.out.println(oneClassFrequency + "..." + (oneClassFrequency < 0.01 ? 0 : (oneClassFrequency * Math.log(oneClassFrequency)))
//        + "..." + (oneClassFrequency > 0.99 ? 0 : ((1 - oneClassFrequency) * Math.log(1 - oneClassFrequency))));
//        int elementsCount = valuesCounts.values().stream().reduce(0, Integer::sum);
        return -1 * ((oneClassFrequency < 0.01 ? 0 : (oneClassFrequency * Math.log(oneClassFrequency)))
                             + (oneClassFrequency > 0.99 ? 0 : ((1 - oneClassFrequency) * Math.log(1 - oneClassFrequency))));
    }

    public double calculateEntropyOfSplit(final int featureIndex, final double threshold) {
        CountSplitValuesMRTask task = new CountSplitValuesMRTask(featureIndex, threshold);
        task.doAll(_train);

//        System.out.println(task.countLeft + " " + task.countLeft0 + " " + task.countRight + " " + task.countRight0);
//        // just count data records with needed value of feature
//        System.out.println("hh " + entropyBinarySplit(task.countLeft0 * 1.0 / (task.countLeft)) + " "
//        + task.countLeft + " " + (task.countLeft + task.countRight));
        double a1 = (entropyBinarySplit(task.countLeft0 * 1.0 / (task.countLeft))
                             * task.countLeft / (task.countLeft + task.countRight));
        double a2 = (entropyBinarySplit(task.countRight0 * 1.0 / (task.countRight))
                             * task.countRight / (task.countLeft + task.countRight));
        double value = a1 + a2;
//        System.out.println("value: " + value + ", t.l " + task.countLeft + ", t.l0 " + task.countLeft0 + ", t.r " + task.countRight + ", t.r0 " + task.countRight0);
        return value;

    }


    private DataFeaturesLimits getInitialFeaturesLimits() {
        return new DataFeaturesLimits(
                Arrays.stream(_train.vecs())
                        .map(v -> new FeatureLimits(v.min(), v.max())).collect(Collectors.toList()));
    }


    private class SDTDriver extends Driver {

        @Override
        public void computeImpl() {
            model = null;
            try {
                init(false);
                if (error_count() > 0) {
                    throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(SDT.this);
                }
                rand = RandomUtils.getRNG(_parms._seed);
//                isolationTreeStats = new IsolationTreeStats();
                model = new SDTModel(dest(), _parms,
                        new SDTModel.SDTOutput(SDT.this));
                model.delete_and_lock(_job);
                buildSDT();
//                model._output._model_summary = createModelSummaryTable();
                LOG.info(model.toString());
            } finally {
                if (model != null)
                    model.unlock(_job);
            }
        }

        private void buildSDT() {
            root = buildSubtree(_train, getStartFeaturesLimits(), 1);
            
            CompressedSDT compressedSDT = new CompressedSDT(compress());

            model._output.treeKey = compressedSDT._key;
            DKV.put(compressedSDT);
            _job.update(1);
            model.update(_job);
        }

    }


//
//    public void trainSDT() {
//        root = buildSubtree(trainData, getStartFeaturesLimits());
////        System.out.println(root);
//    }

    @Override
    protected Driver trainModelImpl() {
        return new SDTDriver();
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

    private static class DataSplit {
        Frame leftSplit;
        Frame rightSplit;
        int featureIndex;
        double threshold;
    }

    public DataSplit splitData(final int feature, final double threshold) {
        // Define task
        SplitFrameMRTask taskLeftSplit = new SplitFrameMRTask(feature, threshold, 0);
        SplitFrameMRTask taskRightSplit = new SplitFrameMRTask(feature, threshold, 1);
        System.out.println("trainData.types().length: " + _train.types().length);

        DataSplit split = new DataSplit();
        split.featureIndex = feature;
        split.threshold = threshold;
        // Run task
        taskLeftSplit.doAll(_train.types(), _train);
        taskRightSplit.doAll(_train.types(), _train);
        split.leftSplit = taskLeftSplit.outputFrame(
                null, // The output Frame will be stored in DKV and you can access it with this Key, can be null, in case you don't wanted in DKV
                _train.names(),
                _train.domains() // Categorical columns need domain, pass null for Numerical and String columns
        );
        split.rightSplit = taskRightSplit.outputFrame(
                null, // The output Frame will be stored in DKV and you can access it with this Key, can be null, in case you don't wanted in DKV
                _train.names(),
                _train.domains() // Categorical columns need domain, pass null for Numerical and String columns
        );
        return split;
    }

    private Double getZeroRatio(final Frame data) {
        GetClassCountsMRTask task = new GetClassCountsMRTask();
        task.doAll(data);
        return task.count0 * 1.0 / (task.count0 + task.count1);
    }


    public Node getRoot() {
        return root;
    }


}


// todo - analyze input data to get candidates for threshold
// see random forest with 1 tree
// bin data (go not by step but by count of data)
// data must be solved by histogram because of the size


// todo:
// train random forest on the same data to see the difference
// train sklearn decision tree on the same data
// debug dtree and see how the clustering is used
// more general - n-class classification, regression, gini, different 
// maybe evaluation by depth and hyperparams on validation set inside of training. Read about it
