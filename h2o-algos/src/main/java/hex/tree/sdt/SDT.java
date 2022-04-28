package hex.tree.sdt;

import water.Key;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.util.Pair;

import java.util.*;
import java.util.stream.Stream;

/**
 * Single Decision Tree
 */
public class SDT {
    private Frame trainData;
    private int predictionColumnIndex;
    private Integer maxDepth;
    private String[] features;
    int nodesCount;

    private Double[][] compressedTree;

    private Integer actualDepth;

    private Node root;

    // todo - create file with constants ?
    private final static int LIMIT_NUM_ROWS_FOR_SPLIT = 3;

    public SDT(final Frame trainData, final int predictionColumnIndex, final Integer maxDepth) {
        this.trainData = trainData;
        this.predictionColumnIndex = predictionColumnIndex;
        this.maxDepth = maxDepth;

        this.actualDepth = 0;
        this.features = trainData.names();
        this.nodesCount = 0;
        this.compressedTree = null;
    }

    public SDT(final Frame trainData, final int predictionColumnIndex) {
        this.trainData = trainData;
        this.predictionColumnIndex = predictionColumnIndex;
        this.maxDepth = Integer.MAX_VALUE;

        this.actualDepth = 0;
        this.features = trainData.names();
    }

    public void compress() {
        //  if parent node is at index i in the array then the left child of that node is at index (2*i + 1) 
        //  and right child is at index (2*i + 2) in the array. 
        compressedTree = new Double[nodesCount][3];
        writeSubtreeStartingFromIndex(root, 0);
    }

    private void writeSubtreeStartingFromIndex(final Node actualNode, final int actualIndex) {
        if (actualNode == null) {
            return;
        }
        compressedTree[actualIndex][0] = actualNode.getFeature() == null ? null 
                                                 : actualNode.getFeature().doubleValue();
        compressedTree[actualIndex][1] = actualNode.getThreshold();
        compressedTree[actualIndex][2] = actualNode.getDecisionValue() == null ? null
                                                 : actualNode.getDecisionValue().doubleValue();
        writeSubtreeStartingFromIndex(actualNode.getLeft(), 2 * actualIndex + 1);
        writeSubtreeStartingFromIndex(actualNode.getRight(), 2 * actualIndex + 2);
    }

    public Double[][] getCompressedTree() {
        if (compressedTree == null) {
            compress();
        }
        return compressedTree;
    }

    public Node buildSubtree(final Frame data, DataFeaturesLimits featuresLimits) {
        Node subtreeRoot = new Node();
        nodesCount++;
        // todo - add limit by information gain (at least because of ideal split for example 11111)
        Double zeroRatio = getZeroRatio(data);
        System.out.println("z " + zeroRatio);
        if (actualDepth >= maxDepth || data.numRows() <= LIMIT_NUM_ROWS_FOR_SPLIT || zeroRatio > 0.9 || zeroRatio < 0.1) {
            if(zeroRatio >= 0.5) {
                subtreeRoot.setDecisionValue(0);
            } else if(zeroRatio < 0.5) {
                subtreeRoot.setDecisionValue(1);
            }
            return subtreeRoot;
        }
        // todo fix - add only when the deps increases, not for the evert node (how?)
        actualDepth++;
        int featuresNumber = data.numCols();

        // find split (feature and threshold)
        Pair<Double, Double> currentMinEntropyPair = new Pair<>(-1., Double.MAX_VALUE);
        int bestFeatureIndex = -1;
        for (int featureIndex = 0; featureIndex < featuresNumber; featureIndex++) {
            if (featureIndex == predictionColumnIndex) {
                continue;
            }
            final int featureIndexForLambda = featureIndex;
            // iterate all candidate values of threshold
            Pair<Double, Double> minEntropyForFeature = featuresLimits.getFeatureRange(featureIndex)
                    .map(candidateValue -> new Pair<>(
                            candidateValue, calculateEntropyOfSplit(featureIndexForLambda, candidateValue)))
                    .min(Comparator.comparing(Pair::_2))
                    .get();
//            System.out.println("//" + minEntropyForFeature._2() + " " + currentMinEntropyPair._2());
            if (minEntropyForFeature._2() < currentMinEntropyPair._2()) {
//                System.out.println("here");
                currentMinEntropyPair = minEntropyForFeature;
                bestFeatureIndex = featureIndex;
            }
        }

        // no sense to split because the split is ideal
//        if (currentMinEntropyPair._2() == 0) {
//            subtreeRoot.setDecisionValue(selectDecisionValueForList(data)); // todo
//            return subtreeRoot;
//        }
        double threshold = currentMinEntropyPair._1();
        subtreeRoot.setFeature(bestFeatureIndex);
        subtreeRoot.setThreshold(threshold);

        // split data
        Frame split = splitData(bestFeatureIndex, threshold);
//        Frame resultLeftSplit = result.subframe(outputColNamesLeft);
//        Frame resultRightSplit = result.subframe(outputColNamesRight);
        String[] outputColNamesLeft = Arrays.stream(features).map(n -> n + "Left").toArray(String[]::new);
        String[] outputColNamesRight = Arrays.stream(features).map(n -> n + "Right").toArray(String[]::new);
        subtreeRoot.setLeft(buildSubtree(split.subframe(outputColNamesLeft),
                featuresLimits.updateMax(bestFeatureIndex, threshold)));
        subtreeRoot.setRight(buildSubtree(split.subframe(outputColNamesRight),
                featuresLimits.updateMin(bestFeatureIndex, threshold)));
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
        CountSplitValuesMRTask task = new CountSplitValuesMRTask(featureIndex, threshold, predictionColumnIndex);
        task.doAll(trainData);

//        System.out.println(task.countLeft + " " + task.countLeft0 + " " + task.countRight + " " + task.countRight0);
//        // just count data records with needed value of feature
//        System.out.println("hh " + entropyBinarySplit(task.countLeft0 * 1.0 / (task.countLeft)) + " "
//        + task.countLeft + " " + (task.countLeft + task.countRight));
        double a1 = (entropyBinarySplit(task.countLeft0 * 1.0 / (task.countLeft))
                             * task.countLeft / (task.countLeft + task.countRight));
        double a2 = (entropyBinarySplit(task.countRight0 * 1.0 / (task.countRight))
                             * task.countRight / (task.countLeft + task.countRight));
//        System.out.println(a1 + "-" + a2);
        double value = a1 + a2;
//        System.out.println("value: " + value);
        return value;

    }

