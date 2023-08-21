package hex;

import hex.genmodel.algos.tree.SharedTreeNode;
import org.apache.commons.lang.mutable.MutableInt;
import water.util.TwoDimTable;

import java.util.*;
import java.util.stream.Collectors;

public class FeatureInteractions {
    
    private final HashMap<String, FeatureInteraction> map;

    public FeatureInteractions() {
        this.map = new HashMap<>();
    }

    public void mergeWith(FeatureInteractions featureInteractions) {
        for (Map.Entry<String,FeatureInteraction> currEntry : featureInteractions.entrySet()) {
            if (this.map.containsKey(currEntry.getKey())) {
                FeatureInteraction leftFeatureInteraction = this.get(currEntry.getKey());
                FeatureInteraction rightFeatureInteraction = currEntry.getValue();
                leftFeatureInteraction.gain += rightFeatureInteraction.gain;
                leftFeatureInteraction.cover += rightFeatureInteraction.cover;
                leftFeatureInteraction.fScore += rightFeatureInteraction.fScore;
                leftFeatureInteraction.fScoreWeighted += rightFeatureInteraction.fScoreWeighted;
                leftFeatureInteraction.averageFScoreWeighted = leftFeatureInteraction.fScoreWeighted / leftFeatureInteraction.fScore;
                leftFeatureInteraction.averageGain = leftFeatureInteraction.gain / leftFeatureInteraction.fScore;
                leftFeatureInteraction.expectedGain += rightFeatureInteraction.expectedGain;
                leftFeatureInteraction.treeIndex += rightFeatureInteraction.treeIndex;
                leftFeatureInteraction.averageTreeIndex = leftFeatureInteraction.treeIndex / leftFeatureInteraction.fScore;
                leftFeatureInteraction.treeDepth += rightFeatureInteraction.treeDepth;
                leftFeatureInteraction.averageTreeDepth = leftFeatureInteraction.treeDepth / leftFeatureInteraction.fScore;
                leftFeatureInteraction.sumLeafCoversRight += rightFeatureInteraction.sumLeafCoversRight;
                leftFeatureInteraction.sumLeafCoversLeft += rightFeatureInteraction.sumLeafCoversLeft;
                leftFeatureInteraction.sumLeafValuesRight += rightFeatureInteraction.sumLeafValuesRight;
                leftFeatureInteraction.sumLeafValuesLeft += rightFeatureInteraction.sumLeafValuesLeft;
                leftFeatureInteraction.splitValueHistogram.merge(rightFeatureInteraction.splitValueHistogram);
            } else {
                this.put(currEntry.getKey(), currEntry.getValue());
            }
        }
    }
    
    public boolean isEmpty(){
        return entrySet().isEmpty();
    }

    public int maxDepth() {
        if(isEmpty()) return 0;
        return Collections.max(this.entrySet(), Comparator.comparingInt(entry -> entry.getValue().depth)).getValue().depth;
    }
    
    public TwoDimTable[] getAsTable() {
        if(isEmpty()) return  null;
        int maxDepth = maxDepth();
        TwoDimTable[] twoDimTables = new TwoDimTable[maxDepth + 1];
        for (int depth = 0; depth < maxDepth + 1; depth++) {
            twoDimTables[depth] = constructFeatureInteractionsTable(depth);
        }
        return twoDimTables;
    }
    
    private List<FeatureInteraction> getFeatureInteractionsOfDepth(int depthRequired) {
        return this.entrySet()
                .stream()
                .filter(entry -> entry.getValue().depth == depthRequired)
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }

    private List<FeatureInteraction> getFeatureInteractionsWithLeafStatistics() {
        return this.entrySet()
                .stream()
                .filter(entry -> entry.getValue().hasLeafStatistics == true)
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }
    
