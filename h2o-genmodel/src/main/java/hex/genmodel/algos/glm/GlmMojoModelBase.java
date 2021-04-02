package hex.genmodel.algos.glm;

import com.sun.corba.se.spi.orb.StringPair;
import hex.genmodel.MojoModel;

import java.util.HashMap;
import java.util.stream.IntStream;

abstract class GlmMojoModelBase extends MojoModel {

  boolean _useAllFactorLevels;
  public StringPair[] _interaction_pairs;
  HashMap<String, Integer[]> _interaction_mapping;

  int _cats;
  int[] _catModes;
  int[] _catOffsets;

  int _nums;
  double[] _numMeans;
  boolean _meanImputation;

  double[] _beta;

  String _family;

  GlmMojoModelBase(String[] columns, String[][] domains, String responseColumn) {
    super(columns, domains, responseColumn);
    mapInteractions();
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

  private void mapInteractions() {
    if (_interaction_pairs == null) {
      _interaction_mapping = null;
      return;
    }
    
    _interaction_mapping = new HashMap<String, Integer[]>();
    for (StringPair pair: _interaction_pairs) {
      int index1 = IntStream.range(0, _names.length)
              .filter(i -> pair.getFirst() == _names[i])
              .findFirst()
              .orElse(-1);
      int index2 = IntStream.range(0, _names.length)
              .filter(i -> pair.getSecond() == _names[i])
              .findFirst()
              .orElse(-1);
      _interaction_mapping.put((pair.getFirst() + "_" + pair.getSecond()), new Integer[]{index1, index2});
    }
  }
  
  private boolean isInteraction(String name) {
    if(_interaction_pairs == null) {return false;}
    return _interaction_mapping.containsKey(name);
  }
  
  private void imputeInteraction(double[] data, int index) {
    Integer[] pairs = _interaction_mapping.get(_names[index]);
    if (_domains[pairs[0]] != null && _domains[pairs[1]] != null) {
      data[index] = (_domains[pairs[0]].length * data[pairs[0]]) + data[pairs[1]] - 1;
    } else if (_domains[pairs[0]] == null && _domains[pairs[1]] == null) {
      data[index] = data[pairs[0]] * data[pairs[1]];
    } else {
      data[index] = _domains[pairs[0]] == null ? data[pairs[0]] : data[pairs[1]];  
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
