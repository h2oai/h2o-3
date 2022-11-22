package hex.modelselection;

import hex.DataInfo;
import hex.Model;
import hex.glm.GLM;
import hex.glm.GLMModel;
import hex.glm.GLMTask;
import jsr166y.ForkJoinTask;
import jsr166y.RecursiveAction;
import water.DKV;
import water.Key;
import water.fvec.Frame;
import water.fvec.Vec;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static hex.glm.GLMModel.GLMParameters.Family.gaussian;

public class ModelSelectionUtils {
    public static Frame[] generateTrainingFrames(ModelSelectionModel.ModelSelectionParameters parms, int predNum, String[] predNames,
                                                 int numModels, String foldColumn) {
        int maxPredNum = predNames.length;
        Frame[] trainFrames = new Frame[numModels];
        int[] predIndices = IntStream.range(0, predNum).toArray();   // contains indices to predictor names
        int zeroBound = maxPredNum-predNum;
        int[] bounds = IntStream.range(zeroBound, maxPredNum).toArray();   // highest combo value
        for (int frameCount = 0; frameCount < numModels; frameCount++) {    // generate one combo
            trainFrames[frameCount] = generateOneFrame(predIndices, parms, predNames, foldColumn);
            DKV.put(trainFrames[frameCount]);
            updatePredIndices(predIndices, bounds);
        }
        return trainFrames;
    }

    /***
     * Given predictor indices stored in currentPredIndices, we need to find the next combination of predictor indices
     * to use to generate the next combination.  For example, if we have 4 predictors and we are looking to take two 
     * predictors, predictor indices can change in the following sequence [0,1]->[0,2]->[0,3]->[1,2]->[1,2]->[2,3]. 
     *
     * @param currentPredIndices
     * @param indicesBounds
     */
    public static void updatePredIndices(int[] currentPredIndices, int[] indicesBounds) {
        int lastPredInd = currentPredIndices.length-1;
        for (int index = lastPredInd; index >= 0; index--) {
            if (currentPredIndices[index] < indicesBounds[index]) { // increase LSB first
                currentPredIndices[index]++;
                updateLaterIndices(currentPredIndices, index, lastPredInd);
                break;
            } 
        }
    }

    /***
     * Give 5 predictors and say we want the combo of 3 predictors, this function will properly reset the prediction
     * combination indices say from [0, 1, 4] -> [0, 2, 3] or [0, 3, 4] -> [1, 2, 3].  Given an index that was just
     * updated, it will update the indices that come later in the list correctly.
     * 
     * @param currentPredIndices
     * @param indexUpdated
     * @param lastPredInd
     */
    public static void updateLaterIndices(int[] currentPredIndices, int indexUpdated, int lastPredInd) {
        for (int index = indexUpdated; index < lastPredInd; index++) {
            currentPredIndices[index+1] = currentPredIndices[index]+1;
        }
    }
    
    /***
     *     Given a predictor indices set, this function will generate a training frame containing the predictors with
     *     indices in predIndices.
     *     
     * @param predIndices
     * @param parms
     * @param predNames
     * @return
     */
    public static Frame generateOneFrame(int[] predIndices, Model.Parameters parms, String[] predNames,
                                         String foldColumn) {
        final Frame predVecs = new Frame(Key.make());
        final Frame train = parms.train();
        int numPreds = predIndices.length;
        for (int index = 0; index < numPreds; index++) {
            int predVecNum = predIndices[index];
            predVecs.add(predNames[predVecNum], train.vec(predNames[predVecNum]));
        }
        if (parms._weights_column != null)
            predVecs.add(parms._weights_column, train.vec(parms._weights_column));
        if (parms._offset_column != null)
            predVecs.add(parms._offset_column, train.vec(parms._offset_column));
        if (foldColumn != null)
            predVecs.add(foldColumn, train.vec(foldColumn));
        predVecs.add(parms._response_column, train.vec(parms._response_column));
        return predVecs;
    }
    
    public static void setBitSet(BitSet predBitSet, int[] currIndices) {
        for (int predIndex : currIndices)
            predBitSet.set(predIndex);
    }
    
    public static int[][] mapPredIndex2CPMIndices(DataInfo dinfo, int predLength) {
        int numPreds = predLength;
        int[][] pred2CPMMapping = new int[numPreds][];
        int offset = 0;
        
        for (int index=0; index < dinfo._cats; index++) {  // take care of categorical columns
            int numLevels = dinfo._catOffsets[index+1]-dinfo._catOffsets[index];    // number of catLevels
            pred2CPMMapping[index] = IntStream.iterate(offset, n->n+1).limit(numLevels).toArray();
            offset += numLevels;
        }
        for (int index=0; index < dinfo._nums; index++) {
            pred2CPMMapping[index+dinfo._cats] = new int[]{dinfo._numOffsets[index]};
        }
        return pred2CPMMapping;
    }
            
    public static double[][] createCrossProductMatrix(Key jobKey, DataInfo dinfo) {
        double[] beta = new double[dinfo.coefNames().length];
        beta = Arrays.stream(beta).map(x -> 1.0).toArray(); // set coefficient to all 1
        GLMTask.GLMIterationTask gtask = new GLMTask.GLMIterationTask(jobKey, dinfo, new GLMModel.GLMWeightsFun(gaussian,
                GLMModel.GLMParameters.Link.identity, 1, 0.1, 0.1), beta).doAll(dinfo._adaptedFrame);
        double[][] xTransposex = gtask.getGram().getXX();
        double[] xTransposey = gtask.getXY();
        int cPMsize = xTransposey.length+1;
        int coeffSize = xTransposey.length;
        double[][] crossProductMatrix = new double[cPMsize][cPMsize];
        // copy xZTransposex, xTransposey, yy to crossProductMatrix
        for (int rowIndex=0; rowIndex<coeffSize; rowIndex++) {
            System.arraycopy(xTransposex[rowIndex], 0, crossProductMatrix[rowIndex], 0, coeffSize);
            crossProductMatrix[rowIndex][coeffSize] = xTransposey[rowIndex];
        }
        System.arraycopy(xTransposey, 0, crossProductMatrix[coeffSize], 0, coeffSize);
        crossProductMatrix[coeffSize][coeffSize] = gtask.getYY();
        return crossProductMatrix;
    }

    public static double calR2Scale(Frame train, String resp) {
        Vec respV = train.vec(resp);
        double sigma = respV.sigma();
        double var = sigma*sigma;
        long nobs = train.numRows()-respV.naCnt()-1;
        return nobs*var;
    }
    
    static class CoeffNormalization {
        double[] _sigmaOrOneOSigma; // only for the numerical predictors
        double[] _meanOverSigma;
        boolean _standardize;
        
