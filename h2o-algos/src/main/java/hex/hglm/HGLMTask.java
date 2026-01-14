package hex.hglm;

import Jama.Matrix;
import hex.DataInfo;
import water.Job;
import water.MRTask;
import water.MemoryManager;
import water.fvec.Chunk;
import water.util.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static hex.hglm.HGLMUtils.fillZTTimesZ;
import static water.util.ArrayUtils.*;

public abstract class HGLMTask {
  // the doc = document attached to https://github.com/h2oai/h2o-3/issues/8487, title HGLM_H2O_Implementation.pdf
  // I will be referring to the doc and different parts of it to explain my implementation.
  
  /***
   * This class will calculate the residual Yj-Afj*beta-Arj*ubetaj for level 2 unit j.   It implements step 2 of 
   * section II.VIII of the doc.
   */
  public static class ResidualLLHTask extends MRTask<ResidualLLHTask> {
    final public double[][] _ubeta;
    final public double[] _beta;  // new fixed coefficients calculated
    final HGLMModel.HGLMParameters _parms;
    final DataInfo _dinfo;
    double _residualSquare;
    double[] _residualSquareLevel2;
    final int[] _fixedCatIndices; 
    final int _level2UnitIndex;
    final int _numLevel2Units;
    final int _predStartIndexFixed;
    final int[] _randomCatIndices;
    final int[] _randomNumIndices;
    final int[] _randomCatArrayStartIndices;
    final int _predStartIndexRandom;
    final int _numFixedCoeffs;
    final int _numRandomCoeffs;
    double[][] _yMinusXTimesZ;  // standarized if parms._standardize=true and vice versa
    double _sse_fixed;
    Job _job;
    final boolean _randomSlopeToo;
    
    public ResidualLLHTask(Job job, HGLMModel.HGLMParameters parms, DataInfo dataInfo, double[][] ubeta, 
                           double[] beta, ComputationEngineTask computeEngine) {
      _parms = parms;
      _dinfo = dataInfo;
      _ubeta = ubeta;
      _beta = beta;
      _job = job;
      _fixedCatIndices = computeEngine._fixedCatIndices;
      _level2UnitIndex = computeEngine._level2UnitIndex;
      _numLevel2Units = computeEngine._numLevel2Units;
      _predStartIndexFixed = computeEngine._predStartIndexFixed;
      _randomCatIndices = computeEngine._randomCatIndices;
      _randomNumIndices = computeEngine._randomNumIndices;
      _randomCatArrayStartIndices = computeEngine._randomCatArrayStartIndices;
      _predStartIndexRandom = computeEngine._predStartIndexRandom;
      _numFixedCoeffs = computeEngine._numFixedCoeffs;
      _numRandomCoeffs = computeEngine._numRandomCoeffs;
      _randomSlopeToo = _parms._random_columns != null && _parms._random_columns.length > 0;
    }

    @Override
    public void map(Chunk[] chks) {
      if(_job != null && _job.stop_requested()) return;
      _residualSquare = 0.0;
      _residualSquareLevel2 = new double[_numLevel2Units];
      double[] xji = MemoryManager.malloc8d(_numFixedCoeffs);
      double[] zji = MemoryManager.malloc8d(_numRandomCoeffs);
      int chkLen = chks[0].len();
      _yMinusXTimesZ = new double[_numLevel2Units][_numRandomCoeffs];
      int level2Index;
      double residual, y, residualSquare;
      double residualFixed;
      DataInfo.Row r = _dinfo.newDenseRow();
      for (int rowInd = 0; rowInd < chkLen; rowInd++) {
        _dinfo.extractDenseRow(chks, rowInd, r);
        if (!r.isBad() && !(r.weight == 0)) {
          y = r.response(0);
          level2Index = _parms._use_all_factor_levels ? r.binIds[_level2UnitIndex] - _dinfo._catOffsets[_level2UnitIndex] :
                  (int) chks[_level2UnitIndex].at8(rowInd);
          ComputationEngineTask.fillInFixedRowValues(r, xji, _parms, _fixedCatIndices, _level2UnitIndex, _numLevel2Units,
                  _predStartIndexFixed, _dinfo); // read in predictors for fixed coefficient effects
          ComputationEngineTask.fillInRandomRowValues(r, zji, _parms, _randomCatIndices, _randomNumIndices,
                  _randomCatArrayStartIndices, _predStartIndexRandom, _dinfo, _randomSlopeToo, _parms._random_intercept); // read in random coefficient effects
          residualFixed = y - innerProduct(xji, _beta) - r.offset;
          _sse_fixed += residualFixed * residualFixed;
          residual = residualFixed - innerProduct(zji, _ubeta[level2Index]);
          residualSquare = residual*residual;
          _residualSquare += residualSquare;
          _residualSquareLevel2[level2Index] += residualSquare;
          add(_yMinusXTimesZ[level2Index], mult(zji, residualFixed));
        }
      }
    }

