package hex.hglm;

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

/***
 * This class will pre-calculate arrays (double[]) or matrices (double[][]) that will be used in later calculations.
 *
 */
public class ComputationEngineTask extends MRTask<ComputationEngineTask> {
  double _YjTYjSum;   // calculate sum of transpose(Yj)*Yj across all level 2 units
  double[][] _AfjTYj; // calculate transpose(Afj)*Yj for each level 2 unit, Y
  double[][] _ArjTYj;
  double[][][] _AfjTAfj;
  double[][][] _ArjTArj;
  double[][][] _AfjTArj;
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
  int[] _fixedPredXInterceptIndices;
  int[] _fixedCatIndices;
  int[] _fixedNumIndices;
  String[] _fixedPredNames;
  String[] _randomPredNames;
  Job _job;
  
  public ComputationEngineTask(HGLMModel.HGLMParameters parms, DataInfo dinfo, Job job) {
    _job = job;
    _parms = parms;
    _dinfo = dinfo;
    extractNamesNIndices();
  }
  
  void setPredXInterceptIndices(List<String> predictorNames) {
    _randomPredXInterceptIndices = new int[_parms._random_columns.length];
    List<String> fixedPredNames = new ArrayList<>();
    List<String> randomPredNames = new ArrayList<>();
    
    List<Integer> randomCatPredList = new ArrayList<>();
    List<Integer> randomNumPredList = new ArrayList<>();
    _fixedPredXInterceptIndices = new int[predictorNames.size()-1];
    List<Integer> fixedCatPredList = new ArrayList<>();
    List<Integer> fixedNumPredList = new ArrayList<>();
    for (int index=0; index<_randomPredXInterceptIndices.length; index++) {
      _randomPredXInterceptIndices[index] = predictorNames.indexOf(_parms._random_columns[index]);
      if (_randomPredXInterceptIndices[index] < _dinfo._cats)
        randomCatPredList.add(_randomPredXInterceptIndices[index]);
      else
        randomNumPredList.add(_randomPredXInterceptIndices[index]);
      randomPredNames.add(predictorNames.get(_randomPredXInterceptIndices[index]));
    }
    if (randomCatPredList.size() > 0) {
      _randomCatIndices = randomCatPredList.stream().mapToInt(x -> x).toArray();
      Arrays.sort(_randomCatIndices);
    }
    if (randomNumPredList.size() > 0) {
      _randomNumIndices = randomNumPredList.stream().mapToInt(x -> x).toArray();
      Arrays.sort(_randomNumIndices);
    }
    for (int index=0; index<_fixedPredXInterceptIndices.length; index++) {
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
  }
  
  void extractNamesNIndices() {
    List<String> predictorNames = Arrays.stream(_dinfo._adaptedFrame.names()).collect(Collectors.toList());
    _level2UnitIndex = predictorNames.indexOf(_parms._group_column);
    
    // assign coefficient names for fixed, random and group column
    List<String> allCoeffNames = Arrays.stream(_dinfo.coefNames()).collect(Collectors.toList());
    String groupCoeffStarts = _parms._group_column+".";
    List<String> groupCoeffNames = allCoeffNames.stream().filter(x -> x.startsWith(groupCoeffStarts)).collect(Collectors.toList());
    _level2UnitNames = groupCoeffNames.stream().toArray(String[]::new);

    // fixed Coefficients are all coefficient names excluding group_column
    List<String> fixedCoeffNames = allCoeffNames.stream().filter(x -> !groupCoeffNames.contains(x)).collect(Collectors.toList());
    fixedCoeffNames.add("intercept");
    _fixedCoeffNames = fixedCoeffNames.stream().toArray(String[]::new);
    List<String> randomPredictorNames = new ArrayList<>();
    // random coefficients names
    int[] randomColumnsIndicesSorted = Arrays.stream(_parms._random_columns).mapToInt(x -> predictorNames.indexOf(x)).toArray();
    Arrays.sort(randomColumnsIndicesSorted);
    _parms._random_columns = Arrays.stream(randomColumnsIndicesSorted).mapToObj(x -> predictorNames.get(x)).toArray(String[]::new);
    for (String coefName :  _parms._random_columns) {
      String startCoef = coefName + ".";
      randomPredictorNames.addAll(allCoeffNames.stream().filter(x -> x.startsWith(startCoef) || x.equals(coefName)).collect(Collectors.toList()));
    }
    _randomCoeffNames = randomPredictorNames.stream().toArray(String[]::new);
    _numLevel2Units = _level2UnitNames.length;
    _numFixedCoeffs = _fixedCoeffNames.length;
    _numRandomCoeffs = _randomCoeffNames.length;
    setPredXInterceptIndices(predictorNames);
  }
  
  @Override
  public void map(Chunk[] chks) {
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
        _YjTYjSum += y*y;
        _nobs++;
        _weightedSum += r.weight;
        level2Index = r.binIds[_level2UnitIndex];
        for (int catInd = 0; catInd < r.nBins; catInd++) {
          
        }
        for (int numInd = 0; numInd < r.nNums; numInd++) {
          
        }
      }
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
    ArrayUtils.add(_AfjTYj, otherTask._AfjTYj);
    ArrayUtils.add(_ArjTYj, otherTask._ArjTYj);
    ArrayUtils.add(_AfjTAfj, otherTask._AfjTAfj);
    ArrayUtils.add(_ArjTArj, otherTask._ArjTArj);
    ArrayUtils.add(_AfjTArj, otherTask._AfjTArj);
  }
}