    private TwoDimTable constructFeatureInteractionsTable(int depth) {
        assert depth >= 0  : "Depth has to be >= 0.";
        String[] colHeaders = new String[] {"Interaction", "Gain", "FScore", "wFScore", "Average wFScore", "Average Gain", 
                "Expected Gain", "Gain Rank", "FScore Rank", "wFScore Rank", "Avg wFScore Rank", "Avg Gain Rank", 
                "Expected Gain Rank", "Average Rank", "Average Tree Index", "Average Tree Depth"};
        String[] colTypes = new String[] {"string", "double", "double", "double", "double", "double",
                "double", "int", "int", "int", "int", "int",
                "int", "double", "double", "double"};
        String[] colFormat = new String[] {"%s", "%.5f", "%.5f", "%.5f", "%.5f", "%.5f",
                "%.5f", "%d", "%d", "%d", "%d", "%d",
                "%d", "%.5f", "%.5f", "%.5f"};

        List<FeatureInteraction> featureInteractions = getFeatureInteractionsOfDepth(depth);
        int numRows = featureInteractions.size();
        List<FeatureInteraction> gainSorted = new ArrayList(featureInteractions);
        gainSorted.sort(Comparator.comparing(entry -> -entry.gain));
        List<FeatureInteraction> fScoreSorted = new ArrayList(featureInteractions);
        fScoreSorted.sort(Comparator.comparing(entry -> -entry.fScore));
        List<FeatureInteraction> fScoreWeightedSorted = new ArrayList(featureInteractions);
        fScoreWeightedSorted.sort(Comparator.comparing(entry -> -entry.fScoreWeighted));
        List<FeatureInteraction> averagefScoreWeightedSorted = new ArrayList(featureInteractions);
        averagefScoreWeightedSorted.sort(Comparator.comparing(entry -> -entry.averageFScoreWeighted));
        List<FeatureInteraction> averageGainSorted = new ArrayList(featureInteractions);
        averageGainSorted.sort(Comparator.comparing(entry -> -entry.averageGain));
        List<FeatureInteraction> expectedGainSorted = new ArrayList(featureInteractions);
        expectedGainSorted.sort(Comparator.comparing(entry -> -entry.expectedGain));

        TwoDimTable table = new TwoDimTable(
          "Interaction Depth " + depth, null,
          new String[numRows],
          colHeaders,
          colTypes,
          colFormat,
          "");
        
        for (int i = 0; i < numRows; i++) {
            String name = featureInteractions.get(i).name;
            table.set(i, 0, name);
            table.set(i, 1, featureInteractions.get(i).gain);
            table.set(i, 2, featureInteractions.get(i).fScore);
            table.set(i, 3, featureInteractions.get(i).fScoreWeighted);
            table.set(i, 4, featureInteractions.get(i).averageFScoreWeighted);
            table.set(i, 5, featureInteractions.get(i).averageGain);
            table.set(i, 6, featureInteractions.get(i).expectedGain);
            double gainRank = indexOfInteractionWithName(name, gainSorted) + 1;
            table.set(i, 7, gainRank);
            double FScoreRank = indexOfInteractionWithName(name, fScoreSorted) + 1;
            table.set(i, 8, FScoreRank);
            double FScoreWeightedRank = indexOfInteractionWithName(name, fScoreWeightedSorted) + 1;
            table.set(i, 9, FScoreWeightedRank);
            double avgFScoreWeightedRank = indexOfInteractionWithName(name, averagefScoreWeightedSorted) + 1;
            table.set(i, 10, avgFScoreWeightedRank);
            double averageGain = indexOfInteractionWithName(name, averageGainSorted) + 1;
            table.set(i, 11, averageGain);
            double expectedGain = indexOfInteractionWithName(name, expectedGainSorted) + 1;
            table.set(i, 12, expectedGain);
            table.set(i, 13, (gainRank + FScoreRank + FScoreWeightedRank + avgFScoreWeightedRank + averageGain + expectedGain) / 6);
            table.set(i, 14, featureInteractions.get(i).averageTreeIndex);
            table.set(i, 15, featureInteractions.get(i).averageTreeDepth);
        }
        
        return table;
    }

