package hex.genmodel.algos.glm;

import hex.genmodel.MojoModel;

import java.util.HashMap;

abstract class GlmMojoModelBase extends MojoModel {

  boolean _useAllFactorLevels;
  public InteractionPair[] _interaction_pairs;
  HashMap<String, Integer[]> _interaction_mapping;
  HashMap<String, Integer> _column_mapping;

  int _cats;
  int[] _catModes;
  int[] _catOffsets;

  int _nums;
  double[] _numMeans;
  boolean _meanImputation;

  int[] _catNumOffsets;
  int _numNumOffset;
  int _numMeanOffset;
  int _numBetaOffset;
  
  double[] _beta;

  String _family;

  GlmMojoModelBase(String[] columns, String[][] domains, String responseColumn) {
    super(columns, domains, responseColumn);
  }

  void init() {
    mapColumns();
    mapInteractions();
    addCatNumOffsets();
    _numNumOffset = _catNumOffsets[_catNumOffsets.length - 1] - (_catNumOffsets.length - 1);
    // meanOffset represents index at which categorical-numerical means end and numerical means begin in numMeans 
    _numMeanOffset = _catNumOffsets[_catNumOffsets.length - 1] -_cats - (_catNumOffsets.length - 1);
    // noff is index at which numerical coefficients begin in beta array
    _numBetaOffset = _catOffsets[_catOffsets.length - 1] + _catNumOffsets[_catNumOffsets.length - 1];
  }

  @Override
  public final double[] score0(double[] data, double[] preds) {
    if (_meanImputation)
      imputeMissingWithMeans(data);

    return glmScore0(data, preds);
  }

  abstract double[] glmScore0(double[] data, double[] preds);

  private void imputeMissingWithMeans(double[] data) {
    for (int i = 0; i < _cats; ++i) {
      if (Double.isNaN(data[i])) {
        if (isInteraction(_names[i])) { // Interaction columns will always be read in as NaN
          imputeInteraction(data, i);
        } else {
          data[i] = _catModes[i];
        }
      }
    }
    for (int i = 0; i < _nums; ++i) {
      if (Double.isNaN(data[i + _cats])) {
        if (isInteraction(_names[i + _cats])) { // Interaction columns will always be read in as NaN
          imputeInteraction(data, i + _cats);
        } else {
          data[i + _cats] = _numMeans[i + _numNumOffset];  
        }
      } 
    }
  }

  private void mapColumns() {
    _column_mapping = new HashMap<String, Integer>();
    for (int i = 0; i < _names.length; i++) {
      _column_mapping.put(_names[i], i);
    }
  }
  
  private void mapInteractions() {
    if (_interaction_pairs == null) {
      _interaction_mapping = null;
      return;
    }
    
    _interaction_mapping = new HashMap<>();
    for (InteractionPair pair: _interaction_pairs) {
      int index1 = _column_mapping.get(pair.columnA);
      int index2 = _column_mapping.get(pair.columnB);
      _interaction_mapping.put((pair.columnA + "_" + pair.columnB), new Integer[]{index1, index2});
    }
  }

  /**
   * Creates array that gives offset of each categorical-numerical interaction column, relative to the first
   * categorical-numerical interaction column. First interaction column has offset 0, each offset is then
   * determined by the domain length of the categorical variable in the subsequent interaction columns.
   * Array is used to determine correct value of beta to use when scoring categorical-numerical interaction column
   * and to impute the correct value into the categorical-numerical interaction columns when reading in data.
   */
  private void addCatNumOffsets() {
    int catNumInteractions = 0;
    for (int i = _cats; i < _domains.length - 1; i++) {
      if (_domains[i] == null) {break;}
      catNumInteractions++;
    }
    _catNumOffsets = new int[catNumInteractions + 1];
    _catNumOffsets[0] = 0;
    for (int i = 0; i < catNumInteractions; i++) {
      _catNumOffsets[i + 1] = _catNumOffsets[i] + _domains[_cats + i].length;
    }
  }
  
  private boolean isInteraction(String name) {
    if(_interaction_pairs == null) {return false;}
    return _interaction_mapping.containsKey(name);
  }
  
  
  private void imputeInteraction(double[] data, int index) {
    Integer[] pair = _interaction_mapping.get(_names[index]);
    // Impute categorical-categorical interaction
    if (_domains[pair[0]] != null && _domains[pair[1]] != null) {
      // If either categorical value in the pair is NaN, use interaction columns mode to impute
      if(Double.isNaN(data[pair[0]]) || Double.isNaN(data[pair[1]])) {
        data[index] = _catModes[index];
      } else {
        // Computes interaction value based on enum level of respective categorical values
        data[index] = (_domains[pair[0]].length * data[pair[0]]) + data[pair[1]];  
      }
    }
    // Impute numerical-numerical interaction
    else if (_domains[pair[0]] == null && _domains[pair[1]] == null) {
      if(Double.isNaN(data[pair[0]]) || Double.isNaN(data[pair[1]])) {
        // index - _cats - (_catNumOffsets.length - 1) says which numerical column is being used
        data[index] = index + _numMeanOffset;
      } else {
        data[index] = data[pair[0]] * data[pair[1]];  
      }
    }
    // Impute categorical-numerical interaction
    else {
      int cat_index = _domains[pair[0]] == null ? pair[1] : pair[0];
      int num_index = _domains[pair[0]] == null ? pair[0] : pair[1];
      if (Double.isNaN(data[num_index])) {
        // index - _cats says which categorical-numerical column is being used
        // meanOffset is index where that categorical-numerical column's means begin in numMeans
        int meanOffset = _catNumOffsets[index - _cats];
        // Each categorical-numerical column has multiple means based on enum level
        // (int)data[cat_index] gets enum level - this is used as the offset to get the corresponding mean
        data[index] = _numMeans[meanOffset + (int)data[cat_index]];
      } else {
        data[index] = data[num_index];
      }
    }
  }
  
  @Override
  public String[] getOutputNames() {
    // special handling of binomial case where response domain is not represented
    if (nclasses() == 2 && getDomainValues(getResponseIdx()) == null) {
      return new String[]{"predict", "0", "1"};
    }
    return super.getOutputNames();
  }
}
