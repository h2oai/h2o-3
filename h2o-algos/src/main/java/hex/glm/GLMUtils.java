package hex.glm;

import water.MemoryManager;
import water.fvec.Frame;
import water.util.ArrayUtils;
import water.util.TwoDimTable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GLMUtils {

  /***
   * From the gamColnames, this method attempts to translate to the column indices in adaptFrame.
   * @param adaptFrame
   * @param gamColnames
   * @return
   */
  public static int[][] extractAdaptedFrameIndices(Frame adaptFrame, String[][] gamColnames, int numOffset) {
    String[] frameNames = adaptFrame.names();
    List<String> allColNames = new ArrayList<>();
    for (String name:frameNames)
      allColNames.add(name);
    int[][] gamColIndices = new int[gamColnames.length][];
    int numFrame = gamColnames.length;
    for (int frameNum=0; frameNum < numFrame; frameNum++) {
      int numCols = gamColnames[frameNum].length;
      gamColIndices[frameNum] = MemoryManager.malloc4(numCols);
      for (int index=0; index < numCols; index++) {
        gamColIndices[frameNum][index] = numOffset+allColNames.indexOf(gamColnames[frameNum][index]);
      }
    }
    return gamColIndices;
  }
  
  public static GLM.GLMGradientInfo copyGInfo(GLM.GLMGradientInfo ginfo) {
    double[] gradient = ginfo._gradient.clone();
    GLM.GLMGradientInfo tempGinfo = new GLM.GLMGradientInfo(ginfo._likelihood, ginfo._objVal, gradient);
    return tempGinfo;
  }

  public static TwoDimTable combineScoringHistory(TwoDimTable glmSc1, TwoDimTable earlyStopSc2, 
                                                  List<Integer> scoreIterationList) {
    String[] esColTypes = earlyStopSc2.getColTypes();
    String[] esColFormats = earlyStopSc2.getColFormats();
    List<String> finalColHeaders = new ArrayList<>(Arrays.asList(glmSc1.getColHeaders()));
    int indexOfIter = finalColHeaders.indexOf("iteration");
    if (indexOfIter < 0)
      indexOfIter = finalColHeaders.indexOf("iterations");
    List<String> finalColTypes = new ArrayList<>(Arrays.asList(glmSc1.getColTypes()));
    List<String> finalColFormats = new ArrayList<>(Arrays.asList(glmSc1.getColFormats()));
    List<Integer> earlyStopColIndices = new ArrayList<Integer>();

    int colCounter = 0;
    for (String colName : earlyStopSc2.getColHeaders()) { // collect final table colHeaders, RowHeaders, ColFormats, ColTypes
      if (!finalColHeaders.contains(colName.toLowerCase())) {
        finalColHeaders.add(colName);
        finalColTypes.add(esColTypes[colCounter]);
        finalColFormats.add(esColFormats[colCounter]);
        earlyStopColIndices.add(colCounter);
      }
      colCounter++;
    }
    final int tableSize = finalColHeaders.size();
    TwoDimTable res = new TwoDimTable("Scoring History", "",
            glmSc1.getRowHeaders(), finalColHeaders.toArray(new String[tableSize]),
            finalColTypes.toArray(new String[tableSize]), finalColFormats.toArray(new String[tableSize]),
            "");
    res = combineTableContents(glmSc1, earlyStopSc2, res, earlyStopColIndices, scoreIterationList, indexOfIter);
    return res;
  }
  
  // glmSc1 is updated for every iteration while earlyStopSc2 is updated per scoring interval.  Hence, glmSc1 is
  // very likely to be longer than earlyStopSc2.  We only add earlyStopSc2 to the table when the iteration 
  // indices align with each other.
  public static TwoDimTable combineTableContents(final TwoDimTable glmSc1, final TwoDimTable earlyStopSc2,
                                                 TwoDimTable combined, final List<Integer> earlyStopColIndices,
                                                 List<Integer> scoreIterationList, final int indexOfIter) {
    final int rowSize = glmSc1.getRowDim();
    final int rowSize2 = earlyStopSc2.getRowDim();
    final int glmColSize = glmSc1.getColDim();
    final int earlyStopColSize = earlyStopColIndices.size();
    int sc2RowIndex = 0;
    for (int rowIndex = 0; rowIndex < rowSize; rowIndex++) {
      for (int colIndex = 0; colIndex < glmColSize; colIndex++) { // add contents of glm Scoring history first
        combined.set(rowIndex, colIndex, glmSc1.get(rowIndex, colIndex));
      }
      if (sc2RowIndex < rowSize2) {
        final int glmSc1Iteration = (int) glmSc1.get(rowIndex, indexOfIter);
        if (scoreIterationList.contains(glmSc1Iteration)) {
          int sc2Index = scoreIterationList.indexOf(glmSc1Iteration);
          final int earlyStopIteration = scoreIterationList.get(sc2Index);
          scoreIterationList.remove(sc2Index);
          if (glmSc1Iteration == earlyStopIteration) { // combine scoring histories when iteration number match
            for (int colIndex = 0; colIndex < earlyStopColSize; colIndex++) { // add early stop scoring history content
              int trueColIndex = colIndex + glmColSize;
              combined.set(rowIndex, trueColIndex, earlyStopSc2.get(sc2RowIndex, earlyStopColIndices.get(colIndex)));
            }
            sc2RowIndex++;
          }
        }
      }
    }
    return combined;
  }
  
  public static void updateGradGam(double[] gradient, double[][][] penalty_mat, int[][] gamBetaIndices, double[] beta,
                                   int[] activeCols) { // update gradient due to gam smoothness constraint
    int numGamCol = gamBetaIndices.length; // number of predictors used for gam
    for (int gamColInd = 0; gamColInd < numGamCol; gamColInd++) { // update each gam col separately
      int penaltyMatSize = penalty_mat[gamColInd].length;
      for (int betaInd = 0; betaInd < penaltyMatSize; betaInd++) {  // derivative of each beta in penalty matrix
        int currentBetaIndex = gamBetaIndices[gamColInd][betaInd];
        if (activeCols!=null) {
          currentBetaIndex = ArrayUtils.find(activeCols, currentBetaIndex);
        }
        double tempGrad = 2*beta[currentBetaIndex]*penalty_mat[gamColInd][betaInd][betaInd];
        for (int rowInd=0; rowInd < penaltyMatSize; rowInd++) {
          if (rowInd != betaInd) {
            int currBetaInd = gamBetaIndices[gamColInd][rowInd];
            if (activeCols!=null) {
              currBetaInd = ArrayUtils.find(activeCols, currBetaInd);
            }
            tempGrad += beta[currBetaInd] * penalty_mat[gamColInd][betaInd][rowInd];
          }
        }
        gradient[currentBetaIndex] += tempGrad;
      }
    }
  }

  // Note that gradient is [ncoeff][nclass].
  public static void updateGradGamMultinomial(double[][] gradient, double[][][] penaltyMat, int[][] gamBetaIndices,
                                              double[][] beta) {
    int numClass = beta[0].length;
    int numGamCol = gamBetaIndices.length;
    for (int classInd = 0; classInd < numClass; classInd++) {
      for (int gamInd = 0; gamInd < numGamCol; gamInd++) {
        int numKnots = gamBetaIndices[gamInd].length;
        for (int rowInd = 0; rowInd < numKnots; rowInd++) { // calculate dpenalty/dbeta rowInd
          double temp = 0.0;
          int betaIndR = gamBetaIndices[gamInd][rowInd];  // dGradient/dbeta_betaIndR
          for (int colInd = 0; colInd < numKnots; colInd++) {
            int betaIndC = gamBetaIndices[gamInd][colInd];
            temp += (betaIndC==betaIndR)?(2*penaltyMat[gamInd][rowInd][colInd]*beta[betaIndC][classInd])
                    :penaltyMat[gamInd][rowInd][colInd]*beta[betaIndC][classInd];
          }
          gradient[betaIndR][classInd] += temp;
        }
      }
    }
  }

  public static double calSmoothNess(double[] beta, double[][][] penaltyMatrix, int[][] gamColIndices) {
    int numGamCols = gamColIndices.length;
    double smoothval = 0;
    for (int gamCol=0; gamCol < numGamCols; gamCol++) {
      smoothval += ArrayUtils.innerProductPartial(beta, gamColIndices[gamCol],
              ArrayUtils.multArrVecPartial(penaltyMatrix[gamCol], beta, gamColIndices[gamCol]));
    }
    return smoothval;
  }

  /**
   *
   * @param beta multinomial number of class by number of predictors
   * @param penaltyMatrix
   * @param gamColIndices
   * @return
   */
  public static double calSmoothNess(double[][] beta, double[][][] penaltyMatrix, int[][] gamColIndices) {
    int numClass = beta.length;
    double smoothval=0;
    for (int classInd=0; classInd < numClass; classInd++) {
      smoothval += calSmoothNess(beta[classInd], penaltyMatrix, gamColIndices);
    }
    return smoothval;
  }
}