        public CoeffNormalization(double[] oOSigma, double[] mOSigma, boolean standardize) {
            _sigmaOrOneOSigma = oOSigma;
           _meanOverSigma = mOSigma;
           _standardize = standardize;
        }
    }

    static CoeffNormalization generateScale(DataInfo dinfo, boolean standardize) {
        int numCols = dinfo._nums;
        double[] sigmaOrOneOverSigma = new double[numCols];
        double[] mOverSigma = new double[numCols];
        for (int index = 0; index < numCols; index++) {
            if (standardize) {
                sigmaOrOneOverSigma[index] = dinfo._normMul[index]; // 1/sigma
                mOverSigma[index] = dinfo._numMeans[index] * dinfo._normMul[index];
            } else {
                sigmaOrOneOverSigma[index] = dinfo._normSigmaStandardizationOff[index]; // sigma
                mOverSigma[index] = dinfo._numMeans[index] / dinfo._normSigmaStandardizationOff[index];
            }
        }
        return new CoeffNormalization(sigmaOrOneOverSigma, mOverSigma, standardize);
    }
    
    /**double
     * @param predictorNames
     * @param foldColumn
     * @param currSubsetIndices
     * @param validSubsets Lists containing only valid predictor indices to choose from
     * @return
     */
    public static Frame[] generateMaxRTrainingFrames(ModelSelectionModel.ModelSelectionParameters parms,
                                                     String[] predictorNames, String foldColumn,
                                                     List<Integer> currSubsetIndices, int newPredPos,
                                                     List<Integer> validSubsets, Set<BitSet> usedCombo) {
        List<Frame> trainFramesList = new ArrayList<>();
        List<Integer> changedSubset = new ArrayList<>(currSubsetIndices);
        changedSubset.add(newPredPos, -1);  // value irrelevant
        int[] predIndices = changedSubset.stream().mapToInt(Integer::intValue).toArray();
        int predNum = predictorNames.length;
        BitSet tempIndices =  new BitSet(predNum);
        int predSizes = changedSubset.size();
        boolean emptyUsedCombo = (usedCombo != null) && (usedCombo.size() == 0);
        for (int predIndex : validSubsets) {  // consider valid predictor indices only
            predIndices[newPredPos] = predIndex;
            if (emptyUsedCombo && predSizes > 1) {   // add all indices set into usedCombo
                tempIndices.clear();
                setBitSet(tempIndices, predIndices);
                usedCombo.add((BitSet) tempIndices.clone());
                Frame trainFrame = generateOneFrame(predIndices, parms, predictorNames, foldColumn);
                DKV.put(trainFrame);
                trainFramesList.add(trainFrame);
                
            } else if (usedCombo != null && predSizes > 1) {   // only need to check for forward and replacement step for maxR
                tempIndices.clear();
                setBitSet(tempIndices, predIndices);
                if (usedCombo.add((BitSet) tempIndices.clone())) {  // returns true if not in keyset
                    Frame trainFrame = generateOneFrame(predIndices, parms, predictorNames, foldColumn);
                    DKV.put(trainFrame);
                    trainFramesList.add(trainFrame);
                }
            } else {     // just build without checking duplicates for other modes
                Frame trainFrame = generateOneFrame(predIndices, parms, predictorNames, foldColumn);
                DKV.put(trainFrame);
                trainFramesList.add(trainFrame);
            }
        }
        return trainFramesList.stream().toArray(Frame[]::new);
    }

    /***
     * Given the prevCPM which contains the CPM generated from the last forward step search, we will add a new predictor
     * from the predictors in validSubsets and calculate its error variances defined in section IV of doc.
     * 
     * If we are at the first step of forward search, prevCPM will be null.  In this case, we just loop through the
     * validSubsets to pick a predictor, generate the CPM and perform sweeping to generate an error variances.
     * 
     * The list of error variances will be returned.  See section V.I of doc.
     * 
     * for maxrsweep2
     */
    public static double[] generateAllErrorVariances(final double[][] allCPM, final SweepVector[][] sweepVec, double[][] prevCPM,
                                                     List<Integer> currSubsetIndices,
                                                     List<Integer> validSubsets, Set<BitSet> usedCombo, BitSet tempIndices,
                                                     final int[][] pred2CPMIndices, final boolean hasIntercept, 
                                                     int predPos, int removedPred, int[] sweepIndices) {
        int[] allPreds = new int[currSubsetIndices.size() + 1];   // store the bigger predictor subset
        int lastPredInd = allPreds.length - 1;

        if (currSubsetIndices.size() > 0)   // copy over last best predictor subset with smaller subset size
            System.arraycopy(currSubsetIndices.stream().mapToInt(Integer::intValue).toArray(), 0, allPreds,
                    0, allPreds.length - 1);
        int predSizes = allPreds.length;
        int maxModelCount = validSubsets.size();

        RecursiveAction[] resA = new RecursiveAction[maxModelCount];
        final double[] subsetMSE = Arrays.stream(new double[maxModelCount]).map(x -> Double.MAX_VALUE).toArray();
        int modelCount = 0;
        for (int predIndex : validSubsets) {  // consider valid predictor indices only
            allPreds[lastPredInd] = predIndex;
            if (predSizes > 1) {
                tempIndices.clear();
                setBitSet(tempIndices, allPreds);
                if (usedCombo.add((BitSet) tempIndices.clone())) {
                    final int resCount = modelCount++;
                    genMSE4MorePreds(pred2CPMIndices, allCPM, sweepVec, allPreds, prevCPM, subsetMSE, resA, resCount, 
                            hasIntercept, predPos, removedPred, sweepIndices);
                }
            } else {    // start from first predictor
                final int resCount = modelCount++;
                genMSE1stPred(pred2CPMIndices, allCPM, allPreds, subsetMSE, resA, resCount, hasIntercept);
            }
        }
        ForkJoinTask.invokeAll(Arrays.stream(resA).filter(Objects::nonNull).toArray(RecursiveAction[]::new));
        return subsetMSE;
    }

