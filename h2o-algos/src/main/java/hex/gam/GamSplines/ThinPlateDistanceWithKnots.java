package hex.gam.GamSplines;

import hex.DataInfo;
import hex.glm.GLMModel.GLMParameters.MissingValuesHandling;
import hex.util.LinearAlgebraUtils.BMulInPlaceTask;
import water.MRTask;
import water.MemoryManager;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;

import static hex.gam.GAMModel.GAMParameters;
import static hex.gam.GamSplines.ThinPlateRegressionUtils.*;
import static hex.genmodel.algos.gam.GamUtilsThinPlateRegression.calculateDistance;
import static org.apache.commons.math3.util.CombinatoricsUtils.factorial;
import static water.util.ArrayUtils.transpose;

/**
 * Implementation details of this class can be found in GamThinPlateRegressionH2O.doc attached to this 
 * JIRA: https://h2oai.atlassian.net/browse/PUBDEV-7860
 **/
public class ThinPlateDistanceWithKnots extends MRTask<ThinPlateDistanceWithKnots> {
  final double[][] _knots;  // store knot values for the spline class
  final int _knotNum;       // number of knot values
  final int _d;             // number of predictors for smoothers
  final int _m;             // highest degree of polynomial basis +1
  final public double _constantTerms;
  final int _weightID;
  final boolean _dEven;
  final double[] _oneOverGamColStd;
  final boolean _standardizeGAM;
  
  public ThinPlateDistanceWithKnots(double[][] knots, int d, double[] oneOGamColStd, boolean standardizeGAM) {
    _knots = knots;
    _knotNum = _knots[0].length;
    _d = d;
    _dEven = _d%2==0;
    _m = calculatem(_d);
    _weightID = _d;     // weight column index
    _oneOverGamColStd = oneOGamColStd;
    _standardizeGAM = standardizeGAM;
    if (_dEven)
      _constantTerms = Math.pow(-1, _m+1+_d/2.0)/(Math.pow(2, 2*_m-1)*Math.pow(Math.PI, _d/2.0)*factorial(_m-1)*
              factorial(_m-_d/2));
    else
      _constantTerms = Math.pow(-1, _m)*_m/(factorial(2*_m)*Math.pow(Math.PI, (_d-1)/2.0));
  }

  @Override
  public void map(Chunk[] chk, NewChunk[] newGamCols) {
    int nrows = chk[0].len();
    double[] rowValues = MemoryManager.malloc8d(_knotNum);
    double[] chkRowValues = MemoryManager.malloc8d(_d);
    for (int rowIndex = 0; rowIndex < nrows; rowIndex++) {
      if (chk[_weightID].atd(rowIndex) != 0) {
        if (checkRowNA(chk, rowIndex)) {
          fillRowOneValue(newGamCols, _knotNum, Double.NaN);
        } else {  // calculate distance measure as in 3.1
          fillRowData(chkRowValues, chk, rowIndex, _d);
          calculateDistance(rowValues, chkRowValues, _knotNum, _knots, _d, _m, _dEven, _constantTerms, 
                  _oneOverGamColStd, _standardizeGAM);
          fillRowArray(newGamCols, _knotNum, rowValues);
        }
      } else {  // insert 0 to newChunk for weight == 0
        fillRowOneValue(newGamCols, _knotNum, 0.0);
      }
    }
  }
  
  public static void fillRowData(double[] rowHolder, Chunk[] chk, int rowIndex, int d) {
    for (int colIndex = 0; colIndex < d; colIndex++)
      rowHolder[colIndex] = chk[colIndex].atd(rowIndex);
  }

  /**
   * This function perform the operation described in 3.3 regarding the part of data Xnmd.
   * 
   * @param fr: H2OFrame to add gamificed columns to.
   * @param colNameStart start of column names for gamified columns
   * @param parms GAMParameters
   * @param zCST  transpose of zCS transform matrix
   * @param newColNum number of gamified columns to be added
   * @return
   */
  public static Frame applyTransform(Frame fr, String colNameStart, GAMParameters parms, double[][] zCST, int newColNum) {
    int numCols = fr.numCols(); // == numKnots
    DataInfo frInfo = new DataInfo(fr, null, 0, false,  DataInfo.TransformType.NONE, 
            DataInfo.TransformType.NONE, MissingValuesHandling.Skip == parms._missing_values_handling, 
            (parms._missing_values_handling == MissingValuesHandling.MeanImputation) || 
                    (parms._missing_values_handling == MissingValuesHandling.PlugValues), parms.makeImputer(), 
            false, false, false, false, null);
    // expand the frame with k-M columns which will contain the product of Xnmd*ZCS
    for (int colInd = 0; colInd < newColNum; colInd++) {
      fr.add(colNameStart+"_cs_"+colInd, fr.anyVec().makeZero());
    }
    new BMulInPlaceTask(frInfo, zCST, numCols, false).doAll(fr);
    for (int index=0; index < numCols; index++) { // remove the original gam columns
      Vec temp = fr.remove(0);
      temp.remove();
    }
    return fr;
  }
  
  public double[][] generatePenalty(double[][] qmat) {
    double[][] penaltyMat = new double[_knotNum][_knotNum];
    double[][] knotsTranspose = transpose(_knots);
    double[] tempVal = MemoryManager.malloc8d(_knotNum);
    for (int index = 0; index < _knotNum; index++) {
      calculateDistance(tempVal, knotsTranspose[index], _knotNum, _knots, _d, _m, _dEven, _constantTerms, 
              _oneOverGamColStd, _standardizeGAM);
      System.arraycopy(tempVal, 0, penaltyMat[index], 0, _knotNum);
    } // penaltyMat is right now hollow with zero off diagonal
    return penaltyMat;
  }
}