    @Override
    public void reduce(ResidualLLHTask otherTask) {
      add(_residualSquareLevel2, otherTask._residualSquareLevel2);
      _residualSquare += otherTask._residualSquare;
      add(_yMinusXTimesZ, otherTask._yMinusXTimesZ);
      _sse_fixed += otherTask._sse_fixed;
    }
  }

  /***
   * This class will pre-calculate arrays (double[]) or matrices (double[][]) that will be used in later calculations
   * that are part of the CDSS described in equation 11 of the doc.
   *
   */
  public static class ComputationEngineTask extends MRTask<ComputationEngineTask> {
    double _YjTYjSum;   // calculate sum of transpose(Yj)*Yj across all level 2 units
    public double[][] _AfjTYj; // calculate transpose(Afj)*Yj for each level 2 unit, Y
    public double[][] _ArjTYj;
    public double[][][] _AfjTAfj; // equivalent to transpose(Xj)*Xj for each j
    public double[][][] _ArjTArj; // equivalent to tranpose(Zj)*Zj for each j
    public double[][][] _AfjTArj;
    public double[][][] _ArjTAfj;
    public double[][] _AfTAftInv;
    public double[] _AfTAftInvAfjTYj; // vectors are represented in row array.  Need to transpose it if used as Matrix
    public double[] _AfjTYjSum;
    double _oneOverJ;
    double _oneOverN;
    int _numFixedCoeffs;
    int _numRandomCoeffs;
    String[] _fixedCoeffNames;
    String[] _randomCoeffNames;
    String[] _level2UnitNames;
    int _numLevel2Units;
    final HGLMModel.HGLMParameters _parms;
    int _nobs;
    double _weightedSum;
    final DataInfo _dinfo;
    int _level2UnitIndex;
    int[] _randomPredXInterceptIndices;
    int[] _randomCatIndices;
    int[] _randomNumIndices;
    int[] _randomCatArrayStartIndices;   // starting index of random cat predictors
    int[] _fixedPredXInterceptIndices;
    int[] _fixedCatIndices;
    int[] _fixedNumIndices;
    String[] _fixedPredNames;
    String[] _randomPredNames;
    int _predStartIndexFixed;
    int _predStartIndexRandom;
    Job _job;
    final boolean _randomSlopeToo;
    double[][] _zTTimesZ;

    public ComputationEngineTask(Job job, HGLMModel.HGLMParameters parms, DataInfo dinfo) {
      _parms = parms;
      _dinfo = dinfo;
      _job = job;
      _randomSlopeToo = _parms._random_columns != null && _parms._random_columns.length > 0;
      extractNamesNIndices();
    }