    public static void genMSE4MorePreds(final int[][] pred2CPMIndices, final double[][] allCPM, SweepVector[][] sweepVec,
                                        final int[] allPreds, double[][] prevCPM, final double[] subsetMSE, RecursiveAction[] resA,
                                        final int resCount, final boolean hasIntercept, int predPos, int removedPred, int [] sweepIndices) {
        List<Integer> predList = null;
        if (predPos >= 0) {
            predList = IntStream.of(allPreds).boxed().collect(Collectors.toList());
            predList.add(predPos, removedPred);
        }
        final int[] subsetIndices = predPos >= 0 ? predList.stream().mapToInt(x->x).toArray() : allPreds.clone();
        resA[resCount] = new RecursiveAction() {
            @Override
            protected void compute() {
                int lastSweepIndex = prevCPM.length - 1;
                double[][] subsetCPM = addNewPred2CPM(allCPM, prevCPM, subsetIndices, pred2CPMIndices,
                        hasIntercept);  // generate CPM in equation 4 of doc
                int lastPredInd = subsetIndices[subsetIndices.length - 1];
                int newPredCPMLength = pred2CPMIndices[lastPredInd].length;
                if (newPredCPMLength == 1) { // perform sweeping with sweep vector when one new row/column is added
                    applySweepVectors2NewPred(sweepVec, subsetCPM, newPredCPMLength, sweepIndices);
                } else {    // when multiple rows/columns are added due to new enum column
                    SweepVector[][] newSweepVec = mapBasicVector2Multiple(sweepVec, newPredCPMLength);
                    applySweepVectors2NewPred(newSweepVec, subsetCPM, newPredCPMLength, sweepIndices);
                }
                // apply new sweeps to CPM due to addition of the new rows/columns
                List<Integer> sweepIndices = IntStream.range(0, newPredCPMLength).map(x -> x + lastSweepIndex).boxed().collect(Collectors.toList());
                // only apply the sweepIndices to only the last element of the CPM
                subsetMSE[resCount] = sweepMSE(subsetCPM, sweepIndices);
            }
        };
    }
    
    public static double sweepMSE(double[][] subsetCPM, List<Integer> sweepIndices) {
        int sweepLen = sweepIndices.size();
        int cpmLen = subsetCPM.length;
        int lastInd = cpmLen-1;
        if (sweepLen == 1) {    // quick stop for one sweep only
            int sweepInd = sweepIndices.get(0);
            return subsetCPM[lastInd][lastInd]
                    -subsetCPM[lastInd][sweepInd]*subsetCPM[sweepInd][lastInd]/subsetCPM[sweepInd][sweepInd];
        } 
        Set<SweepElement>[] sweepElements = new Set[sweepLen];
        List<SweepElement> tempElements = new ArrayList<>();
        tempElements.add(new SweepElement(lastInd, lastInd, new ArrayList<>(sweepIndices)));
        while (tempElements.size() > 0) {
            SweepElement oneEle = tempElements.remove(0);
            if (oneEle._sweepIndices.size() == 1) {
                if (sweepElements[0] == null)
                    sweepElements[0] = new HashSet<>();
                sweepElements[0].add(oneEle);
            } else { // sweepIndices size > 1
                int arrIndex = oneEle._sweepIndices.size() - 1;
                if (sweepElements[arrIndex] == null)
                    sweepElements[arrIndex] = new HashSet<>();
                sweepElements[arrIndex].add(oneEle);
                process(oneEle, tempElements);
            }
        }
        sweepCPMElements(sweepElements, subsetCPM);
        return subsetCPM[lastInd][lastInd];
    }

    public static void sweepCPMElements(Set<SweepElement>[] sweepElements, double[][] subsetCPM) {
        int numSweeps = sweepElements.length;
        int row, col, oneIndex;
        for (int index = 0; index < numSweeps; index++) {
            Set<SweepElement> oneSweepAction = sweepElements[index];
            for (SweepElement oneElement : oneSweepAction) {
                oneIndex = oneElement._sweepIndices.get(oneElement._sweepIndices.size() - 1);
                row = oneElement._row;
                col = oneElement._col;
                subsetCPM[row][col] = subsetCPM[row][col] -
                        subsetCPM[row][oneIndex] * subsetCPM[oneIndex][col] / subsetCPM[oneIndex][oneIndex];
            }
        }
    }

    /***
     * This method will generate all the elements that are needed to perform sweeping on the currEle.  The
     * formula for sweeping is:
     *  subsetCPM[row][col] = subsetCPM[row][col]-subsetCPM[row][sweepInd]*subsetCPM[sweepInd][row]/subsetCPM[sweepInd][sweepInd]
     *  
     *  We are not performing the actual sweeping here but rather to remember the elements of subsetCPM that we need
     *  to perform sweeping on.  From the formula, each currEle will generate three more new SweepElements
     */
    public static void process(SweepElement currEle, List<SweepElement> tempList) {
        List<Integer> newSweepIndices = new ArrayList<>(currEle._sweepIndices);
        int sweepIndex = newSweepIndices.remove(newSweepIndices.size()-1);  // remove last sweepIndices
        tempList.add(new SweepElement(currEle._row, currEle._col, newSweepIndices));
        tempList.add(new SweepElement(currEle._row, sweepIndex, newSweepIndices));
        tempList.add(new SweepElement(sweepIndex, currEle._col, newSweepIndices));
        tempList.add(new SweepElement(sweepIndex, sweepIndex, newSweepIndices));
    }
    
    static class SweepElement {
        final int _row;
        final int _col;
        final List<Integer> _sweepIndices;
        
        public SweepElement(int row, int col, List<Integer> sweepInd) {
            _row = row;
            _col = col;
            _sweepIndices = sweepInd;
        }
        
        @Override
        public boolean equals(Object o) {
            if (o instanceof SweepElement) {
                if (_row == ((SweepElement) o)._row && _col == ((SweepElement) o)._col) {
                    if (_sweepIndices.equals(((SweepElement) o)._sweepIndices))
                        return true;
                }
            }
            return false;
        }

        @Override
        public int hashCode() {
            return _row+(_col+1)*10+_sweepIndices.hashCode();
        }
    }
    public static void genMSE1stPred(final int[][] pred2CPMIndices, final double[][] allCPM, final int[] allPreds, 
                                     final double[] subsetMSE, RecursiveAction[] resA,
                                     final int resCount, final boolean hasIntercept) {
        final int[] subsetIndices = allPreds.clone();
        resA[resCount] = new RecursiveAction() {
            @Override
            protected void compute() {
                // generate CPM corresponding to the subset indices in subsetIndices
                double[][] subsetCPM = extractPredSubsetsCPM(allCPM, subsetIndices, pred2CPMIndices, hasIntercept);
                int lastSubsetIndex = subsetCPM.length-1;
                // perform sweeping action and record the sweeping vector and save the changed cpm
                subsetMSE[resCount] = sweepMSE(subsetCPM, IntStream.range(0, lastSubsetIndex).boxed().collect(Collectors.toList()));
            }
        };
    }

