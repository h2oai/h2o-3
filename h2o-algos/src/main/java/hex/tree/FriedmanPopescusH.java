package hex.tree;

import hex.genmodel.algos.tree.SharedTreeNode;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
import water.DKV;
import water.Key;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.Val;
import water.util.ArrayUtils;
import water.util.VecUtils;

import java.util.*;

/**
 * Calculates Friedman and Popescu's H statistics, in order to test for the presence of an interaction between specified variables in h2o gbm and xgb models.
 * H varies from 0 to 1. It will have a value of 0 if the model exhibits no interaction between specified variables and a correspondingly larger value for a 
 * stronger interaction effect between them. NaN is returned if a computation is spoiled by weak main effects and rounding errors.
 *
 * See Jerome H. Friedman and Bogdan E. Popescu, 2008, "Predictive learning via rule ensembles", *Ann. Appl. Stat.*
 * **2**:916-954, http://projecteuclid.org/download/pdfview_1/euclid.aoas/1223908046, s. 8.1.
 *         
 * Reference implementation: https://pypi.org/project/sklearn-gbmi/
 * */
public class FriedmanPopescusH {
    
    public static double h(Frame frame, String[] vars, double learnRate, SharedTreeSubgraph[][] sharedTreeSubgraphs) {
        Frame filteredFrame = filterFrame(frame, vars);
        int[] modelIds = getModelIds(frame.names(), vars);
        Map<String, Frame> fValues = new HashMap<>();

        int numCols = filteredFrame.numCols();
        int[] colIds = new int[numCols];
        for (int i = 0; i < numCols; i++) {
            colIds[i] = i;
        }

        for (int i = numCols; i > 0; i--) {
            List<int[]> currCombinations = combinations(colIds, i);
            for (int j = 0; j < currCombinations.size(); j++) {
                int[] currCombination = currCombinations.get(j);
                String[] cols = getCurrCombinationCols(currCombination, vars);
                Integer[] currModelIds = getCurrentCombinationModelIds(currCombination, modelIds);
                fValues.put(Arrays.toString(currCombination), computeFValues(currModelIds, filteredFrame, cols, learnRate, sharedTreeSubgraphs));
            }
        }
        return computeHValue(fValues, filteredFrame, colIds);
        
    }

    static Integer[] getCurrentCombinationModelIds(int[] currCombination, int[] modelIds) {
        Integer[] currCombinationCols = new Integer[currCombination.length];
        for (int i = 0; i < currCombination.length; i++) {
            currCombinationCols[i] = modelIds[currCombination[i]];
        }
        return currCombinationCols;
    }
    
    static double computeHValue(Map<String, Frame> fValues, Frame filteredFrame, int[] inds) {
        if (filteredFrame._key == null)
            filteredFrame._key = Key.make();
        Frame uniqueWithCounts = uniqueRowsWithCounts(filteredFrame);
        long uniqHeight = uniqueWithCounts.numRows();
        Vec numerEls = Vec.makeZero(uniqHeight);
        Vec denomEls = Vec.makeZero(uniqHeight);
        for (long i = 0; i < uniqHeight; i++) {
            int sign = 1;
            for (int n = inds.length; n > 0; n--) {
                List<int[]> currCombinations = combinations(inds, n);
                for (int j = 0; j < currCombinations.size(); j++) {
                    double fValue = findFValue(i, (int[])currCombinations.toArray()[j], fValues.get(Arrays.toString((int[])currCombinations.toArray()[j])), filteredFrame);
                    numerEls.set(i, numerEls.at(i) + (float)sign * (float)fValue);
                }
                sign *= -1;
            }
            denomEls.set(i, (float)fValues.get(Arrays.toString(inds)).vec(0).at(i));
        }
        double numer = new Transform(2).doAll(numerEls, uniqueWithCounts.vec("nrow")).result;
        double denom = new Transform(2).doAll(denomEls, uniqueWithCounts.vec("nrow")).result;
        return numer < denom ? Math.sqrt(numer/denom) : Double.NaN;
    }

    private static class Transform extends MRTask<Transform> {
        double result;
        int power;
        
        Transform(int power) {
            this.power = power;
        }
        
        @Override public void map( Chunk[] bvs ) {
            result = 0;
            int len = bvs[0]._len;
            for (int i = 0; i < len; i++) {
                result += Math.pow(bvs[0].atd(i), 2) * bvs[1].atd(i);
            }
        }
        @Override public void reduce(Transform mrt ) {
            result += mrt.result;
        }
    }
    
    static double[] getValueToFindFValueFor(int[] currCombination, Frame filteredFrame, long i) {
        int combinationLength = currCombination.length;
        double[] value = new double[combinationLength];
        for (int j = 0; j < combinationLength; j++) {
            value[j] = filteredFrame.vec(currCombination[j]).at(i);
        }
        return value;
    }
    