    private DataFeaturesLimits getStartFeaturesLimits() {
        // todo - calculate min and max for each feature and save to list of maps.
        // new todo - mapreduce task
        return new DataFeaturesLimits(Arrays.asList(new FeatureLimits(0, 9), new FeatureLimits(0, 9)));
    }

    public void train() {
        root = buildSubtree(trainData, getStartFeaturesLimits());
//        System.out.println(root);
    }

    public Vec predict(final Frame data) {
        // task to recursively apply nodes criteria to each row, so it can be parallelized by rows 
        // (should it be parallelised one more time, not only map-reduce?)
        PredictMRTask task = new PredictMRTask(getCompressedTree());
        byte[] outputTypes = new byte[]{Vec.T_NUM};
        task.doAll(outputTypes, data);

        Frame result = task.outputFrame(
                null, // The output Frame will be stored in DKV and you can access it with this Key, can be null, in case you don't wanted in DKV
                new String[]{"outputClass"},
                new String[][]{null} // Categorical columns need domain, pass null for Numerical and String columns
        );
        return result.vec(0);
    }

    public Frame splitData(final int feature, final double threshold /* are all features double ? todo*/) {
//        System.out.println("f" + feature);
        // todo fixed size is bad
        byte[] outputTypes = new byte[]{Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM};
//            Object[] outputTypes = Arrays.stream(train.names()).map(n -> Vec.T_NUM).collect(Collectors.toList()).;
        Key<Frame> outputKey = Key.make("result_frame");
//            String[] outputColNames = (String[]) (Stream.concat(Arrays.stream(train.names()).map(n -> n + "Left"),
//                    Arrays.stream(train.names()).map(n -> n + "Right")).toArray()); //new String[]{"FirstLeft", "SecondLeft"};
        String[] outputColNames = (Stream.concat(Arrays.stream(trainData.names()).map(n -> n + "Left"),
                Arrays.stream(features).map(n -> n + "Right")).toArray(String[]::new)); //new String[]{"FirstLeft", "SecondLeft"};
        // todo fixed size is bad
        String[][] outputDomains = new String[][]{null, null, null, null, null, null};


        // Define task
        SplitFrameMRTask task = new SplitFrameMRTask(feature, threshold);

        // Run task
        task.doAll(outputTypes, trainData);

        return task.outputFrame(
                null, // The output Frame will be stored in DKV and you can access it with this Key, can be null, in case you don't wanted in DKV
                outputColNames,
                outputDomains // Categorical columns need domain, pass null for Numerical and String columns
        );
    }

//    private int selectDecisionValueForList(final Frame data) {
//        GetClassCountsMRTask task = new GetClassCountsMRTask(predictionColumnIndex);
//        task.doAll(data);
//        if (task.count0 > task.count1) {
//            System.out.println(0);
//            return 0;
//        } else {
//            System.out.println(1);
//            return 1;
//        }
//    }

//    5
//    0/5, 1/4, 2/3, 3/2, 4/1, 5/0
    // todo - the same task as in the previous method is used
    private Double getZeroRatio(final Frame data) {
        GetClassCountsMRTask task = new GetClassCountsMRTask(predictionColumnIndex);
        task.doAll(data);
        System.out.println(task.count0 + " " + task.count1);
        return task.count0 * 1.0 / (task.count0 + task.count1);
//        if(task.count1 == 0) {
//            return Double.POSITIVE_INFINITY;
//        }
//        return task.count0 * 1.0 / task.count1;
    }


    public Node getRoot() {
        return root;
    }
    
    
    //    @Test
//    public void testMapReduce() {
//        try {
//            Scope.enter();
//            Frame train = Scope.track(parseTestFile("smalldata/anomaly/single_blob.csv"));
//            new PrintMRTask().doAll(train);
//
//        } finally {
//            Scope.exit();
//        }
//    }

//    class SplitDataMRTask extends MRTask<SplitDataMRTask> {
//
//        @Override // pass custom parameter? (SplitInfo)
//        public void map(Chunk[] cs, int featureIndex, double threshold, Chunk[] csLeft, Chunk[] csRight) {
//            int numCols = cs.length;
//            System.out.println("numCols = " + numCols);
//            for (int row = 0; row < cs[0]._len; row++) {
//                if(cs[featureIndex].atd(row) <= threshold) {
//                    // add to left split
//                } else {
//                    // add row to the right split
//                }
//                for (int col = 0; col < numCols; col++) {
//                    System.out.print(cs[col].atd(row) + ", ");
//                }
//                System.out.println("");
//            }
//        }
//    }

}


// todo - analyze input data to get candidates for threshold
// todo - how to effectively work with 
// add to class in constructor
// ist (aist)
// Node should impleent aist
// DKV.put(…) - to claster to see another claster
//DKV.get(…)
// see random forest with 1 tree
// bin data (go not by step but by count of data)
// data must be solved by histogram because of the size
// see rebase to forked (new remote?)