    void setPredXInterceptIndices(List<String> predictorNames) {
      boolean randomColsExist = _parms._random_columns != null;
      _randomPredXInterceptIndices = randomColsExist ? new int[_parms._random_columns.length] : null;
      List<String> fixedPredNames = new ArrayList<>();
      List<String> randomPredNames = new ArrayList<>();

      List<Integer> randomCatPredList = new ArrayList<>();
      List<Integer> randomNumPredList = new ArrayList<>();
      _fixedPredXInterceptIndices = new int[predictorNames.size() - 1];
      List<Integer> fixedCatPredList = new ArrayList<>();
      List<Integer> fixedNumPredList = new ArrayList<>();
      if (randomColsExist) {
        for (int index = 0; index < _randomPredXInterceptIndices.length; index++) {
          _randomPredXInterceptIndices[index] = predictorNames.indexOf(_parms._random_columns[index]);
          if (_randomPredXInterceptIndices[index] < _dinfo._cats)
            randomCatPredList.add(_randomPredXInterceptIndices[index]);
          else
            randomNumPredList.add(_randomPredXInterceptIndices[index]);
          randomPredNames.add(predictorNames.get(_randomPredXInterceptIndices[index]));
        }
      }
      if (randomCatPredList.size() > 0) {
        _randomCatIndices = randomCatPredList.stream().mapToInt(x -> x).toArray();
        Arrays.sort(_randomCatIndices);
        List<Integer> randomCatLevels = Arrays.stream(_randomCatIndices).map(x -> _dinfo._adaptedFrame.vec(x).domain().length).boxed().collect(Collectors.toList());
        randomCatLevels.add(0, _parms._use_all_factor_levels ? 0 : 1);
        int[] randomCatArrayStartIndices = randomCatLevels.stream().map(x -> _parms._use_all_factor_levels ? x : (x - 1)).mapToInt(x -> x).toArray();
        _randomCatArrayStartIndices = ArrayUtils.cumsum(randomCatArrayStartIndices);
      }
      if (randomNumPredList.size() > 0) {
        _randomNumIndices = randomNumPredList.stream().mapToInt(x -> x).toArray();
        Arrays.sort(_randomNumIndices);
      }
      for (int index = 0; index < _fixedPredXInterceptIndices.length; index++) {
        String predName = predictorNames.get(index);
        if (!predName.equals(_parms._group_column)) {
          if (index < _dinfo._cats)
            fixedCatPredList.add(index);
          else
            fixedNumPredList.add(index);
          fixedPredNames.add(predName);
        }
      }
      if (fixedCatPredList.size() > 0) {
        _fixedCatIndices = fixedCatPredList.stream().mapToInt(x -> x).toArray();
        Arrays.sort(_fixedCatIndices);
      }
      if (fixedNumPredList.size() > 0) {
        _fixedNumIndices = fixedNumPredList.stream().mapToInt(x -> x).toArray();
        Arrays.sort(_fixedNumIndices);
      }

      _fixedPredNames = fixedPredNames.stream().toArray(String[]::new);
      _randomPredNames = randomPredNames.stream().toArray(String[]::new);
      _predStartIndexFixed = fixedCatPredList.size() == 0 ? 0 : (_parms._use_all_factor_levels ?
              Arrays.stream(_fixedCatIndices).map(x -> _dinfo._adaptedFrame.vec(x).domain().length).sum() :
              Arrays.stream(_fixedCatIndices).map(x -> (_dinfo._adaptedFrame.vec(x).domain().length - 1)).sum());
      _predStartIndexRandom = randomCatPredList.size() == 0 ? 0 : (_parms._use_all_factor_levels ?
              Arrays.stream(_randomCatIndices).map(x -> _dinfo._adaptedFrame.vec(x).domain().length).sum() :
              Arrays.stream(_randomCatIndices).map(x -> (_dinfo._adaptedFrame.vec(x).domain().length - 1)).sum());
    }

    void extractNamesNIndices() {
      List<String> predictorNames = Arrays.stream(_dinfo._adaptedFrame.names()).collect(Collectors.toList());
      _level2UnitIndex = predictorNames.indexOf(_parms._group_column);

      // assign coefficient names for fixed, random and group column
      List<String> allCoeffNames = Arrays.stream(_dinfo.coefNames()).collect(Collectors.toList());
      String groupCoeffStarts = _parms._group_column + ".";
      _level2UnitNames = Arrays.stream(_dinfo._adaptedFrame.vec(_level2UnitIndex).domain()).map(x -> groupCoeffStarts + x).toArray(String[]::new);
      List<String> groupCoeffNames = Arrays.stream(_level2UnitNames).collect(Collectors.toList());

      // fixed Coefficients are all coefficient names excluding group_column
      List<String> fixedCoeffNames = allCoeffNames.stream().filter(x -> !groupCoeffNames.contains(x)).collect(Collectors.toList());
      fixedCoeffNames.add("intercept");
      _fixedCoeffNames = fixedCoeffNames.stream().toArray(String[]::new);
      List<String> randomPredictorNames = new ArrayList<>();
      if (_randomSlopeToo) {
        // random coefficients names
        int[] randomColumnsIndicesSorted = Arrays.stream(_parms._random_columns).mapToInt(x -> predictorNames.indexOf(x)).toArray();
        Arrays.sort(randomColumnsIndicesSorted);
        _parms._random_columns = Arrays.stream(randomColumnsIndicesSorted).mapToObj(x -> predictorNames.get(x)).toArray(String[]::new);
        for (String coefName : _parms._random_columns) {
          String startCoef = coefName + ".";
          randomPredictorNames.addAll(allCoeffNames.stream().filter(x -> x.startsWith(startCoef) || x.equals(coefName)).collect(Collectors.toList()));
        }
      }
      if (_parms._random_intercept)
        randomPredictorNames.add("intercept");

      _randomCoeffNames = randomPredictorNames.stream().toArray(String[]::new);
      _numLevel2Units = _level2UnitNames.length;
      _numFixedCoeffs = _fixedCoeffNames.length;
      _numRandomCoeffs = _randomCoeffNames.length;
      setPredXInterceptIndices(predictorNames);
    }

