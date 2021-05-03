package hex.anovaglm;

import hex.DataInfo;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.NewChunk;

import static hex.anovaglm.AnovaGLMModel.AnovaGLMParameters;
import static hex.anovaglm.AnovaGLMUtils.find;
import static hex.anovaglm.AnovaGLMUtils.findComboMatch;

/***
 * This class will take two predictors and transform them according to rules specified in Wendy Docs
 */
public class GenerateTransformColumns extends MRTask<GenerateTransformColumns> {
  final public int[] _newColNumber;
  final public boolean _imputeMissing;
  final public int[] _catNAFills;
  final public double[] _numNAFills;
  final int _numNewCols;
  final boolean _hasWeight;
  final boolean _hasOffset;
  final int _weightID;
  final int _offsetID;
  final int _responseID;
  final int _numPredIndividual;
  final int _nCats;
  final int _nNums;
  final String[][] _transformedColNames;
  final String[][] _predColsCombo;
  
  public GenerateTransformColumns(String[][] newColNames, AnovaGLMParameters parms, DataInfo dinfo, int numPreds, 
                                  String[][] predColsCombo) {
    _predColsCombo = predColsCombo;
    _transformedColNames = newColNames;
    _newColNumber = countColNumber(newColNames);
    _imputeMissing = parms.imputeMissing();
    _catNAFills = dinfo.catNAFill();
    _nCats = dinfo._cats;
    _nNums = dinfo._nums;
    _numNAFills = dinfo.numNAFill();
    _numNewCols = _newColNumber.length;
    _hasWeight = parms._weights_column != null;
    _hasOffset = parms._offset_column != null;
    _weightID = _hasWeight ? dinfo.weightChunkId() : -1;
    _offsetID = _hasOffset ? dinfo.offsetChunkId() : -1;
    _responseID = dinfo.responseChunkId(0);
    _numPredIndividual = numPreds;
  }
  
  public static int[] countColNumber(String[][] transformedColNames) {
    int[] colNumber = new int[transformedColNames.length];
    for (int colInd = 0; colInd < transformedColNames.length; colInd++) {
      colNumber[colInd] = transformedColNames[colInd].length;
    }
    return colNumber;
  }

  @Override
  public void map(Chunk[] chk, NewChunk[] newChk) {
    int numChkRows = chk[0].len();
    double[][] changedRow = allocateRow(_newColNumber);  // pre-allocate array for reuse
    double[] oneRow = new double[_numPredIndividual];       // read in chunk row
    for (int rowInd = 0; rowInd < numChkRows; rowInd++) {
      if (!readCatVal(chk, rowInd, oneRow)) // read in one row of data
        continue; // imputeMissing=skip and encounter NAs

      transformOneRow(changedRow, oneRow, _numPredIndividual, _newColNumber);
      int colIndex = 0;
      for (int predInd = 0; predInd < _numNewCols; predInd++) {
        for (int eleInd = 0; eleInd < _newColNumber[predInd]; eleInd++)
          newChk[colIndex++].addNum(changedRow[predInd][eleInd]);
      }
      if (_hasWeight)
        newChk[colIndex++].addNum(chk[_weightID].atd(rowInd));
      if (_hasOffset)
        newChk[colIndex++].addNum(chk[_offsetID].atd(rowInd));
      newChk[colIndex].addNum(chk[_responseID].atd(rowInd));
    }
  }
  
  public  double imputeNA(int colIndex) {
    if (colIndex < _nCats)
      return _catNAFills[colIndex];
    else
      return _numNAFills[colIndex-_nCats];
  }
  
  public static double[][] allocateRow(int[] newColNumber) {
    int numPreds = newColNumber.length;
    double[][] oneRow = new double[numPreds][];
    for (int index = 0; index < numPreds; index++)
      oneRow[index] = new double[newColNumber[index]];
    return oneRow;
  }
  
  public void transformOneRow(double[][] newRow, double[] val, int numPreds, int[] newColNumber) {
    // transform individual enum predictors
    for (int colInd = 0; colInd < _nCats; colInd++) {
      for (int valInd = 0; valInd < newColNumber[colInd]; valInd++) {
        if (val[colInd] == valInd)
          newRow[colInd][valInd] = 1;
        else if (val[colInd] == newColNumber[colInd])
          newRow[colInd][valInd] = -1;
        else
          newRow[colInd][valInd] = 0;
      }
    }
    // transform individual num predictors
    for (int colInd = _nCats; colInd < _numPredIndividual; colInd++)
      newRow[colInd][0] = val[colInd];
    // transform interacting columns
    transformInteractingPred(newRow);
  }
  
  public void transformInteractingPred(double[][] newRow) {
    for (int newColInd = _numPredIndividual; newColInd < _numNewCols; newColInd++) {
      String[] currPredNames = _predColsCombo[newColInd];
      int matchPCols = findComboMatch(_predColsCombo, newColInd);
      double[] transformedInteraction = newRow[matchPCols]; // grab the transformed interaction of later columns
      //int cols2TranformInd = IntStream.range(0, _predColsCombo.length).filter(i->_predColsCombo[i][0]==currPredNames[0]).findFirst().orElse(-1);
      int cols2TranformInd = find(_predColsCombo, currPredNames[0]);
      double[] currTransform = newRow[cols2TranformInd];
      int countInd = 0;
      for (int currInd = 0; currInd < currTransform.length; currInd++) {
        for (int matchInd = 0; matchInd < transformedInteraction.length; matchInd++)
          newRow[newColInd][countInd++] = currTransform[currInd]*transformedInteraction[matchInd];
      }
    }
  }

  boolean readCatVal(Chunk[] chk, int rowInd, double[] rowData) {
    for (int index = 0; index < _numPredIndividual; index++) {
      rowData[index] = chk[index].atd(rowInd);
      if (Double.isNaN(rowData[index])) {
        if (_imputeMissing)
          rowData[index] = imputeNA(index);
        else 
          return false;
      }
    }
    return true;
  }
}