    /***
     * When multiple rows/columns are added to the CPM due to the new predictor being categorical, we need to map the 
     * old sweep vector arrays to new bigger sweep vector arrays.  See section V.II.V of doc.
     */
    public static SweepVector[][] mapBasicVector2Multiple(SweepVector[][] sweepVec, int newPredCPMLen) {
        int numSweep = sweepVec.length;
        int oldColLen = sweepVec[0].length/2;
        int newColLen = oldColLen+newPredCPMLen-1;  // sweepVector from old CPM was calculated when one new row/col is added
        int lastNewColInd = newColLen-1;
        int lastOldColInd = oldColLen-1;
        SweepVector[][] newSweepVec = new SweepVector[numSweep][newColLen*2];
        for (int sInd = 0; sInd < numSweep; sInd++) {
            double oneOverPivot = sweepVec[sInd][lastOldColInd-1]._value;
            int rowColInd = sweepVec[sInd][0]._column;
            for (int vInd = 0; vInd < lastNewColInd; vInd++) {
                if (vInd==sInd || vInd < lastOldColInd) { // index within old sweep vector range
                    newSweepVec[sInd][vInd] = new SweepVector(vInd, rowColInd, sweepVec[sInd][vInd]._value);
                    newSweepVec[sInd][vInd+newColLen] = new SweepVector(rowColInd, vInd,
                            sweepVec[sInd][vInd+oldColLen]._value);
                } else if (vInd == lastOldColInd) {  // last sweep index
                    newSweepVec[sInd][lastNewColInd] = new SweepVector(lastNewColInd, rowColInd,
                            sweepVec[sInd][lastOldColInd]._value);
                    newSweepVec[sInd][lastNewColInd + newColLen] = new SweepVector(rowColInd, lastNewColInd,
                            sweepVec[sInd][lastOldColInd + oldColLen]._value);
                    newSweepVec[sInd][vInd] = new SweepVector(vInd, rowColInd, oneOverPivot);
                    newSweepVec[sInd][vInd+newColLen] = new SweepVector(rowColInd, vInd, oneOverPivot);
                } else {    // new sweep vector index exceed old sweep vector index
                    newSweepVec[sInd][vInd] = new SweepVector(vInd, rowColInd, oneOverPivot);
                    newSweepVec[sInd][vInd+newColLen] = new SweepVector(rowColInd, vInd, oneOverPivot);
                }
            }
        }
        return newSweepVec;
    }

    /***
     * This method will sweep the rows/columns added to the CPM due to the addition of the new predictor using sweep
     * vector arrays.  See Step 3 of section V.II.IV of doc.  The sweep vectors should contain sweeping for predictor
     * 0, for predictor 2, .... predictor s of the predictor subset.
     */
    public static void applySweepVectors2NewPred(SweepVector[][] sweepVec, double[][] subsetCPM, int numNewRows, 
                                                 int[] sweepMat) {
        int numSweep = sweepVec.length; // number of sweeps that we need to do
        int sweepVecLen = sweepVec[0].length;
        int[][] elementAccessMatrix = new int[sweepVecLen][sweepVecLen];
        if (sweepMat == null) {
            for (int sweepInd=0; sweepInd < numSweep; sweepInd++) {
                zeroFill2DArrays(elementAccessMatrix);
                oneSweepWSweepVector(sweepVec[sweepInd], subsetCPM, sweepInd, numNewRows, elementAccessMatrix);
            }
        } else {
            int sweepInd;
            for (int index = 0; index < numSweep; index++) {
                sweepInd = sweepMat[index];
                zeroFill2DArrays(elementAccessMatrix);
                oneSweepWSweepVector(sweepVec[index], subsetCPM, sweepInd, numNewRows, elementAccessMatrix);
            }
        }
    }
    
    public static void zeroFill2DArrays(int[][] elementArray) {
        int width = elementArray.length;
        for (int index=0; index<width; index++)
            Arrays.fill(elementArray[index], 0);
    }

    /***
     * This method perform just one sweep of the sweeping action described in Step 3 of section V.II.IV of doc.
     * Note that for a sweep vector array of 2*(N+1), the first N+1 elements describe the changes to the new column.
     * The last N+1 elements describing changes to the new row.  The sweep vector arrays will contain changes to 
     * the same element multiple times.  I only change each new element once by using elementAccessCount to keep track 
     * of which elements have been changed already.  See Step 3 of section V.II.IV of doc for details.
     * 
     * In addition, I did not use two arrays to implement the changes.  Hence, the order of change is important here.
     * I used temporary variable to store element changes that are used by other element updates.  I copied the 
     * temporary elements back to the CPM at the end.
     */
    public static void oneSweepWSweepVector(SweepVector[] sweepVec, double[][] subsetCPM, int sweepIndex,
                                            int colRowsAdded, int[][] elementAccessCount) {
        int sweepVecLen = sweepVec.length / 2;
        int newLastCPMInd = sweepVecLen - 1;
        int oldSweepVec = sweepVecLen - colRowsAdded;
        int oldLastCPMInd = oldSweepVec - 1;    // sweeping index before adding new rows/columns
        double[] colSweeps = new double[colRowsAdded];
        double[] rowSweeps = new double[colRowsAdded];

        for (int rcInd = 0; rcInd < colRowsAdded; rcInd++) {   // for each newly added row/column
            int rowColInd = sweepVec[0]._column + rcInd;
            for (int svInd = 0; svInd < sweepVecLen; svInd++) { // working on each additional row/col
                int svIndOffset = svInd + sweepVecLen;
                if (sweepVec[svInd]._row == sweepIndex) {  // take care of both row and column elements
                    if (elementAccessCount[sweepIndex][rowColInd] == 0) {
                        rowSweeps[rcInd] = sweepVec[svInd]._value * subsetCPM[sweepIndex][rowColInd];
                        elementAccessCount[sweepIndex][rowColInd] = 1;
                    }
                    if (elementAccessCount[rowColInd][sweepIndex]==0) {
                        colSweeps[rcInd] = sweepVec[svIndOffset]._value * subsetCPM[rowColInd][sweepIndex];
                        elementAccessCount[rowColInd][sweepIndex] = 1;
                    }
                } else if (sweepVec[svInd]._row == newLastCPMInd) {
                    if (elementAccessCount[newLastCPMInd][rowColInd] == 0) {
                        subsetCPM[newLastCPMInd][rowColInd] = subsetCPM[newLastCPMInd][rowColInd] -
                                sweepVec[svInd]._value * subsetCPM[sweepIndex][rowColInd];
                        elementAccessCount[newLastCPMInd][rowColInd] = 1;
                    }
                    if (elementAccessCount[rowColInd][newLastCPMInd]==0) {
                        subsetCPM[rowColInd][newLastCPMInd] = subsetCPM[rowColInd][newLastCPMInd] -
                                sweepVec[svIndOffset]._value * subsetCPM[rowColInd][sweepIndex];
                        elementAccessCount[rowColInd][newLastCPMInd] = 1;
                    }
                } else if (sweepVec[svInd]._row == rowColInd) {
                    if (elementAccessCount[rowColInd][rowColInd] == 0) {
                        subsetCPM[rowColInd][rowColInd] = subsetCPM[rowColInd][rowColInd] -
                                subsetCPM[rowColInd][sweepIndex] * subsetCPM[sweepIndex][rowColInd] * sweepVec[svInd]._value;
                        elementAccessCount[rowColInd][rowColInd] = 1;
                    }
                } else if (sweepVec[svInd]._row < oldLastCPMInd) {
                    if (elementAccessCount[sweepVec[svInd]._row][rowColInd] == 0) {
                        subsetCPM[sweepVec[svInd]._row][rowColInd] = subsetCPM[sweepVec[svInd]._row][rowColInd] -
                                subsetCPM[sweepIndex][rowColInd] * sweepVec[svInd]._value;
                        elementAccessCount[sweepVec[svInd]._row][rowColInd] = 1;
                    }
                    if (elementAccessCount[rowColInd][sweepVec[svIndOffset]._column]==0) {
                        subsetCPM[rowColInd][sweepVec[svIndOffset]._column] =
                                subsetCPM[rowColInd][sweepVec[svIndOffset]._column] - subsetCPM[rowColInd][sweepIndex] *
                                        sweepVec[svIndOffset]._value;
                        elementAccessCount[rowColInd][sweepVec[svIndOffset]._column] = 1;
                    }
                } else { // considering rows/columns >= oldSweepVec
                    if (elementAccessCount[sweepVec[svInd]._row][rowColInd] == 0) {
                        subsetCPM[sweepVec[svInd]._row][rowColInd] = subsetCPM[sweepVec[svInd]._row][rowColInd] -
                                subsetCPM[sweepVec[svInd]._row][sweepIndex] * subsetCPM[sweepIndex][rowColInd] * sweepVec[svInd]._value;
                        elementAccessCount[sweepVec[svInd]._row][rowColInd] = 1;
                    }
                    if (elementAccessCount[rowColInd][sweepVec[svIndOffset]._column]==0) {
                        subsetCPM[rowColInd][sweepVec[svIndOffset]._column] = subsetCPM[rowColInd][sweepVec[svIndOffset]._column]
                                - subsetCPM[rowColInd][sweepIndex] * subsetCPM[sweepIndex][sweepVec[svIndOffset]._column] * sweepVec[svIndOffset]._value;

                        elementAccessCount[rowColInd][sweepVec[svIndOffset]._column] = 1;
                    }
                }
            }
        }
        // take care of updating elements that are not updated
        for (int rcInd = 0; rcInd < colRowsAdded; rcInd++) {
            int rowColInd = sweepVec[0]._column + rcInd;
            subsetCPM[sweepIndex][rowColInd] = rowSweeps[rcInd];
            subsetCPM[rowColInd][sweepIndex] = colSweeps[rcInd];
        }
    }