    private int indexOfInteractionWithName(String name, List<FeatureInteraction> featureInteractions) {
        for (int i = 0; i < featureInteractions.size(); i++)
            if (featureInteractions.get(i).name == name)
                return i;
        
        return -1;
    }

    public TwoDimTable getLeafStatisticsTable() {
        String[] colHeaders = new String[] {"Interaction", "Sum Leaf Values Left", "Sum Leaf Values Right", "Sum Leaf Covers Left", "Sum Leaf Covers Right"};
        String[] colTypes = new String[] {"string", "double", "double", "double", "double"};
        String[] colFormat = new String[] {"%s", "%.5f", "%.5f", "%.5f", "%.5f"};

        List<FeatureInteraction> featureInteractions = getFeatureInteractionsWithLeafStatistics();
        int numRows = featureInteractions.size();

        TwoDimTable table = new TwoDimTable(
                "Leaf Statistics", null,
                new String[numRows],
                colHeaders,
                colTypes,
                colFormat,
                "");

        for (int i = 0; i < numRows; i++) {
            table.set(i, 0, featureInteractions.get(i).name);
            table.set(i, 1, featureInteractions.get(i).sumLeafValuesLeft);
            table.set(i, 2, featureInteractions.get(i).sumLeafValuesRight);
            table.set(i, 3, featureInteractions.get(i).sumLeafCoversLeft);
            table.set(i, 4, featureInteractions.get(i).sumLeafCoversRight);
        }
        
        return table;
    }
    
    public TwoDimTable[] getSplitValueHistograms() {
        List<FeatureInteraction> featureInteractions = getFeatureInteractionsOfDepth(0);
        int numHistograms = featureInteractions.size();
        
        TwoDimTable[] splitValueHistograms = new TwoDimTable[numHistograms];
        
        for (int i = 0; i < numHistograms; i++) {
            splitValueHistograms[i] = constructHistogramForFeatureInteraction(featureInteractions.get(i));
        }
        
        return splitValueHistograms;
    }
    
    private TwoDimTable constructHistogramForFeatureInteraction(FeatureInteraction featureInteraction) {
        String[] colHeaders = new String[] {"Split Value", "Count"};
        String[] colTypes = new String[] {"double", "int"};
        String[] colFormat = new String[] {"%.5f", "%d"};
        
        int N = featureInteraction.splitValueHistogram.entrySet().size();

        TwoDimTable table = new TwoDimTable(
                featureInteraction.name + " Split Value Histogram", null,
                new String[N],
                colHeaders,
                colTypes,
                colFormat,
                "");
        int i = 0;
        for (Map.Entry<Double, MutableInt> entry : featureInteraction.splitValueHistogram.entrySet()) {
            table.set(i, 0, entry.getKey());
            table.set(i, 1, entry.getValue().intValue());
            i++;
        }
        
        return table;
    }
    
    public int size() {
        return map.size();
    }

    public FeatureInteraction get(String key) {
        return map.get(key);
    }
    
    public FeatureInteraction put(String key, FeatureInteraction value) {
        return map.put(key, value);
    }

    public Set<Map.Entry<String, FeatureInteraction>> entrySet() {
        return map.entrySet();
    }