    @Override
    public void map(Chunk[] chks) {
      if(_job != null && _job.stop_requested()) return;
      initializeArraysVar();
      double y;
      double[] xji = MemoryManager.malloc8d(_numFixedCoeffs);
      double[] zji = MemoryManager.malloc8d(_numRandomCoeffs);
      int level2Index;
      int chkLen = chks[0].len();
      DataInfo.Row r = _dinfo.newDenseRow();
      for (int rowInd = 0; rowInd < chkLen; rowInd++) {
        _dinfo.extractDenseRow(chks, rowInd, r);
        if (!r.isBad() && !(r.weight == 0)) {
          y = r.response(0);
          _YjTYjSum += y * y;
          _nobs++;
          _weightedSum += r.weight;
          level2Index = _parms._use_all_factor_levels ? r.binIds[_level2UnitIndex] - _dinfo._catOffsets[_level2UnitIndex] :
                  (int) chks[_level2UnitIndex].at8(rowInd);
          fillInFixedRowValues(r, xji, _parms, _fixedCatIndices, _level2UnitIndex, _numLevel2Units, 
                  _predStartIndexFixed, _dinfo); // read in predictors for fixed coefficient effects
          fillInRandomRowValues(r, zji, _parms, _randomCatIndices, _randomNumIndices, _randomCatArrayStartIndices, 
                  _predStartIndexRandom, _dinfo, _randomSlopeToo, _parms._random_intercept); // read in random coefficient effects
          formFixedMatricesVectors(level2Index, xji, y, _AfjTYj, _AfjTAfj); // form _AfjTYj, _AfjTAfj
          formFixedMatricesVectors(level2Index, zji, y, _ArjTYj, _ArjTArj); // form ArjTYj, _ArjTArj
          outerProductCum(_AfjTArj[level2Index], xji, zji); // form AfjTArj
        }
      }
    }

    /**
     * It does two things:
     * a. form output product of one row of data set (matMat[level2Ind])
     * b. form product of one row of data and response y.
     */
    void formFixedMatricesVectors(int level2Ind, double[] xji, double y, double[][] matVec, double[][][] matMat) {
      outputProductSymCum(matMat[level2Ind], xji);
      multCum(xji, matVec[level2Ind], y);
    }

    static void fillInRandomRowValues(DataInfo.Row r, double[] zji, HGLMModel.HGLMParameters parms, 
                                      int[] randomCatIndices, int[] randomNumIndices, int[] randomCatArrayStartIndices, 
                                      int predStartIndexRandom, DataInfo dinfo, boolean randomSlopeToo, boolean randomIntercept) {
      // read in predictors for random coefficient effects
      Arrays.fill(zji, 0.0);
      int catPredInd;
      int startEnumInd = 0;
      int catVal;
      if (randomSlopeToo) {
        if (randomCatIndices != null) {
          for (int catInd = 0; catInd < randomCatIndices.length; catInd++) {
            catPredInd = randomCatIndices[catInd];
            catVal = r.binIds[catPredInd];
            if (!parms._use_all_factor_levels) {
              RowInfo rowInfo = grabCatIndexVal(r, startEnumInd, catPredInd, dinfo);
              catVal = rowInfo._catVal;
              startEnumInd = rowInfo._rowEnumInd;
            }
            if (catVal >= 0)
              zji[catVal - dinfo._catOffsets[catPredInd] + randomCatArrayStartIndices[catInd]] = 1;
          }
        }

        if (randomNumIndices != null)
          for (int numInd = 0; numInd < randomNumIndices.length; numInd++)
            zji[numInd + predStartIndexRandom] = r.numVals[randomNumIndices[numInd] - dinfo._cats];
      }
      
      if (randomIntercept)
        zji[zji.length - 1] = 1.0;
    }

    public static void fillInFixedRowValues(DataInfo.Row r, double[] xji, HGLMModel.HGLMParameters parms, int[] fixedCatIndices, 
                              int level2UnitIndex, int numLevel2Units, int predStartIndexFixed, DataInfo dinfo) {
      // read in predictors for fixed coefficient effects
      Arrays.fill(xji, 0.0);
      int startEnumInd = 0;
      int catPredInd;
      int catVal;
      if (r.nBins > 1) {  // will always have at least one enum column
        for (int catInd = 0; catInd < fixedCatIndices.length; catInd++) {
          catPredInd = fixedCatIndices[catInd];
          catVal = r.binIds[catPredInd];
          if (!parms._use_all_factor_levels) {
            RowInfo rowInfo = grabCatIndexVal(r, startEnumInd, catPredInd, dinfo);
            catVal = rowInfo._catVal;
            startEnumInd = rowInfo._rowEnumInd;
          }
          if (catVal > -1) {
            if (catPredInd < level2UnitIndex) {
              xji[catVal] = 1;
            } else if (catPredInd > level2UnitIndex) {
              xji[catVal - (parms._use_all_factor_levels ? numLevel2Units : (numLevel2Units - 1))] = 1;
            }
          }
        }
      }
      for (int numInd = 0; numInd < r.nNums; numInd++) {
        xji[numInd + predStartIndexFixed] = r.numVals[numInd];
      }
      xji[xji.length - 1] = 1.0;  // for intercept
    }