    /**
     * Given current CPM which has been swept already, we need to add the lastest predictor to the current CPM that have
     * not been swept. The new elements belonging to the newest predictor is extracted from the original allCPM.
     * 
     * Basically, I just extract the CPM from the original non-swept CPM that contains rows/columns of CPM 
     * corresponding to the predictors in subsetPredIndex.  Next, I copied over the swept rows/columns of CPM 
     * corresponding to the previous predictor subset while leaving the rows/columns corresponding to the new rows/
     * columns due to the new predictor unchanged.
     * 
     * See Step 2 in section V.II.IV of doc.
     */
    public static double[][] addNewPred2CPM(double[][] allCPM, double[][] currentCPM, int[] subsetPredIndex,
                                            int[][] pred2CPMIndices, boolean hasIntercept) {
        double[][] newCPM = extractPredSubsetsCPM(allCPM, subsetPredIndex, pred2CPMIndices, hasIntercept);
        int oldCPMDim = currentCPM.length-1;    // XTX dimension
        int newCPMDim = newCPM.length;
        int lastnewCPMInd = newCPMDim-1;
        for (int index=0; index<oldCPMDim; index++) {   // copy over the swept CPM elements of smaller predictor subset
                System.arraycopy(currentCPM[index], 0, newCPM[index], 0, oldCPMDim);// copy over old cpm
                newCPM[index][lastnewCPMInd] = currentCPM[index][oldCPMDim];    // copy over the last column of CPM
        }
        // correct last row of newCPM to be part of last row of currentCPM
        System.arraycopy(currentCPM[oldCPMDim], 0, newCPM[lastnewCPMInd], 0, oldCPMDim);
        newCPM[lastnewCPMInd][lastnewCPMInd] = currentCPM[oldCPMDim][oldCPMDim];    // copy over corner element
        
        return newCPM;
    }

    /***
     * Given the predictor subset stored in subsetPred, the newest predictor index is the last element of subsetPred.
     * The subsetCPM contains the swept rows/cols corresponding to the predictors stored in subsetPred except the last
     * predictor.  Here, we need to update the sweep vectors for the sweeping of a new predictor in the future.
     */
    public static SweepVector[][] updateSweepVectors(double[][] subsetCPM, SweepVector[][] sweepVector, 
                                                     int[] subsetPred, int[][] predInd2CPMInd, boolean hasIntercept) {
        int subsetSize = subsetCPM.length;
        int newPred = subsetPred[subsetPred.length-1];
        int numSV = sweepVector.length;
        int newNumSweeps = predInd2CPMInd[newPred].length;
        SweepVector[][] currSV = newNumSweeps > 1 ? mapBasicVector2Multiple(sweepVector, newNumSweeps) : sweepVector;
        SweepVector[][] newSweepVector = new SweepVector[subsetSize-1][];
        int sweepVecLen = currSV[0].length;
        int[][] elementAccessMatrix = new int[sweepVecLen][sweepVecLen];
        for (int sweepInd=0; sweepInd < numSV; sweepInd++) {
            // copy over sweepVector to newSV
            newSweepVector[sweepInd] = genNewSV(currSV[sweepInd], subsetCPM, newNumSweeps, sweepInd);
            // perform sweeping of sweepInd using sweep vectors
            zeroFill2DArrays(elementAccessMatrix);
            oneSweepWSweepVector(currSV[sweepInd], subsetCPM, sweepInd, newNumSweeps, elementAccessMatrix);
        }
        // generate new sweep vectors associated with newly added predictor and copy it over to newSV
        SweepVector[][] newPredSV =  sweepCPM(subsetCPM, IntStream.range(0, newNumSweeps).map(x -> x+numSV).toArray(), true);
        for (int index=0; index < newNumSweeps; index++)
            newSweepVector[index+numSV] = newPredSV[index];
        return newSweepVector;
    }
    
    public static int[] extractSweepIndices(List<Integer> currSubsetIndices, int predPos, int predRemoved, 
                                     int[][] predInd2CPMIndices, boolean hasIntercept) {
        int predRemovedLen = predInd2CPMIndices[predRemoved].length;
        int totalSize = IntStream.range(0, predPos).map(x->predInd2CPMIndices[currSubsetIndices.get(x)].length).sum()
                + (hasIntercept ? 1 : 0);
        return IntStream.range(0, predRemovedLen).map(x -> x+totalSize).toArray();
    }
    
