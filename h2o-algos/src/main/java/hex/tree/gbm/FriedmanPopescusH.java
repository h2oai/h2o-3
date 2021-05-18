package hex.tree.gbm;

import hex.genmodel.algos.tree.SharedTreeNode;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
import water.DKV;
import water.Key;
import water.Scope;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.Val;
import water.util.ArrayUtils;

import java.util.*;

public class FriedmanPopescusH {
    
    public static double h(Frame frame, String[] vars, GBMModel gbmModel) {
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
                fValues.put(Arrays.toString(currCombination), computeFValues(gbmModel, currModelIds, filteredFrame, cols));
            }
        }
        return computeHValue(fValues, filteredFrame, modelIds);
        
    }

    static Integer[] getCurrentCombinationModelIds(int[] currCombination, int[] modelIds) {
        Integer[] currCombinationCols = new Integer[currCombination.length];
        for (int i = 0; i < currCombination.length; i++) {
            currCombinationCols[i] = modelIds[currCombination[i]];
        }
        return currCombinationCols;
    }     


    public static double h_test(Frame frame, String[] vars, SharedTreeSubgraph tree) {
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
                fValues.put(Arrays.toString(currCombination), computeFValuesForTest(tree, currModelIds, filteredFrame, cols, 0.1));
            }
        }
        return computeHValue(fValues, filteredFrame, modelIds);
    }
    
    static double computeHValue(Map<String, Frame> fValues, Frame filteredFrame, int[] inds) {
        if (filteredFrame._key == null)
            filteredFrame._key = Key.make();
        Frame uniqueWithCounts = uniqueRowsWithCounts(filteredFrame);
        int uniqHeight = (int)uniqueWithCounts.numRows();
        float[] numerEls = new float[uniqHeight];
        float[] denomEls = new float[uniqHeight];
        for (int i = 0; i < uniqHeight; i++) {
            int sign = 1;
            for (int n = inds.length; n > 0; n--) {
                List<int[]> currCombinations = combinations(inds, n);
                for (int j = 0; j < currCombinations.size(); j++) {
                    // the id used in line numerEls[i] = ... has to be the order of current line (i) in frame sorted by 
                    // the first-indice-column of current combination
                    int firstIdOfCurrCombination = ((int[])currCombinations.toArray()[j])[0];
                    float[] currArrSorted = FriedmanPopescusH.FrameTo2DArr(filteredFrame, true)[firstIdOfCurrCombination];
                    Arrays.sort(currArrSorted);
                    int id = Arrays.binarySearch(currArrSorted, (float)filteredFrame.vec(firstIdOfCurrCombination).at(i));
                    Arrays.sort(FriedmanPopescusH.FrameTo2DArr(filteredFrame, true)[1]);
                    numerEls[i] += (float)sign * (float)fValues.get(Arrays.toString((int[])currCombinations.toArray()[j])).vec(0).at(id);
                }
                sign *= -1;
            }
            denomEls[i] = (float)fValues.get(Arrays.toString(inds)).vec(0).at(i);
        }
        float[][] counts = FrameTo2DArr(new Frame(uniqueWithCounts.vec("nrow")), false);
        float[][] numer = matrixMultiply(new float[][] {powArray(numerEls, 2)}, counts);
        float[][] denom = matrixMultiply(new float[][] {powArray(denomEls,2)}, counts);
        assert numer.length == 1; assert numer[0].length == 1;
        assert denom.length == 1; assert denom[0].length == 1;
        return numer[0][0] < denom[0][0] ? Math.sqrt(numer[0][0]/denom[0][0]) : Double.NaN;
    }
    
    static float[] powArray(float[] array, int power) {
        float[] result = new float[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = (float) Math.pow(array[i], power);
        }
        return result;
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
    
    
    static Frame computeFValues(GBMModel model, Integer[] modelIds, Frame filteredFrame, String[] cols) {
        // filter frame -> only curr combination cols will be used
        filteredFrame = filterFrame(filteredFrame, cols);
        filteredFrame = new Frame(Key.make(), filteredFrame.names(), filteredFrame.vecs());
        Frame uniqueWithCounts = uniqueRowsWithCounts(filteredFrame);
        Frame uncenteredFvalues = partialDependence(model, modelIds, uniqueWithCounts);
        float[][] counts = FrameTo2DArr(new Frame(uniqueWithCounts.vec("nrow")), true);
        float[][] fValues = FrameTo2DArr(uncenteredFvalues, false);
        float[][] meanUncenteredFVal = matrixScalarDivision(matrixMultiply(counts, fValues), filteredFrame.numRows());
        // todo test this with multiclass
        assert uncenteredFvalues.numCols() == meanUncenteredFVal[0].length;
        for (int i = 0; i < uncenteredFvalues.numRows(); i++) {
            for (int j = 0; j < uncenteredFvalues.numCols(); j++) {
                uncenteredFvalues.vec(j).set(i, uncenteredFvalues.vec(j).at(i) - meanUncenteredFVal[0][i]);
            }
        }
        return uncenteredFvalues;
    }

    static Frame computeFValuesForTest(SharedTreeSubgraph tree, Integer[] modelIds, Frame filteredFrame, String[] cols, double learnRate) {
        // filter frame -> only curr combination cols will be used
        filteredFrame = filterFrame(filteredFrame, cols);
        filteredFrame = new Frame(Key.make(), filteredFrame.names(), filteredFrame.vecs());
        Frame uniqueWithCounts = uniqueRowsWithCounts(filteredFrame);
        Frame uncenteredFvalues = partialDependence(tree, modelIds, uniqueWithCounts, learnRate);
        float[][] counts = FrameTo2DArr(new Frame(uniqueWithCounts.vec("nrow")), true);
        float[][] fValues = FrameTo2DArr(uncenteredFvalues, false);
        float[][] meanUncenteredFVal = matrixScalarDivision(matrixMultiply(counts, fValues), filteredFrame.numRows());
        // todo test this with multiclass
        assert uncenteredFvalues.numCols() == meanUncenteredFVal[0].length;
        for (int i = 0; i < uncenteredFvalues.numRows(); i++) {
            for (int j = 0; j < uncenteredFvalues.numCols(); j++) {
                uncenteredFvalues.vec(j).set(i, uncenteredFvalues.vec(j).at(i) - meanUncenteredFVal[0][j]);
            }
        }
        return uncenteredFvalues;
    }
    
    public static float[][] matrixScalarDivision(float[][] M, float x) {
        float[][] result = new float[M.length][M[0].length];
        for (int i = 0; i < M.length; i++) {
            for (int j = 0; j < M[0].length; j++) {
                result[i][j] = M[i][j]/x;
            }
        }
        return result;
    }

    public static float[][] matrixMultiply(float[][] A, float[][] B) {
        int aRows = A.length;
        int aColumns = A[0].length;
        int bRows = B.length;
        int bColumns = B[0].length;
        
        if (aColumns != bRows) {
            throw new IllegalArgumentException("A:Rows: " + aColumns + " did not match B:Columns " + bRows + ".");
        }
        float[][] C = new float[aRows][bColumns];
        for (int i = 0; i < aRows; i++) {
            for (int j = 0; j < bColumns; j++) {
                C[i][j] = 0.00000f;
            }
        }
        for (int i = 0; i < aRows; i++) { // aRow
            for (int j = 0; j < bColumns; j++) { // bColumn
                for (int k = 0; k < aColumns; k++) { // aColumn
                    C[i][j] += A[i][k] * B[k][j];
                }
            }
        }
        return C;
    }
    
    
    static float[][] FrameTo2DArr(Frame frame, boolean transpose) {
        float[][] matrix;
        if (transpose)
            matrix  = new float[frame.numCols()][(int)frame.numRows()];
        else
            matrix  = new float[(int)frame.numRows()][frame.numCols()];
        for (int i = 0; i < frame.numRows(); i++) {
            for (int j = 0; j < frame.numCols(); j++) {
                if (transpose)
                    matrix[j][i] = (float) frame.vec(j).at(i);
                else
                    matrix[i][j] = (float) frame.vec(j).at(i);
            }
        }
        return matrix;
    }
    
    
    static Frame partialDependence(GBMModel model, Integer[] modelIds, Frame uniqueWithCounts) {
        double[] pdp;
        Frame result = new Frame();
        for (int i = 0; i < (model._parms)._ntrees; i++) {
            for (int treeClass = 0; treeClass < model._output.nclasses(); treeClass++) {
                SharedTreeSubgraph sharedTreeSubgraph = model.getSharedTreeSubgraph(i, treeClass);
                pdp = partialDependenceTree(sharedTreeSubgraph, modelIds, model._parms._learn_rate, uniqueWithCounts);
                result.add("pdp_T" + i +"C" + treeClass , Vec.makeVec(pdp, Key.make()));
            }
        }
        return result;
    }


    static Frame partialDependence(SharedTreeSubgraph tree, Integer[] modelIds, Frame uniqueWithCounts, double learnRate) {
        double[] pdp;
        Frame result = new Frame();
        SharedTreeSubgraph sharedTreeSubgraph = tree;
        pdp = partialDependenceTree(sharedTreeSubgraph, modelIds, learnRate, uniqueWithCounts);
        result.add("pdp_T" + 0 +"C" + 0 , Vec.makeVec(pdp, Vec.newKey()));
        return result;
    }

    
    static Frame filterFrame(Frame frame, String[] cols) {
        //return frame with those cols of frame which have names in cols
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

    
    
    public static double[] partialDependenceTree(SharedTreeSubgraph tree, Integer[] targetFeature, double learnRate, Frame grid) {
        //    For each row in ``X`` a tree traversal is performed.
        //    Each traversal starts from the root with weight 1.0.
        //
        //    At each non-terminal node that splits on a target variable either
        //    the left child or the right child is visited based on the feature
        //    value of the current sample and the weight is not modified.
        //    At each non-terminal node that splits on a complementary feature
        //    both children are visited and the weight is multiplied by the fraction
        //    of training samples which went to each child.
        //
        //    At each terminal node the value of the node is multiplied by the
        //    current weight (weights sum to 1 for all visited terminal nodes).
        
        //params:
        // tree = regression tree
        // target feature = the set of target features for which the partial dependence should be evaluated
        // learn rate = constant scaling factor for the leaf predictions
        // grid = the grid points on which the partial dependence should be evaluated
        
        // TODO: for now like this
        double[] out = new double[(int)grid.numRows()];
        
        int stackSize;
        SharedTreeNode[] nodeStackAr = new SharedTreeNode[tree.nodesArray.size() * 2];
        Double[] weightStackAr = new Double[tree.nodesArray.size() * 2];
        Arrays.fill(weightStackAr, 1.0);
        double totalWeight;
        SharedTreeNode currNode;
        double currWeight;
        
        for (int i = 0; i < grid.numRows(); i++) {
            stackSize = 1;
            nodeStackAr[0] = tree.rootNode;
            weightStackAr[0] = 1.0;
            totalWeight = 0.0;
            
            while (stackSize > 0) {
                // get top node on stack
                stackSize -= 1;
                currNode = nodeStackAr[stackSize];
                
                if (currNode.isLeaf()) {
                    out[i] += weightStackAr[stackSize] * currNode.getPredValue() * learnRate;
                    totalWeight += weightStackAr[stackSize];
                } else {
                    // non-terminal node:
                    int featureId = ArrayUtils.indexOf(targetFeature, currNode.getColId());
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
                        //push left
                        nodeStackAr[stackSize] = currNode.getLeftChild();
                        left_sample_frac = currNode.getLeftChild().getWeight() / currNode.getWeight();
                        weightStackAr[stackSize] = currWeight * left_sample_frac;
                        stackSize++;
                        //push right
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
        return out;
    }

}