    static double findFValue(long i, int[] currCombination, Frame currFValues, Frame filteredFrame) {
        double[] valueToFindFValueFor = getValueToFindFValueFor(currCombination, filteredFrame, i);
        String[] currNames = getCurrCombinationNames(currCombination, filteredFrame.names());
        FindFValue findFValueTask = new FindFValue(valueToFindFValueFor, currNames, currFValues._names, 1e-5);
        Frame result = findFValueTask.doAll(Vec.T_NUM, currFValues).outputFrame();
        if (result.numRows() == 0) {
            throw new RuntimeException("FValue was not found!" + Arrays.toString(currCombination) + "value: " + Arrays.toString(valueToFindFValueFor));
        } else {
            return result.vec(0).at(0);
        }
    }

    static class FindFValue extends MRTask<FindFValue> {
        double[] valueToFindFValueFor;
        String[] currNames;
        String[] currFValuesNames;
        double eps;

        FindFValue(double[] valueToFindFValueFor, String[] currNames, String[] currFValuesNames, double eps) {
            this.valueToFindFValueFor = valueToFindFValueFor;
            this.currNames = currNames;
            this.currFValuesNames = currFValuesNames;
            this.eps = eps;
        }

        @Override public void map(Chunk[] cs, NewChunk[] nc) {
            int count = 0;
            for (int iRow = 0; iRow < cs[0].len(); iRow++) {
                for (int k = 0; k < valueToFindFValueFor.length; k++) {
                    int id = ArrayUtils.find(currFValuesNames, currNames[k]);
                    if (Math.abs(valueToFindFValueFor[k] - cs[id].atd(iRow)) < eps) {
                        count++;
                    }
                }
                if (count == valueToFindFValueFor.length) {
                    nc[0].addNum(cs[0].atd(iRow));
                } else {
                    count = 0;
                }
            }
        }
    }
    
    static String[] getCurrCombinationNames(int[] currCombination, String[] names) {
        String[] currNames = new String[currCombination.length];
        for (int j = 0; j < currCombination.length; j++) {
            currNames[j] = names[currCombination[j]];
        }
        return currNames;
    }
    
    static String[] getCurrCombinationCols(int[] currCombination, String[] vars) {
        String[] currCombinationCols = new String[currCombination.length];
        for (int i = 0; i < currCombination.length; i++) {
            currCombinationCols[i] = vars[currCombination[i]];
        }
        return currCombinationCols;
    }
    
    
    static int findFirstNumericalColumn(Frame frame) {
        for (int i = 0; i < frame.names().length; i++) {
            if (frame.vec(i).isNumeric())
                return i;
        }
        return -1;
    }
    
    static Frame uniqueRowsWithCounts(Frame frame) {
        DKV.put(frame);
        StringBuilder sb = new StringBuilder("(GB ");
        String[] cols = frame.names();
        sb.append(frame._key.toString());
        sb.append(" [");
        for (int i = 0; i < cols.length; i++) {
            if (i != 0) sb.append(",");
            sb.append(i);
        }
        sb.append("] ");
        int i = findFirstNumericalColumn(frame);
        if (i == -1) {
           frame.add("nrow", Vec.makeOne(frame.numRows()));
           return frame;
        }
        sb.append(" nrow ").append(i).append(" \"all\")");
        
        Val val = Rapids.exec(sb.toString());
        DKV.remove(frame._key);
        return val.getFrame();
    }
    
    
    static Frame computeFValues(Integer[] modelIds, Frame filteredFrame, String[] cols, double learnRate, SharedTreeSubgraph[][] sharedTreeSubgraphs) {
        // filter frame -> only curr combination cols will be used
        filteredFrame = filterFrame(filteredFrame, cols);
        filteredFrame = new Frame(Key.make(), filteredFrame.names(), filteredFrame.vecs());
        Frame uniqueWithCounts = uniqueRowsWithCounts(filteredFrame);
        Frame uncenteredFvalues = new Frame(partialDependence(modelIds, uniqueWithCounts, learnRate, sharedTreeSubgraphs).vec(0));
        VecUtils.DotProduct multiply = new VecUtils.DotProduct().doAll(uniqueWithCounts.vec("nrow"), uncenteredFvalues.vec(0));
        double meanUncenteredFValue = multiply.result / filteredFrame.numRows();
        for (int i = 0; i < uncenteredFvalues.numRows(); i++) {
            uncenteredFvalues.vec(0).set(i, uncenteredFvalues.vec(0).at(i) - meanUncenteredFValue);
        }
        return uncenteredFvalues.add(uniqueWithCounts);
    }
    
    
    
    static Frame partialDependence(Integer[] modelIds, Frame uniqueWithCounts, double learnRate, SharedTreeSubgraph[][] sharedTreeSubgraphs) {
        Frame result = new Frame();
        int nclasses = sharedTreeSubgraphs[0].length;
        int ntrees = sharedTreeSubgraphs.length;
        for (int treeClass = 0; treeClass < nclasses; treeClass++) {
            Vec pdp = Vec.makeZero(uniqueWithCounts.numRows());
            for (int i = 0; i < ntrees; i++) {
                SharedTreeSubgraph sharedTreeSubgraph = sharedTreeSubgraphs[i][treeClass];
                Vec currTreePdp = partialDependenceTree(sharedTreeSubgraph, modelIds, learnRate, uniqueWithCounts);
                for (long j = 0; j < uniqueWithCounts.numRows(); j++) {
                    pdp.set(j, pdp.at(j) + currTreePdp.at(j));
                }
            }
            result.add("pdp_C" + treeClass  , pdp);
        }
        return result;
    }