    public static SweepVector[][] mergeSV(SweepVector[][] sweepVector, SweepVector[][] removedPredSV) {
        int oldSVLen = sweepVector.length;
        int svLen = oldSVLen + removedPredSV.length;
        SweepVector[][] mergedSV = new SweepVector[svLen][];
        for (int index=0; index<oldSVLen; index++)
            mergedSV[index] = sweepVector[index];
        for (int index=oldSVLen; index<svLen; index++) 
            mergedSV[index] = removedPredSV[index-oldSVLen];
        return mergedSV;
    }
    
    public static SweepVector[] genNewSV(SweepVector[] currSV, double[][] subsetCPM, int newPredLen, int sweepInd) {
        int halfOldSVLen = currSV.length/2;
        int oldSVInd = halfOldSVLen-2;
        int halfSVLen = halfOldSVLen+newPredLen;
        int lastSVInd = halfSVLen-2;
        int lastCPMInd = subsetCPM.length-1;
        int lastOldSVInd = halfOldSVLen-1;
        SweepVector[] newSV = new SweepVector[halfSVLen*2];
        double oneOverPivot = currSV[sweepInd]._value;
        for (int index=0; index<halfSVLen; index++) {
            if (index < oldSVInd && index != sweepInd) { // copy over from currSV
                newSV[index] = new SweepVector(index, lastSVInd, currSV[index]._value);
                newSV[index + halfSVLen] = new SweepVector(lastSVInd, index, currSV[index + halfOldSVLen]._value);
            } else if (index == sweepInd) { // element corresponding to sweepIndex
                newSV[index] = new SweepVector(index, lastSVInd, oneOverPivot);
                newSV[index + halfSVLen] = new SweepVector(lastSVInd, index, -oneOverPivot);
            } else if (index >= oldSVInd) { // exceed old SV 
                if (index == lastSVInd) {    // row index == col index
                    newSV[index] = new SweepVector(index, index, oneOverPivot);
                    newSV[index+halfSVLen] = newSV[index];
                } else if (index <= lastCPMInd){
                    newSV[index] = new SweepVector(index, lastSVInd, subsetCPM[index][sweepInd]*oneOverPivot);
                    newSV[index+halfSVLen] = new SweepVector(lastSVInd, index, subsetCPM[sweepInd][index]*oneOverPivot);
                } else {
                    newSV[index] = new SweepVector(index, lastCPMInd, currSV[lastOldSVInd]._value);
                    newSV[index+halfSVLen] = new SweepVector(lastCPMInd, index, currSV[lastOldSVInd+halfOldSVLen]._value);
                }
            }
        }
        return newSV;
    }
    
    public static List<Integer> extractCPMIndexFromPred(double[][] allCPM, int[][] pred2CPMIndices, int[] newPredList, 
                                                        boolean hasIntercept) {
        List<Integer> CPMIndices = new ArrayList<>();
        for (int predInd : newPredList) {
            CPMIndices.addAll(Arrays.stream(pred2CPMIndices[predInd]).boxed().collect(Collectors.toList()));
        }
        if (hasIntercept)
            CPMIndices.add(0, allCPM.length-2);;
        CPMIndices.add(allCPM.length-1);
        return CPMIndices;
    }

    /***
     * Given the predictor subset in bestModel, this method will perform sweeping on the predictor subset, generate
     * new sweep vector arrays and stored the new swept CPM back to bestModel.  Note that the Sweep Vectors generated
     * are from predictor 0, predictor 1, ... , predictor s in the predictor subset.
     */
    public static void genBestSweepVector(ModelSelection.SweepModel bestModel, double[][] cpm,
                                          int[][] pred2CPMIndices, boolean hasIntercept){
        double[][] subsetCPM = extractPredSubsetsCPM(cpm, bestModel._predSubset, pred2CPMIndices, hasIntercept);
        // perform sweeping action and record the sweeping vector and save the changed cpm
        bestModel._sweepVector = sweepCPM(subsetCPM, IntStream.range(0, subsetCPM.length-1).toArray(), true);
        bestModel._CPM = subsetCPM;
        int lastInd = subsetCPM.length-1;
        bestModel._errorVariance = subsetCPM[lastInd][lastInd];
    }

    /***
     * This method perform the sweeping action described in section II of doc.  In addition, if genSweepVector is set
     * to true, it will also generate the corresponding sweep vector arrays described in section V.II of doc.
     */
    public static SweepVector[][] sweepCPM(double[][] subsetCPM, int[] sweepIndices, boolean genSweepVector) {
        int currSubsetCPMSize = subsetCPM.length;
        int numSweep = sweepIndices.length;
        SweepVector[][] sweepVecs = new SweepVector[numSweep][2*(currSubsetCPMSize+1)];
        for (int index=0; index < numSweep; index++) 
            performOneSweep(subsetCPM, sweepVecs[index], sweepIndices[index], genSweepVector);
        return sweepVecs;
    }

    /**
     * store information on sweeping actions that are to be performed to new rows/columns added to CPM due to the 
     * addition of new predcitors.
     */
    public static class SweepVector {
        int _row;
        int _column;
        double _value;
        public SweepVector(int rIndex, int cIndex, double val) {
            _row = rIndex;
            _column = cIndex;
            _value = val;
        }
    }