    public static RowInfo grabCatIndexVal(DataInfo.Row r, int startEnumInd, int enumIndexOfInterest, DataInfo dinfo) {
      int startInd = startEnumInd;
      for (int index = startEnumInd; index < r.nBins; index++) {
        if (dinfo._catOffsets[enumIndexOfInterest] <= r.binIds[index] && r.binIds[index] < dinfo._catOffsets[enumIndexOfInterest + 1])
          return new RowInfo(index, r.binIds[index]);

        if (r.binIds[index] >= dinfo._catOffsets[enumIndexOfInterest + 1])
          return new RowInfo(index, -1);
        startInd = index;
      }
      return new RowInfo(startInd, -1);
    }

    static class RowInfo {
      int _rowEnumInd;
      int _catVal;

      public RowInfo(int rowEnumInd, int catVal) {
        _rowEnumInd = rowEnumInd;
        _catVal = catVal;
      }
    }

    void initializeArraysVar() {
      _YjTYjSum = 0;
      _nobs = 0;
      _weightedSum = 0.0;
      _AfjTYj = MemoryManager.malloc8d(_numLevel2Units, _numFixedCoeffs);
      _ArjTYj = MemoryManager.malloc8d(_numLevel2Units, _numRandomCoeffs);
      _AfjTAfj = MemoryManager.malloc8d(_numLevel2Units, _numFixedCoeffs, _numFixedCoeffs);
      _ArjTArj = MemoryManager.malloc8d(_numLevel2Units, _numRandomCoeffs, _numRandomCoeffs);
      _AfjTArj = MemoryManager.malloc8d(_numLevel2Units, _numFixedCoeffs, _numRandomCoeffs);
    }

    @Override
    public void reduce(ComputationEngineTask otherTask) {
      _YjTYjSum += otherTask._YjTYjSum;
      _nobs += otherTask._nobs;
      _weightedSum += otherTask._weightedSum;
      add(_AfjTYj, otherTask._AfjTYj);
      add(_ArjTYj, otherTask._ArjTYj);
      add(_AfjTAfj, otherTask._AfjTAfj);
      add(_ArjTArj, otherTask._ArjTArj);
      add(_AfjTArj, otherTask._AfjTArj);
    }

    @Override
    public void postGlobal() {
      _ArjTAfj = new double[_numLevel2Units][][];
      _AfjTYjSum = MemoryManager.malloc8d(_numFixedCoeffs);
      _AfTAftInvAfjTYj = MemoryManager.malloc8d(_numFixedCoeffs);

      _oneOverJ = 1.0 / _numLevel2Units;
      _oneOverN = 1.0 / _nobs;

      double[][] sumAfjAfj = MemoryManager.malloc8d(_numFixedCoeffs, _numFixedCoeffs);
      sumAfjAfjAfjTYj(_AfjTAfj, _AfjTYj, sumAfjAfj, _AfjTYjSum);
      for (int index = 0; index < _numLevel2Units; index++)
        _ArjTAfj[index] = new Matrix(_AfjTArj[index]).transpose().getArray();

      _zTTimesZ = fillZTTimesZ(_ArjTArj);
      if (_parms._max_iterations > 0) { // only proceed if max_iterations is not zero        
        _AfTAftInv = (new Matrix(sumAfjAfj)).inverse().getArray();
        matrixVectorMult(_AfTAftInvAfjTYj, _AfTAftInv, _AfjTYjSum);
      }
    }
    
    public static void sumAfjAfjAfjTYj(double[][][] afjTAfj, double[][] afjTYj, double[][] sumAfjAfj, double[] sumAfjTYj) {
      int numLevel2 = afjTAfj.length;
      for (int index=0; index<numLevel2; index++) {
        add(sumAfjAfj, afjTAfj[index]);
        add(sumAfjTYj, afjTYj[index]);
      }
    }
  }
}