    public static double[] add(double[] first, double[] second) { 
        int length = first.length < second.length ? first.length : second.length;
        double[] result = new double[length]; 
        for (int i = 0; i < length; i++) { 
            result[i] = first[i] + second[i]; 
        } 
        return result; 
    }
    
    
    static Frame filterFrame(Frame frame, String[] cols) {
        Frame frame1 = new Frame();
        frame1.add(cols, frame.vecs(cols));
        return frame1;
    }


    static int[] getModelIds(String[] frameNames, String[] vars) {
        int[] modelIds = new int[vars.length];
        Arrays.fill(modelIds, -1);
        for (int i = 0; i < vars.length; i++) {
            for (int j = 0; j < frameNames.length; j++) {
                if (vars[i].equals(frameNames[j])) {
                    modelIds[i] = j;
                }
            }
            if (modelIds[i] == -1) {
                throw new RuntimeException("Column " + vars[i] + " is not present in the input frame!");
            }
        }
        return modelIds;
    }

    static List<int[]> combinations(int[] vals, int combinationSize) {
        List<int[]> overallResult = new ArrayList<>();
        combinations(vals, combinationSize, 0, new int[combinationSize], overallResult);
        return overallResult;
    }
    
    private static void combinations(int[] arr, int len, int startPosition, int[] result, List<int[]> overallResult) {
        if (len == 0) {
            overallResult.add(result.clone());
            return;
        }
        for (int i = startPosition; i <= arr.length-len; i++){
            result[result.length - len] = arr[i];
            combinations(arr, len - 1, i + 1, result, overallResult);
        }
    }


    /**
     * For each row in ``X`` a tree traversal is performed.
     * Each traversal starts from the root with weight 1.0.
     *
     * At each non-terminal node that splits on a target variable either
     * the left child or the right child is visited based on the feature
     * value of the current sample and the weight is not modified.
     * At each non-terminal node that splits on a complementary feature
     * both children are visited and the weight is multiplied by the fraction
     * of training samples which went to each child.
     *
     * At each terminal node the value of the node is multiplied by the
     * current weight (weights sum to 1 for all visited terminal nodes).
     *
     * @param tree tree to traverse
     * @param targetFeature the set of target features for which the partial dependence should be evaluated
     * @param learnRate constant scaling factor for the leaf predictions
     * @param grid the grid points on which the partial dependence should be evaluated
     *             
     * @return Vec with the resulting partial dependence values for each point of the input grid         
     */
    public static Vec partialDependenceTree(SharedTreeSubgraph tree, Integer[] targetFeature, double learnRate, Frame grid) {
        Vec outVec = Vec.makeZero(grid.numRows());
        
        int stackSize;
        SharedTreeNode[] nodeStackAr = new SharedTreeNode[tree.nodesArray.size() * 2];
        Double[] weightStackAr = new Double[tree.nodesArray.size() * 2];
        Arrays.fill(weightStackAr, 1.0);
        double totalWeight;
        SharedTreeNode currNode;
        double currWeight;
        
        for (long i = 0; i < grid.numRows(); i++) {
            stackSize = 1;
            nodeStackAr[0] = tree.rootNode;
            weightStackAr[0] = 1.0;
            totalWeight = 0.0;
            
            while (stackSize > 0) {
                // get top node on stack
                stackSize -= 1;
                currNode = nodeStackAr[stackSize];
                
                if (currNode.isLeaf()) {
                    outVec.set(i, outVec.at(i) +  weightStackAr[stackSize] * currNode.getPredValue() * learnRate);
                    totalWeight += weightStackAr[stackSize];
                } else {
                    // non-terminal node:
                    int featureId = ArrayUtils.indexOfSorted(targetFeature, currNode.getColId());
                    if (featureId != -1) {
                        // split feature in target set
                        // push left or right child on stack
                        if (grid.vec(featureId).at(i) <= currNode.getSplitValue()) {
                            // left
                            nodeStackAr[stackSize] = currNode.getLeftChild();
                        } else {
                            nodeStackAr[stackSize] = currNode.getRightChild();
                        }
                        stackSize += 1;
                    } else {
                        double left_sample_frac;
                        // split feature complement set
                        // push both children onto stack
                        currWeight = weightStackAr[stackSize];
                        // push left
                        nodeStackAr[stackSize] = currNode.getLeftChild();
                        left_sample_frac = currNode.getLeftChild().getWeight() / currNode.getWeight();
                        weightStackAr[stackSize] = currWeight * left_sample_frac;
                        stackSize++;
                        // push right
                        nodeStackAr[stackSize] = currNode.getRightChild();
                        weightStackAr[stackSize] = currWeight * (1.0 - left_sample_frac);
                        stackSize++;
                    }
                }
            }
            if (!(0.999 < totalWeight && totalWeight < 1.001)) {
                throw new RuntimeException("Total weight should be 1.0 but was " + totalWeight);
            }
        }
        return outVec;
    }

}