    /***
     * Perform one sweep according to section II of doc and generate sweep vector according to section V.II of doc.
     */
    public static void performOneSweep(double[][] subsetCPM, SweepVector[] sweepVec, int sweepIndex,
                                                boolean genSweepVector) {
        int subsetCPMLen = subsetCPM.length;
        int lastSubsetInd = subsetCPMLen-1;
        if (subsetCPM[sweepIndex][sweepIndex]==0) { // pivot is zero, set error variance to max value
            subsetCPM[lastSubsetInd][lastSubsetInd] = Double.MAX_VALUE;
            return;
        } else {    // subsetCPM is healthy
            double oneOverPivot = 1.0/subsetCPM[sweepIndex][sweepIndex];
            // generate sweep vector as in section V.II of doc
            if (genSweepVector) {
                int sweepVecLen = sweepVec.length / 2;
                for (int index = 0; index < sweepVecLen; index++) {
                    if (index == sweepIndex) {
                        sweepVec[index] = new SweepVector(index, lastSubsetInd, oneOverPivot);
                        sweepVec[index + sweepVecLen] = new SweepVector(lastSubsetInd, index, -oneOverPivot);
                    } else if (index == subsetCPMLen) {
                        sweepVec[index] = new SweepVector(index, lastSubsetInd, subsetCPM[lastSubsetInd][sweepIndex] * oneOverPivot);
                        sweepVec[index + sweepVecLen] = new SweepVector(lastSubsetInd, index, subsetCPM[sweepIndex][lastSubsetInd] * oneOverPivot);
                    } else if (index==lastSubsetInd) {
                            sweepVec[index] = new SweepVector(index, lastSubsetInd, oneOverPivot);
                            sweepVec[index+sweepVecLen] = new SweepVector(lastSubsetInd, index, oneOverPivot);
                    } else {
                        sweepVec[index] = new SweepVector(index, lastSubsetInd, subsetCPM[index][sweepIndex] * oneOverPivot);
                        sweepVec[index + sweepVecLen] = new SweepVector(lastSubsetInd, index, subsetCPM[sweepIndex][index] * oneOverPivot);
                    }
                }
            }
            // perform sweeping action as in section II of doc.
            for (int rInd = 0; rInd < subsetCPMLen; rInd++) {
                for (int cInd = rInd; cInd < subsetCPMLen; cInd++) {
                    if (rInd != sweepIndex && cInd != sweepIndex) {
                        subsetCPM[rInd][cInd] = subsetCPM[rInd][cInd]-
                                subsetCPM[rInd][sweepIndex]*subsetCPM[sweepIndex][cInd]*oneOverPivot;
                        if (cInd != rInd)
                            subsetCPM[cInd][rInd] = subsetCPM[cInd][rInd]-
                                    subsetCPM[cInd][sweepIndex]*subsetCPM[sweepIndex][rInd]*oneOverPivot;
                    }
                }
            }
            for (int index=0; index < subsetCPMLen; index++) {
                subsetCPM[index][sweepIndex] = -subsetCPM[index][sweepIndex]*oneOverPivot;
                if (sweepIndex != index)
                    subsetCPM[sweepIndex][index] = subsetCPM[sweepIndex][index]*oneOverPivot;
            }
            subsetCPM[sweepIndex][sweepIndex] = oneOverPivot;
        }
    }
    
    public static String[][] shrinkStringArray(String[][] array, int numModels) {
        int offset = array.length - numModels;
        String[][] newArray =new String[numModels][];
        for (int index=0; index < numModels; index++)
            newArray[index] = array[offset+index].clone();
        return newArray;
    }
    
    public static double[][] shrinkDoubleArray(double[][] array, int numModels) {
        int offset = array.length-numModels;
        double[][] newArray =new double[numModels][];
        for (int index=0; index < numModels; index++)
            newArray[index] = array[offset+index].clone();
        return newArray;
    }
    
    public static double[][] copy2D(double[][] original) {
        int dim1 = original.length;
        double[][] copy = new double[dim1][];
        int dim2;
        for (int index=0; index<dim1; index++) {
            dim2 = original[index].length;
            copy[index] = new double[dim2];
            System.arraycopy(original[index], 0, copy[index], 0, dim2);
        }
        return copy;
    }

    public static Key[] shrinkKeyArray(Key[] array, int numModels) {
        int arrLen = array.length;
        Key[] newArray = new Key[numModels];
        System.arraycopy(array, (arrLen-numModels), newArray, 0, numModels);
        return newArray;
    }
    
    public static String joinDouble(double[] val) {
        int arrLen = val.length; // skip the intercept terms
        String[] strVal = new String[arrLen];
        for (int index=0; index < arrLen; index++)
            strVal[index] = Double.toString(val[index]);
        return String.join(", ", strVal);
    }
    
    public static GLMModel.GLMParameters[] generateGLMParameters(Frame[] trainingFrames,
                                                                 ModelSelectionModel.ModelSelectionParameters parms, 
                                                                 int nfolds, String foldColumn,
                                                                 Model.Parameters.FoldAssignmentScheme foldAssignment) {
        final int numModels = trainingFrames.length;
        GLMModel.GLMParameters[] params = new GLMModel.GLMParameters[numModels];
        final Field[] field1 = ModelSelectionModel.ModelSelectionParameters.class.getDeclaredFields();
        final Field[] field2 = Model.Parameters.class.getDeclaredFields();
        for (int index = 0; index < numModels; index++) {
            params[index] = new GLMModel.GLMParameters();
            setParamField(parms, params[index], false, field1, Collections.emptyList());
            setParamField(parms, params[index], true, field2, Collections.emptyList());
            params[index]._train = trainingFrames[index]._key;
            params[index]._nfolds = nfolds;
            params[index]._fold_column = foldColumn;
            params[index]._fold_assignment = foldAssignment;
        }
        return params;
    }
    
    public static void setParamField(Model.Parameters params, GLMModel.GLMParameters glmParam, boolean superClassParams,
                                     Field[] paramFields, List<String> excludeList) {
        // assign relevant GAMParameter fields to GLMParameter fields
        Field glmField;
        boolean emptyExcludeList = excludeList.size() == 0;
        for (Field oneField : paramFields) {
            try {
                if (emptyExcludeList || !excludeList.contains(oneField.getName())) {
                    if (superClassParams)
                        glmField = glmParam.getClass().getSuperclass().getDeclaredField(oneField.getName());
                    else
                        glmField = glmParam.getClass().getDeclaredField(oneField.getName());
                    glmField.set(glmParam, oneField.get(params));
                }
            } catch (IllegalAccessException|NoSuchFieldException e) { // suppress error printing, only cares about fields that are accessible
                ;
            }
        }    
    }
    
    public static GLM[] buildGLMBuilders(GLMModel.GLMParameters[] trainingParams) {
        int numModels = trainingParams.length;
        GLM[] builders = new GLM[numModels];
        for (int index=0; index<numModels; index++)
            builders[index] = new GLM(trainingParams[index]);
        return builders;
    }
    
    public static void removeTrainingFrames(Frame[] trainingFrames) {
        for (Frame oneFrame : trainingFrames) 
            DKV.remove(oneFrame._key);
    }

    /**
     * Given GLM run results of a fixed number of predictors, find the model with the best R2 value.
     *
     * @param glmResults
     */
    public static GLMModel findBestModel(GLM[] glmResults) {
        double bestR2Val = 0;
        int numModels = glmResults.length;
        GLMModel bestModel = null;
        for (int index = 0; index < numModels; index++) {
            GLMModel oneModel = glmResults[index].get();
            double currR2 = oneModel.r2();
            if (oneModel._parms._nfolds > 0) {
                int r2Index = Arrays.asList(oneModel._output._cross_validation_metrics_summary.getRowHeaders()).indexOf("r2");
                Float tempR2 = (Float) oneModel._output._cross_validation_metrics_summary.get(r2Index, 0);
                currR2 = tempR2.doubleValue();
            }
            if (currR2 > bestR2Val) {
                bestR2Val = currR2;
                if (bestModel != null)
                    bestModel.delete();
                bestModel = oneModel;
            } else {
                oneModel.delete();
            }
        }
        return bestModel;
    }