    public static void collectFeatureInteractions(SharedTreeNode node, List<SharedTreeNode> interactionPath,
                                                  double currentGain, double currentCover, double pathProba, int depth, int deepening,
                                                  FeatureInteractions featureInteractions, Set<String> memo, int maxInteractionDepth,
                                                  int maxTreeDepth, int maxDeepening, int treeIndex, boolean useSquaredErrorForGain) {

        if (node.isLeaf() || depth == maxTreeDepth) {
            return;
        }

        interactionPath.add(node);
        currentGain += node.getGain(useSquaredErrorForGain);
        currentCover += node.getWeight();

        double ppl = pathProba * (node.getLeftChild().getWeight() / node.getWeight());
        double ppr = pathProba * (node.getRightChild().getWeight() / node.getWeight());

        FeatureInteraction featureInteraction = new FeatureInteraction(interactionPath, currentGain, currentCover, pathProba, depth, 1, treeIndex);

        if ((depth < maxDeepening) || (maxDeepening < 0)) {
            collectFeatureInteractions(node.getLeftChild(), new ArrayList<>(), 0, 0, ppl, depth + 1,
                    deepening + 1, featureInteractions, memo, maxInteractionDepth, maxTreeDepth, maxDeepening, treeIndex, useSquaredErrorForGain);
            collectFeatureInteractions(node.getRightChild(), new ArrayList<>(), 0, 0, ppr, depth + 1,
                    deepening + 1, featureInteractions, memo, maxInteractionDepth, maxTreeDepth, maxDeepening, treeIndex, useSquaredErrorForGain);
        }

        String path = FeatureInteraction.interactionPathToStr(interactionPath, true, true);

        FeatureInteraction foundFI = featureInteractions.get(featureInteraction.name);
        if (foundFI == null) {
            featureInteractions.put(featureInteraction.name, featureInteraction);
            memo.add(path);
        } else {
            if (memo.contains(path)) {
                return;
            }
            memo.add(path);
            foundFI.gain += currentGain;
            foundFI.cover += currentCover;
            foundFI.fScore += 1;
            foundFI.fScoreWeighted += pathProba;
            foundFI.averageFScoreWeighted = foundFI.fScoreWeighted / foundFI.fScore;
            foundFI.averageGain = foundFI.gain / foundFI.fScore;
            foundFI.expectedGain += currentGain * pathProba;
            foundFI.treeDepth += depth;
            foundFI.averageTreeDepth = foundFI.treeDepth / foundFI.fScore;
            foundFI.treeIndex += treeIndex;
            foundFI.averageTreeIndex = foundFI.treeIndex / foundFI.fScore;
            foundFI.splitValueHistogram.merge(featureInteraction.splitValueHistogram);
        }

        if (interactionPath.size() - 1 == maxInteractionDepth)
            return;

        foundFI = featureInteractions.get(featureInteraction.name);
        SharedTreeNode leftChild = node.getLeftChild();
        if (leftChild.isLeaf() && deepening == 0) {
            foundFI.sumLeafValuesLeft += leftChild.getLeafValue();
            foundFI.sumLeafCoversLeft += leftChild.getWeight();
            foundFI.hasLeafStatistics = true;
        }

        SharedTreeNode rightChild = node.getRightChild();
        if (rightChild.isLeaf() && deepening == 0) {
            foundFI.sumLeafValuesRight += rightChild.getLeafValue();
            foundFI.sumLeafCoversRight += rightChild.getWeight();
            foundFI.hasLeafStatistics = true;
        }

        collectFeatureInteractions(leftChild, new ArrayList<>(interactionPath), currentGain, currentGain, ppl,
                depth + 1, deepening, featureInteractions, memo, maxInteractionDepth, maxTreeDepth, maxDeepening, treeIndex, useSquaredErrorForGain);
        collectFeatureInteractions(node.getRightChild(), new ArrayList<>(interactionPath), currentGain, currentGain, ppr,
                depth + 1, deepening, featureInteractions, memo, maxInteractionDepth, maxTreeDepth, maxDeepening, treeIndex, useSquaredErrorForGain);
    }
    
    public static TwoDimTable[][] getFeatureInteractionsTable(FeatureInteractions featureInteractions) {
        if(featureInteractions == null) {
            return null;
        }
        TwoDimTable[][] table = new TwoDimTable[3][];
        table[0] = featureInteractions.getAsTable();
        table[1] = new TwoDimTable[]{featureInteractions.getLeafStatisticsTable()};
        table[2] = featureInteractions.getSplitValueHistograms();
        return table;
    }
}
