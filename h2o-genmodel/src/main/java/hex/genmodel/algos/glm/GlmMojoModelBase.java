package hex.genmodel.algos.glm;

import com.sun.corba.se.spi.orb.StringPair;
import hex.genmodel.MojoModel;

import java.util.HashMap;
import java.util.stream.IntStream;

abstract class GlmMojoModelBase extends MojoModel {

  boolean _useAllFactorLevels;
  public StringPair[] _interaction_pairs;
  HashMap<String, Integer[]> _interaction_mapping;
  HashMap<String, Integer> _column_mapping;

  int _cats;
  int[] _catModes;
  int[] _catOffsets;

  int _nums;
  double[] _numMeans;
  boolean _meanImputation;

  int[] _catNumOffsets;
  
  double[] _beta;

  String _family;

  GlmMojoModelBase(String[] columns, String[][] domains, String responseColumn) {
    super(columns, domains, responseColumn);
    mapColumns();
    mapInteractions();
    addCatNumOffsets();
  }

  void init() { /* do nothing by default */ }

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
        if (isInteraction(_names[i])) {
          imputeInteraction(data, i);
        } else {
          data[i] = _catModes[i];
        }
      }
    }
    for (int i = 0; i < _nums; ++i) {
      if (Double.isNaN(data[i + _cats])) {
        if (isInteraction(_names[i + _cats])) {
          imputeInteraction(data, i + _cats);
        } else {
          data[i + _cats] = _numMeans[i];  
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
    
    _interaction_mapping = new HashMap<String, Integer[]>();
    for (StringPair pair: _interaction_pairs) {
      int index1 = _column_mapping.get(pair.getFirst());
      int index2 = _column_mapping.get(pair.getSecond());
      _interaction_mapping.put((pair.getFirst() + "_" + pair.getSecond()), new Integer[]{index1, index2});
    }
  }
  
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
    if (_domains[pair[0]] != null && _domains[pair[1]] != null) {
      if(Double.isNaN(data[pair[0]]) || Double.isNaN(data[pair[1]])) {
        data[index] = _catModes[index];
      } else {
        data[index] = (_domains[pair[0]].length * data[pair[0]]) + data[pair[1]] - 1;  
      }
    } else if (_domains[pair[0]] == null && _domains[pair[1]] == null) {
      if(Double.isNaN(data[pair[0]]) || Double.isNaN(data[pair[1]])) {
        int meanOffset = _catNumOffsets[_catNumOffsets.length - 1];
        data[index] = _numMeans[meanOffset + (index - _cats - (_catNumOffsets.length - 1))];
      } else {
        data[index] = data[pair[0]] * data[pair[1]];  
      }
    } else {
      int cat_index = _domains[pair[0]] == null ? 1 : 0;
      int num_index = _domains[pair[0]] == null ? 0 : 1;
      if (Double.isNaN(data[num_index])) {
        int meanOffset = _catNumOffsets[index - _cats];
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