    public static String[] extractPredictorNames(Model.Parameters parms, DataInfo dinfo,
                                                 String foldColumn) {
        List<String> frameNames = Arrays.stream(dinfo._adaptedFrame.names()).collect(Collectors.toList());
        String[] nonResponseCols = parms.getNonPredictors();
        for (String col : nonResponseCols)
            frameNames.remove(col);
        if (foldColumn != null && frameNames.contains(foldColumn))
            frameNames.remove(foldColumn);
        return frameNames.stream().toArray(String[]::new);
    }
    
    public static int findMinZValue(GLMModel model, List<String> numPredNames, List<String> catPredNames, 
                                    List<String> predNames) {
        List<Double> zValList = Arrays.stream(model._output.zValues()).boxed().map(Math::abs).collect(Collectors.toList());
        List<String> coeffNames = Arrays.stream(model._output.coefficientNames()).collect(Collectors.toList());
        if (coeffNames.contains("Intercept")) { // remove intercept terms
            int interceptIndex = coeffNames.indexOf("Intercept");
            zValList.remove(interceptIndex);
            coeffNames.remove(interceptIndex);
        }
        // grab min z-values for numerical and categorical columns
        PredNameMinZVal numericalPred = findNumMinZVal(numPredNames, zValList, coeffNames);
        PredNameMinZVal categoricalPred = findCatMinZVal(model, zValList);
        
        // choose the min z-value from numerical and categorical predictors and return its index in predNames
        if (categoricalPred._minZVal >= 0 && categoricalPred._minZVal < numericalPred._minZVal) { // categorical pred has minimum z-value
            catPredNames.remove(catPredNames.indexOf(categoricalPred._predName));
            return predNames.indexOf(categoricalPred._predName);
        } else {    // numerical pred has minimum z-value
            numPredNames.remove(numPredNames.indexOf(numericalPred._predName));
            return predNames.indexOf(numericalPred._predName);
        }
    }
    
    public static PredNameMinZVal findNumMinZVal(List<String> numPredNames, List<Double> zValList, List<String> coeffNames) {
        double minNumVal = -1;
        String numPredMinZ = null;
        if (numPredNames != null && numPredNames.size() > 0) {
            List<Double> numZValues = new ArrayList<>();
            for (String predName : numPredNames) {
                int eleInd = coeffNames.indexOf(predName);
                double oneZValue = zValList.get(eleInd);
                if (Double.isNaN(oneZValue)) {
                    zValList.set(eleInd, 0.0);
                    numZValues.add(0.0);    // NaN corresponds to coefficient of 0.0
                } else {
                    numZValues.add(oneZValue);
                }
            }
            minNumVal = numZValues.stream().min(Double::compare).get(); // minimum z-value of numerical predictors
            numPredMinZ = numPredNames.get(numZValues.indexOf(minNumVal));
        }
        return new PredNameMinZVal(numPredMinZ, minNumVal);
    }

    /***
     * This method extracts the categorical coefficient z-value by using the following method:
     * 1. From GLMModel model, it extracts the column names of the dinfo._adaptedFrame that is used to build the glm 
     * model and generate the glm coefficients.  The column names will be in exactly the same order as the coefficient
     * names with the exception that each enum levels will not be given a name in the column names.
     * 2. To figure out which coefficient name corresponds to which column name, we use the catOffsets which will tell
     * us how many enum levels are used in the glm model coefficients.  If the catOffset for the first coefficient
     * says 3, that means that column will have three enum levels represented in the glm model coefficients.
     */
    public static PredNameMinZVal findCatMinZVal(GLMModel model, List<Double> zValList) {
        String[] columnNames = model.names(); // column names of dinfo._adaptedFrame
        int[] catOffsets = model._output.getDinfo()._catOffsets;
        double minCatVal = -1;
        String catPredMinZ = null;
        if (catOffsets != null) {
            minCatVal = Double.MAX_VALUE;
            int numCatCol = catOffsets.length-1;

            for (int catInd = 0; catInd < numCatCol; catInd++) {    // go through each categorical column
                List<Double> catZValues = new ArrayList<>();
                int nextCatOffset = catOffsets[catInd+1];
                for (int eleInd = catOffsets[catInd]; eleInd < nextCatOffset; eleInd++) {   // check z-value for each level
                    double oneZVal = zValList.get(eleInd);
                    if (Double.isNaN(oneZVal)) {
                        zValList.set(eleInd, 0.0);
                        catZValues.add(0.0);
                    } else {
                        catZValues.add(oneZVal);
                    }
                }
                if (catZValues.size() > 0) {
                    double oneCatMinZ = catZValues.stream().max(Double::compare).get(); // choose the best z-value here
                    if (oneCatMinZ < minCatVal) {
                        minCatVal = oneCatMinZ;
                        catPredMinZ = columnNames[catInd];
                    }
                }
            }
        }
        return new PredNameMinZVal(catPredMinZ, minCatVal);
    }
    
    static class PredNameMinZVal {
        String _predName;
        double _minZVal;
        
        public PredNameMinZVal(String predName, double minZVal) {
            _predName= predName;
            _minZVal = minZVal;
        }
    }
    
    public static List<String> extraModelColumnNames(List<String> coefNames, GLMModel bestModel) {
        List<String> coefUsed = new ArrayList<String>();
        List<String> modelColumns = new ArrayList<>(Arrays.asList(bestModel.names()));
        for (String coefName : modelColumns) {
            if (coefNames.contains(coefName)) 
                coefUsed.add(coefName);
        }
        return coefUsed;
    }

    /***
     * Given a predictor subset and the complete CPM, we extract the CPM associated with the predictors 
     * specified in the predictor subset (predIndices).  
     */
    public static double[][] extractPredSubsetsCPM(double[][] allCPM, int[] predIndices, int[][] pred2CPMIndices,
                                                   boolean hasIntercept) {
        List<Integer> CPMIndices = extractCPMIndexFromPred(allCPM, pred2CPMIndices, predIndices, hasIntercept);
        int subsetcpmDim = CPMIndices.size();
        double[][] subsetCPM = new double[subsetcpmDim][subsetcpmDim];

        for (int rIndex=0; rIndex < subsetcpmDim; rIndex++) {
            for (int cIndex=rIndex; cIndex < subsetcpmDim; cIndex++) {
                subsetCPM[rIndex][cIndex] = allCPM[CPMIndices.get(rIndex)][CPMIndices.get(cIndex)];
                subsetCPM[cIndex][rIndex] = allCPM[CPMIndices.get(cIndex)][CPMIndices.get(rIndex)];
            }
        }
        return subsetCPM;
    }
}
