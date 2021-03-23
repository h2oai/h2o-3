package hex.gam.GamSplines;

import water.MRTask;
import water.MemoryManager;
import water.fvec.Chunk;
import water.fvec.NewChunk;

import static hex.gam.GamSplines.ThinPlateRegressionUtils.*;
import static hex.genmodel.algos.gam.GamUtilsThinPlateRegression.calculatePolynomialBasis;

public class ThinPlatePolynomialWithKnots extends MRTask<ThinPlatePolynomialWithKnots> {
  final int _weightID;
  final int[][] _polyBasisList;
  final int _M; // size of polynomial basis
  final int _d; // number of predictors used
  final double[] _gamColMeanRaw;
  final double[] _oneOverColStd;
  final boolean _standardizeGAM;

  public ThinPlatePolynomialWithKnots(int weightID, int[][] polyBasis, double[] gamColMeanRaw, double[] oneOverColStd, boolean standardizeGAM) {
    _weightID = weightID;
    _d = weightID;
    _polyBasisList = polyBasis;
    _M = polyBasis.length;
    _gamColMeanRaw = gamColMeanRaw;
    _oneOverColStd = oneOverColStd;
    _standardizeGAM = standardizeGAM;
  }

  @Override
  public void map(Chunk[] chk, NewChunk[] newGamCols) {
    int numRow = chk[0].len();
    double[] onePolyRow = MemoryManager.malloc8d(_M);
    double[] oneDataRow = MemoryManager.malloc8d(_d);
    for (int rowIndex = 0; rowIndex < numRow; rowIndex++) {
      if (chk[_weightID].atd(rowIndex) != 0) {
        if (checkRowNA(chk, rowIndex)) {
          fillRowOneValue(newGamCols, _M, Double.NaN);
        } else {
          extractNDemeanOneRowFromChunk(chk, rowIndex, oneDataRow, _d); // extract data to oneDataRow
          calculatePolynomialBasis(onePolyRow, oneDataRow, _d, _M, _polyBasisList, _gamColMeanRaw, _oneOverColStd,
                  _standardizeGAM);                 // generate polynomial basis for oneDataRow
          fillRowArray(newGamCols, _M, onePolyRow); // fill newChunk with array onePolyRow
        }
      } else {  // set the row to zero
        fillRowOneValue(newGamCols, _M, 0.0);
      }
    }
  }
  
  // grab data in each chunk into an array
  public static void extractNDemeanOneRowFromChunk(Chunk[] chk, int rowIndex, double[] oneRow, int d) {
    for (int colInd = 0; colInd < d; colInd++)
      oneRow[colInd] = chk[colInd].atd(rowIndex);
  }
}
