package hex.genmodel.algos.glm;

import hex.genmodel.MojoModel;

abstract class GlmMojoModelBase extends MojoModel {

  boolean _useAllFactorLevels;

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
    for (int i = 0; i < _cats; ++i)
      if (Double.isNaN(data[i])) data[i] = _catModes[i];
    for (int i = 0; i < _nums; ++i)
      if (Double.isNaN(data[i + _cats])) data[i + _cats] = _numMeans[i];
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
