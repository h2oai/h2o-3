package hex.modelselection;

import hex.DataInfo;
import hex.Model;
import hex.glm.GLM;
import hex.glm.GLMModel;
import hex.glm.GLMTask;
import hex.gram.Gram;
import jsr166y.ForkJoinTask;
import jsr166y.RecursiveAction;
import water.DKV;
import water.Key;
import water.MemoryManager;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;

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
        boolean usePredIndices = predIndices != null;
        int numPreds = usePredIndices? predIndices.length : predNames.length;
        for (int index = 0; index < numPreds; index++) {
            int predVecNum = usePredIndices ? predIndices[index] : index;
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

    /***
     * Class to store the CPM produced, predNames and pred2CPMMapping after the removal of redundant predictors
     * if present
     */
    static class CPMnPredNames {
        double[][] _cpm;
        String[] _predNames;
        String[] _coefNames;
        int[][] _pred2CPMMapping;
        
        public CPMnPredNames(double[][] cpm, String[] predNames, String[] coeffNames, int[][] pred2CPMM) {
            _cpm = cpm; // cpm with duplicated predictors removed
            _predNames = predNames; // predictor names with duplicated predictors removed
            _pred2CPMMapping = pred2CPMM;   // mapping of predictors to cpm indices
            _coefNames = coeffNames;
        }
    }

    public static CPMnPredNames genCPMPredNamesIndex(Key jobKey, DataInfo dinfo, String[] predictorNames, 
                                                     ModelSelectionModel.ModelSelectionParameters parms) {
        // check if there are redundant predictors
        ArrayList<Integer> ignoredCols = new ArrayList<>();
        GLMTask.GLMIterationTask gtask = genGramCheckDup(jobKey, dinfo, ignoredCols, parms);
        double[] xTransposey;
        Gram gram = gtask.getGram();
        List<Integer> ignoredFullPredCols = new ArrayList<>();
        String[] coefNames = dinfo.coefNames();
        // drop unwanted predictors
        if (ignoredCols.size() > 0) {
            List<String> ignoredPredNames = new ArrayList<>();
            List<String> ignoredCoefNames = new ArrayList<>();
            ignoredFullPredCols = findFullDupPred(dinfo, ignoredCols, ignoredPredNames, ignoredCoefNames, predictorNames);
            coefNames = Arrays.stream(coefNames).filter(x -> !ignoredCoefNames.contains(x)).toArray(String[]::new);
            predictorNames = Arrays.stream(predictorNames).filter(x -> !ignoredPredNames.contains(x)).toArray(String[]::new);
            // drop cols from gram and XTY
            xTransposey = dropIgnoredCols(gtask, ignoredFullPredCols);
        } else {
            xTransposey = gtask.getXY();
        }
        return new CPMnPredNames(formCPM(gram, xTransposey, gtask.getYY()), predictorNames, coefNames, 
                mapPredIndex2CPMIndices(dinfo, predictorNames.length, ignoredFullPredCols));
    }

    /**
     *  This method attempts to map all predictors into the corresponding cpm indices that refer to that predictor.
     *  This is complicated by two things:
     *  a. the presence of duplicated predictors that are removed;
     *  b. the presence of enum predictors that will map one predictor to multiple consecutive cpm indices.
     *  Note that ignoredPredInd is at the level of coefficient indexing and not predictor indexing
     */
    public static int[][] mapPredIndex2CPMIndices(DataInfo dinfo, int numPreds, List<Integer> ignoredPredInd) {
        int[][] pred2CPMMapping = new int[numPreds][];
        int offset = 0;
        int countPreds = 0;

        for (int index=0; index < dinfo._cats; index++) {  // take care of categorical columns
            int catStartLevel = dinfo._catOffsets[index];
            if (!ignoredPredInd.contains(catStartLevel)) {  // enum pred not ignored
                int numLevels = dinfo._catOffsets[index + 1] - dinfo._catOffsets[index];    // number of catLevels
                pred2CPMMapping[countPreds++] = IntStream.iterate(offset, n -> n + 1).limit(numLevels).toArray();
                offset += numLevels;
            }
            if (countPreds >= numPreds)
                break;
        }
        int totPreds = dinfo._catOffsets[dinfo._cats]+dinfo._nums;
        for (int index=dinfo._catOffsets[dinfo._cats]; index < totPreds; index++) {
            if (countPreds >= numPreds)
                break;
            if (!ignoredPredInd.contains(index))
                pred2CPMMapping[countPreds++] = new int[]{offset++};
        }
        return pred2CPMMapping;
    }
    
    
    public static double[][] formCPM(Gram gram, double[] xTransposey, double yy) {
        int coeffSize = xTransposey.length;
        int cPMsize = coeffSize+1;
        double[][] crossProductMatrix = MemoryManager.malloc8d(cPMsize, cPMsize);
        gram.getXXCPM(crossProductMatrix, false, false);
        // copy xZTransposex, xTransposey, yy to crossProductMatrix
        for (int rowIndex=0; rowIndex<coeffSize; rowIndex++) {
            crossProductMatrix[rowIndex][coeffSize] = xTransposey[rowIndex];
        }
        System.arraycopy(xTransposey, 0, crossProductMatrix[coeffSize], 0, coeffSize);
        crossProductMatrix[coeffSize][coeffSize] = yy;
        return crossProductMatrix;
    }
    
    public static double[] dropIgnoredCols(GLMTask.GLMIterationTask gtask, List<Integer> ignoredCols) {
        Gram gram = gtask.getGram();
        int[] droppedCols = ignoredCols.stream().mapToInt(x->x).toArray();
        gram.dropCols(droppedCols);
        return ArrayUtils.removeIds(gtask.getXY(), droppedCols);
    }
    
    /***
     * The duplicated columns generated by qr-cholesky is at the level of coefficients.  This means for enum predictors
     * there could be multiple coefficients, one for each level of the enum level.  For enum predictors, I will 
     * remove the predictor only if all its columns are duplicated.  This make it a little more complicated.  The
     * returned ignored list is at the level of coefficients.
     */
    public static List<Integer> findFullDupPred(DataInfo dinfo, List<Integer> ignoredCols, List<String> ignoredPredNames, 
                                                List<String> ignoredCoefNames, String[] prednames) {
        List<Integer> ignoredColsCopy = new ArrayList<>(ignoredCols);
        List<Integer> fullIgnoredCols = new ArrayList<>();
        int[] catOffsets = dinfo._catOffsets;
        String[] allCoefNames = dinfo.coefNames();
        
        if (dinfo._cats > 0) {   // there are enum columns in dataset
            int catOffsetsLen = catOffsets.length;
            for (int index = 1; index < catOffsetsLen; index++) {
                final int counter = index;
                List<Integer> discarded = ignoredColsCopy.stream().filter(x -> x < catOffsets[counter]).collect(Collectors.toList());
                if ((discarded != null) && (discarded.size() == (catOffsets[index]-catOffsets[index-1]))) {  // full enum predictors found in ignored columns
                    fullIgnoredCols.addAll(discarded);
                    ignoredPredNames.add(prednames[index-1]);
                    ignoredCoefNames.addAll(discarded.stream().map(x -> allCoefNames[x]).collect(Collectors.toList()));
;                }
                if (discarded != null && discarded.size() > 0) 
                    ignoredColsCopy.removeAll(discarded);
            }
        }
        if (ignoredColsCopy != null && ignoredColsCopy.size()>0) {
            int offsetNum = dinfo._numOffsets[0]-dinfo._cats;
            ignoredPredNames.addAll(ignoredColsCopy.stream().map(x -> prednames[x-offsetNum]).collect(Collectors.toList()));
            ignoredCoefNames.addAll(ignoredColsCopy.stream().map(x -> allCoefNames[x]).collect(Collectors.toList()));
            fullIgnoredCols.addAll(ignoredColsCopy);    // add all remaining numerical ignored predictors columns
        }
        return fullIgnoredCols;
    }
    
    public static GLMTask.GLMIterationTask genGramCheckDup(Key jobKey, DataInfo dinfo, ArrayList<Integer> ignoredCols, 
                                                    ModelSelectionModel.ModelSelectionParameters parms) {
        double[] beta = new double[dinfo.coefNames().length];
        beta = Arrays.stream(beta).map(x -> 1.0).toArray(); // set coefficient to all 1
        GLMTask.GLMIterationTask gtask = new GLMTask.GLMIterationTask(jobKey, dinfo, new GLMModel.GLMWeightsFun(gaussian,
                GLMModel.GLMParameters.Link.identity, 1, 0.1, 0.1, 1, false), beta).doAll(dinfo._adaptedFrame);
        Gram gram = gtask.getGram();
        Gram.Cholesky chol = gram.qrCholesky(ignoredCols, parms._standardize);
        if (!chol.isSPD()) throw new Gram.NonSPDMatrixException();
        return gtask;
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
     * Given the original predictor subset, this function will go into a for loop and choose one predictor out of the
     * remaining predictor set validSubsets and put it into the array allPreds.  Then, it will spin off a process to 
     * calculation the error variance with the addition of the new predictor.  All error variances will be stored in
     * an array and returned for further processing.  This is very similar to generateAllErrVar but they are not the
     * same.  For starters, the replaced predictors are included in the predictor subset allPreds. For details, refer to 
     * https://github.com/h2oai/h2o-3/issues/6538, section VI.
     */
    public static double[] generateAllErrVarR(final double[][] allCPM, final Frame allCPMFrame, double[][] prevCPM, int predPos,
                                              List<Integer> currSubsetIndices, List<Integer> validSubsets,
                                              Set<BitSet> usedCombo, BitSet tempIndices,
                                              final int[][] pred2CPMIndices, final boolean hasIntercept,
                                              int[] removedPredSweepInd, SweepVector[][] removedPredSV) {
        int[] allPreds = new int[currSubsetIndices.size() + 1];   // store the bigger predictor subset
        int lastPredInd = allPreds.length - 1;
        System.arraycopy(currSubsetIndices.stream().mapToInt(Integer::intValue).toArray(), 0, allPreds, 0,
                allPreds.length - 1);
        int maxModelCount = validSubsets.size();
        RecursiveAction[] resA = new RecursiveAction[maxModelCount];
        final double[] subsetMSE = new double[maxModelCount];
        Arrays.fill(subsetMSE, Double.MAX_VALUE);
        int modelCount = 0;
        int[] oneLessSub = new int[lastPredInd];
        List<Integer> oneLessSubset = new ArrayList<>(currSubsetIndices);
        oneLessSubset.remove(predPos);
        System.arraycopy(oneLessSubset.stream().mapToInt(Integer::intValue).toArray(), 0, oneLessSub, 0,
                oneLessSub.length - 1);
        int oneLessSubInd = lastPredInd-1;
        for (int predIndex : validSubsets) {  // consider valid predictor indices only
            allPreds[lastPredInd] = predIndex;
            oneLessSub[oneLessSubInd] = predIndex;
            tempIndices.clear();
            setBitSet(tempIndices, oneLessSub);
            if (usedCombo.add((BitSet) tempIndices.clone())) {
                final int resCount = modelCount++;
                genMSE4MorePredsR(pred2CPMIndices, allCPM, allCPMFrame, prevCPM, allPreds, subsetMSE, resA, resCount,
                        hasIntercept, removedPredSV, removedPredSweepInd);
            }
        }
        ForkJoinTask.invokeAll(Arrays.stream(resA).filter(Objects::nonNull).toArray(RecursiveAction[]::new));
        return subsetMSE;
    }

    /***
     * Generate the error variance for one predictor subset setting in allPreds.  It will do the following:
     * 1. add rows/columns corresponding to new predictor and store the partial CPM in subsetCPM;
     * 2. sweep the new rows/columns from 1 with the sweep vectors generated for the removed predictor and store
     * everything in subsetCPM;
     * 3. sweep the subsetCPM with rows/columns associated with the new predictor;
     * 4. record the new error variance.
     * 
     * For details, refer to https://github.com/h2oai/h2o-3/issues/6538, section VI.
     */
    public static void genMSE4MorePredsR(final int[][] pred2CPMIndices, final double[][] allCPM, 
                                         final Frame allCPMFrame, double[][] prevCPM, final int[] allPreds, 
                                         final double[] subsetMSE, RecursiveAction[] resA, final int resCount, 
                                         final boolean hasIntercept, SweepVector[][] removePredSV, 
                                         int[] removedPredSweepInd) {
        final int[] subsetIndices = allPreds.clone();
        resA[resCount] = new RecursiveAction() {
            @Override
            protected void compute() {
                double[][] subsetCPM = addNewPred2CPM(allCPM, allCPMFrame, prevCPM, subsetIndices, pred2CPMIndices,
                        hasIntercept);  // new pred added but swept with removed predictor
                // swept just new pred with sweep vector of removed pred to undo its effect
                int newPredInd = subsetIndices[subsetIndices.length - 1];
                int newPredCPMLength = pred2CPMIndices[newPredInd].length;
                int lastSweepIndex = prevCPM.length-1;
                if (newPredCPMLength == 1) {
                    applySweepVectors2NewPred(removePredSV, subsetCPM, newPredCPMLength, removedPredSweepInd);
                } else {
                    SweepVector[][] newSV = mapBasicVector2Multiple(removePredSV, newPredCPMLength);
                    applySweepVectors2NewPred(newSV, subsetCPM, newPredCPMLength, removedPredSweepInd);
                }
                // sweep subsetCPM with newly added predictor
                int[] newPredSweepInd = IntStream.range(0,newPredCPMLength).map(x -> x+lastSweepIndex).toArray();
                sweepCPM(subsetCPM, newPredSweepInd, false);
                // only apply the sweepIndices to only the last element of the CPM
                int lastInd = subsetCPM.length-1;
                subsetMSE[resCount] = subsetCPM[lastInd][lastInd];
            }
        };
    }

    /***
     * Given the original predictor subset, this function will go into a for loop and choose one predictor out of the
     * remaining predictor set validSubsets and put it into the array allPreds.  Then, it will spin off a process to 
     * calculation the error variance with the addition of the new predictor.  All error variances will be stored in
     * an array and returned for further processing.  For details, refer to 
     * https://github.com/h2oai/h2o-3/issues/6538, section V.
     */
    public static double[] generateAllErrVar(final double[][] allCPM, Frame allCPMFrame, int prevCPMSize, 
                                             List<Integer> currSubsetIndices, List<Integer> validSubsets, 
                                             Set<BitSet> usedCombo, BitSet tempIndices, final int[][] pred2CPMIndices, 
                                             final boolean hasIntercept) {
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
                    genMSE4MorePreds(pred2CPMIndices, allCPM, allCPMFrame, allPreds, prevCPMSize, subsetMSE, resA, 
                            resCount, hasIntercept);
                }
            } else {    // start from first predictor
                final int resCount = modelCount++;
                genMSE1stPred(pred2CPMIndices, allCPM, allCPMFrame, allPreds, subsetMSE, resA, resCount,
                        hasIntercept);
            }
        }
        ForkJoinTask.invokeAll(Arrays.stream(resA).filter(Objects::nonNull).toArray(RecursiveAction[]::new));
        return subsetMSE;
    }

    /***
     * This method will calculate the error variance value for all predictors in the allPreds.  For details,
     * refer to https://github.com/h2oai/h2o-3/issues/6538, section V.
     */
    public static void genMSE4MorePreds(final int[][] pred2CPMIndices, final double[][] allCPM, final Frame allCPMFrame, 
                                        final int[] allPreds, int lastSweepIndex, final double[] subsetMSE, 
                                        RecursiveAction[] resA, final int resCount, final boolean hasIntercept) {
        final int[] subsetIndices = allPreds.clone();
        resA[resCount] = new RecursiveAction() {
            @Override
            protected void compute() {
                boolean multinodeMode = allCPM == null && allCPMFrame != null;
                double[][] subsetCPM = multinodeMode ? 
                        extractPredSubsetsCPMFrame(allCPMFrame, subsetIndices, pred2CPMIndices, hasIntercept) : 
                        extractPredSubsetsCPM(allCPM, subsetIndices, pred2CPMIndices, hasIntercept);
                int lastPredInd = subsetIndices[subsetIndices.length - 1];
                int newPredCPMLength = pred2CPMIndices[lastPredInd].length;
                int[] sweepIndices = IntStream.range(0,newPredCPMLength).map(x -> x+lastSweepIndex).toArray();
                sweepCPM(subsetCPM, sweepIndices, false);
                // only apply the sweepIndices to only the last element of the CPM
                int lastInd = subsetCPM.length-1;
                subsetMSE[resCount] = subsetCPM[lastInd][lastInd];
            }
        };
    }

    /***
     * This function performs sweeping on the last row and column only to update the variance error to reduce
     * computation time.
     */
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
    
    static class CPMElement {
        final int _row;
        final int _col;
        
        public CPMElement(int row, int col) {
            _row = row;
            _col = col;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof CPMElement) {
                return (_row == ((CPMElement) o)._row && _col == ((CPMElement) o)._col);
            }
            return false;
        }

        @Override
        public int hashCode() {
            Integer rowCol = _row+(_col+1)*10;
            return rowCol.hashCode();
        }
        
    }

    /***
     * This method will calculate the variance variance when only one predictor is considered in allPreds.  For details,
     * refer to https://github.com/h2oai/h2o-3/issues/6538, section V.
     */
    public static void genMSE1stPred(final int[][] pred2CPMIndices, final double[][] allCPM, final Frame allCPMFrame, 
                                     final int[] allPreds, final double[] subsetMSE, RecursiveAction[] resA, 
                                     final int resCount, final boolean hasIntercept) {
        final int[] subsetIndices = allPreds.clone();
        resA[resCount] = new RecursiveAction() {
            @Override
            protected void compute() {
                // generate CPM corresponding to the subset indices in subsetIndices
                boolean multinodeMode = allCPM == null && allCPMFrame != null;
                double[][] subsetCPM = multinodeMode ? 
                        extractPredSubsetsCPMFrame(allCPMFrame, subsetIndices, pred2CPMIndices, hasIntercept) : 
                        extractPredSubsetsCPM(allCPM, subsetIndices, pred2CPMIndices, hasIntercept);
                int lastSubsetIndex = subsetCPM.length-1;
                // perform sweeping action and record the sweeping vector and save the changed cpm
                subsetMSE[resCount] = sweepMSE(subsetCPM, IntStream.range(1, lastSubsetIndex).boxed().collect(Collectors.toList()));
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
                if (vInd < lastOldColInd) { // index within old sweep vector range
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
        if (sweepMat == null) {
            for (int sweepInd=0; sweepInd < numSweep; sweepInd++) {
                oneSweepWSweepVector(sweepVec[sweepInd], subsetCPM, sweepInd, numNewRows);
            }
        } else {
            int sweepInd;
            for (int index = 0; index < numSweep; index++) {
                sweepInd = sweepMat[index];
                oneSweepWSweepVector(sweepVec[index], subsetCPM, sweepInd, numNewRows);
            }
        }
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
                                            int colRowsAdded) {
        int sweepVecLen = sweepVec.length / 2;
        int newLastCPMInd = sweepVecLen - 1;
        int oldSweepVec = sweepVecLen - colRowsAdded;
        int oldLastCPMInd = oldSweepVec - 1;    // sweeping index before adding new rows/columns
        double[] colSweeps = new double[colRowsAdded];
        double[] rowSweeps = new double[colRowsAdded];
        Set<CPMElement> trackSweep = new HashSet<>();
        CPMElement oneEle;
        for (int rcInd = 0; rcInd < colRowsAdded; rcInd++) {   // for each newly added row/column
            int rowColInd = sweepVec[0]._column + rcInd;
            for (int svInd = 0; svInd < sweepVecLen; svInd++) { // working on each additional row/col
                int svIndOffset = svInd + sweepVecLen;
                if (sweepVec[svInd]._row == sweepIndex) {  // take care of both row and column elements at sweepIndex
                    rowSweeps[rcInd] = sweepVec[svInd]._value * subsetCPM[sweepIndex][rowColInd];
                    colSweeps[rcInd] = sweepVec[svIndOffset]._value * subsetCPM[rowColInd][sweepIndex];
                } else if (sweepVec[svInd]._row == newLastCPMInd) {
                    oneEle = new CPMElement(newLastCPMInd, rowColInd);
                    if (!trackSweep.contains(oneEle)) {
                        trackSweep.add(oneEle);
                        subsetCPM[newLastCPMInd][rowColInd] = subsetCPM[newLastCPMInd][rowColInd] -
                                sweepVec[svInd]._value * subsetCPM[sweepIndex][rowColInd];
                    }
                    oneEle = new CPMElement(rowColInd, newLastCPMInd);
                    if (!trackSweep.contains(oneEle)) {
                        trackSweep.add(oneEle);
                        subsetCPM[rowColInd][newLastCPMInd] = subsetCPM[rowColInd][newLastCPMInd] -
                                sweepVec[svIndOffset]._value * subsetCPM[rowColInd][sweepIndex];
                    }
                } else if (sweepVec[svInd]._row == rowColInd) {
                    oneEle = new CPMElement(rowColInd, rowColInd);
                    if (!trackSweep.contains(oneEle)) {
                        subsetCPM[rowColInd][rowColInd] = subsetCPM[rowColInd][rowColInd] -
                                subsetCPM[rowColInd][sweepIndex] * subsetCPM[sweepIndex][rowColInd] * sweepVec[svInd]._value;
                        trackSweep.add(oneEle);
                    }
                } else if (sweepVec[svInd]._row < oldLastCPMInd) {
                    oneEle = new CPMElement(sweepVec[svInd]._row, rowColInd);
                    if (!trackSweep.contains(oneEle)) {
                        subsetCPM[sweepVec[svInd]._row][rowColInd] = subsetCPM[sweepVec[svInd]._row][rowColInd] -
                                subsetCPM[sweepIndex][rowColInd] * sweepVec[svInd]._value;
                        trackSweep.add(oneEle);
                    }
                    oneEle = new CPMElement(rowColInd, sweepVec[svIndOffset]._column);
                    if (!trackSweep.contains(oneEle)) {
                        trackSweep.add(oneEle);
                        subsetCPM[rowColInd][sweepVec[svIndOffset]._column] =
                                subsetCPM[rowColInd][sweepVec[svIndOffset]._column] - subsetCPM[rowColInd][sweepIndex] *
                                        sweepVec[svIndOffset]._value;
                    }
                } else { // considering rows/columns >= oldSweepVec
                    oneEle = new CPMElement(sweepVec[svInd]._row, rowColInd);
                    if (!trackSweep.contains(oneEle)) {
                        trackSweep.add(oneEle);
                        subsetCPM[sweepVec[svInd]._row][rowColInd] = subsetCPM[sweepVec[svInd]._row][rowColInd] -
                                subsetCPM[sweepVec[svInd]._row][sweepIndex] * subsetCPM[sweepIndex][rowColInd] * sweepVec[svInd]._value;
                    }
                    oneEle = new CPMElement(rowColInd, sweepVec[svIndOffset]._column);
                    if (!trackSweep.contains(oneEle)) {
                        trackSweep.add(oneEle);
                        subsetCPM[rowColInd][sweepVec[svIndOffset]._column] = subsetCPM[rowColInd][sweepVec[svIndOffset]._column]
                                - subsetCPM[rowColInd][sweepIndex] * subsetCPM[sweepIndex][sweepVec[svIndOffset]._column] * sweepVec[svIndOffset]._value;
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
    public static double[][] addNewPred2CPM(double[][] allCPM, Frame allCPMFrame, double[][] currentCPM, 
                                            int[] subsetPredIndex, int[][] pred2CPMIndices, boolean hasIntercept) {
        boolean multinodeMode = allCPM == null && allCPMFrame != null;
        double[][] newCPM = multinodeMode ? 
                extractPredSubsetsCPMFrame(allCPMFrame, subsetPredIndex, pred2CPMIndices, hasIntercept) : 
                extractPredSubsetsCPM(allCPM, subsetPredIndex, pred2CPMIndices, hasIntercept);
        
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

    /**
     * Given predRemoved (the predictor that is to be removed and replaced in the forward step), this method will
     * calculate the locations of the CPM rows/columns associated with it.  CurrSubsetIndices contains the original
     * predictor subsets.
     */
    public static int[] extractSweepIndices(List<Integer> currSubsetIndices, int predPos, int predRemoved, 
                                     int[][] predInd2CPMIndices, boolean hasIntercept) {
        int predRemovedLen = predInd2CPMIndices[predRemoved].length;
        int totalSize = IntStream.range(0, predPos).map(x->predInd2CPMIndices[currSubsetIndices.get(x)].length).sum()
                + (hasIntercept ? 1 : 0);
        return IntStream.range(0, predRemovedLen).map(x -> x+totalSize).toArray();
    }
    
    public static List<Integer> extractCPMIndexFromPred(int cpmLastIndex, int[][] pred2CPMIndices, int[] newPredList, 
                                                        boolean hasIntercept) {
        List<Integer> CPMIndices = extractCPMIndexFromPredOnly(pred2CPMIndices, newPredList);
        if (hasIntercept)
            CPMIndices.add(0, cpmLastIndex-1);;
        CPMIndices.add(cpmLastIndex);
        return CPMIndices;
    }


    /***
     * Given the predictor in subset newPredList, this function will find the rows/columns in the cpm matrix that
     * are contributed by the predictors in subset newPredList.
     */
    public static List<Integer> extractCPMIndexFromPredOnly(int[][] pred2CPMIndices, int[] newPredList) {
        List<Integer> CPMIndices = new ArrayList<>();
        for (int predInd : newPredList) {
            CPMIndices.addAll(Arrays.stream(pred2CPMIndices[predInd]).boxed().collect(Collectors.toList()));
        }
        return CPMIndices;
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
    
    public static void sweepCPMParallel(Frame cpm, int[] sweepIndices, int[] trackPivotSweeps) {
        int numSweep = sweepIndices.length;
        for (int index=0; index < numSweep; index++) {
            new ModelSelectionTasks.SweepFrameParallel(trackPivotSweeps, sweepIndices[index], cpm).doAll(cpm);
            DKV.put(cpm);
            trackPivotSweeps[sweepIndices[index]] *= -1;
        }
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
       PredNameMinZVal categoricalPred = findCatMinOfMaxZScore(model, zValList); // null if all predictors are inactive
        
        // choose the min z-value from numerical and categorical predictors and return its index in predNames
        if (categoricalPred != null && categoricalPred._minZVal >= 0 && categoricalPred._minZVal < numericalPred._minZVal) { // categorical pred has minimum z-value
            return predNames.indexOf(categoricalPred._predName);
        } else {    // numerical pred has minimum z-value
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
                    zValList.set(eleInd, Double.POSITIVE_INFINITY);
                    numZValues.add(Double.POSITIVE_INFINITY);    // NaN corresponds to inactive predictors
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
     * This method extracts the categorical coefficient z-score (abs(z-value)) by using the following method:
     * 1. From GLMModel model, it extracts the column names of the dinfo._adaptedFrame that is used to build the glm 
     * model and generate the glm coefficients.  The column names will be in exactly the same order as the coefficient
     * names with the exception that each enum levels will not be given a name in the column names.
     * 2. To figure out which coefficient name corresponds to which column name, we use the catOffsets which will tell
     * us how many enum levels are used in the glm model coefficients.  If the catOffset for the first coefficient
     * says 3, that means that column will have three enum levels represented in the glm model coefficients.
     * 
     * For categorical predictors with multiple enum levels, we will look at the max z-score.  This will show the best
     * performing enum levels.  We will remove the enum predictor if its best z-score is not good enough when compared
     * to the z-score of other predictors.
     */
    public static PredNameMinZVal findCatMinOfMaxZScore(GLMModel model, List<Double> zValList) {
        String[] columnNames = model.names(); // column names of dinfo._adaptedFrame
        int[] catOffsets = model._output.getDinfo()._catOffsets;
        List<Double> bestZValues = new ArrayList<>();
        List<String> catPredNames = new ArrayList<>();
        if (catOffsets != null) {
            int numCatCol = catOffsets.length - 1;
            int numNaN = (int) zValList.stream().filter(x -> Double.isNaN(x)).count();
            if (numNaN == zValList.size()) {    // if all levels are NaN, this predictor is redundant
                return null;
            } else {
                for (int catInd = 0; catInd < numCatCol; catInd++) {    // go through each categorical column
                    List<Double> catZValues = new ArrayList<>();
                    int nextCatOffset = catOffsets[catInd + 1];
                    for (int eleInd = catOffsets[catInd]; eleInd < nextCatOffset; eleInd++) {   // check z-value for each level
                        double oneZVal = zValList.get(eleInd);
                        if (Double.isNaN(oneZVal)) {    // one level is inactivity, let other levels be used
                            zValList.set(eleInd, 0.0);
                            catZValues.add(0.0);
                        } else {
                            catZValues.add(oneZVal);
                        }
                    }
                    if (catZValues.size() > 0) {
                        double oneCatMinZ = catZValues.stream().max(Double::compare).get(); // choose the best z-value here
                        bestZValues.add(oneCatMinZ);
                        catPredNames.add(columnNames[catInd]);
                    }
                }
            }
        }
        if (bestZValues.size() < 1)
            return null;
        double maxCatLevel = bestZValues.stream().min(Double::compare).get();
        String catPredBestZ = catPredNames.get(bestZValues.indexOf(maxCatLevel));
        return new PredNameMinZVal(catPredBestZ, maxCatLevel);
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
     * specified in the predictor subset (predIndices).  If there is intercept, it will be moved to the first row
     * and column.
     */
    public static double[][] extractPredSubsetsCPM(double[][] allCPM, int[] predIndices, int[][] pred2CPMIndices,
                                                   boolean hasIntercept) {
        List<Integer> CPMIndices = extractCPMIndexFromPred(allCPM.length-1, pred2CPMIndices, predIndices, hasIntercept);
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

    /***
     * Given a predictor subset and the complete CPM, we extract the CPM associated with the predictors 
     * specified in the predictor subset (predIndices).  If there is intercept, it will be moved to the first row
     * and column.
     */
    public static double[][] extractPredSubsetsCPMFrame(Frame allCPM, int[] predIndices, int[][] pred2CPMIndices,
                                                   boolean hasIntercept) {
        List<Integer> CPMIndices = extractCPMIndexFromPred(allCPM.numCols()-1, pred2CPMIndices, predIndices, hasIntercept);
        int subsetcpmDim = CPMIndices.size();
        double[][] subsetCPM = new double[subsetcpmDim][subsetcpmDim];

        for (int rIndex=0; rIndex < subsetcpmDim; rIndex++) {
            for (int cIndex=rIndex; cIndex < subsetcpmDim; cIndex++) {
                subsetCPM[rIndex][cIndex] = allCPM.vec(CPMIndices.get(cIndex)).at(CPMIndices.get(rIndex));
                subsetCPM[cIndex][rIndex] = allCPM.vec(CPMIndices.get(rIndex)).at(CPMIndices.get(cIndex));
            }
        }
        return subsetCPM;
    }
}
