package hex.genmodel.algos.glm;

import hex.genmodel.GenModel;

import java.io.Serializable;

public class GlmMojoModel extends GlmMojoModelBase {

  String _link;
  double _tweedieLinkPower;

  // set by init()
  private Function1 _linkFn;
  private boolean _binomial;

  GlmMojoModel(String[] columns, String[][] domains, String responseColumn) {
    super(columns, domains, responseColumn);
  }

  @Override
  void init() {
    _linkFn = createLinkFunction();
    _binomial = _family.equals("binomial");
  }

  @Override
  double[] glmScore0(double[] data, double[] preds) {
    double eta = 0.0;

    if (!_useAllFactorLevels) { // skip level 0 of all factors
      for(int i = 0; i < _catOffsets.length-1; ++i) if(data[i] != 0) {
        int ival = (int) data[i] - 1;
        if (ival != data[i] - 1) throw new IllegalArgumentException("categorical value out of range");
        ival += _catOffsets[i];
        if (ival < _catOffsets[i + 1])
          eta += _beta[ival];
      }
    } else { // do not skip any levels
      for(int i = 0; i < _catOffsets.length-1; ++i) {
        int ival = (int) data[i];
        if (ival != data[i]) throw new IllegalArgumentException("categorical value out of range");
        ival += _catOffsets[i];
        if (ival < _catOffsets[i + 1])
          eta += _beta[ival];
      }
    }

    int noff = _catOffsets[_cats] - _cats;
    for(int i = _cats; i < _beta.length - 1 - noff; ++i)
      eta += _beta[noff + i] * data[i];
    eta += _beta[_beta.length - 1]; // reduce intercept

    double mu = _linkFn.eval(eta);

    if (_binomial) {
      preds[0] = (mu >= _defaultThreshold) ? 1 : 0; // threshold given by ROC
      preds[1] = 1.0 - mu; // class 0
      preds[2] =       mu; // class 1
    } else {
      preds[0] = mu;
    }

    return preds;
  }

  /**
   * Applies GLM coefficients to a given row of data to calculate
   * feature contributions.
   *
   * Note: for internal purposes only (k-LIME)
   *
   * @param data input row of data (same input as to glmScore0)
   * @param output target output array
   * @param destPos index to the output array where the result should start
   * @return feature contributions, prediction = linkFunction(sum(output) + intercept)
   */
  public double[] applyCoefficients(double[] data, double[] output, int destPos) {
    final int offset = _useAllFactorLevels ? 0 : -1;
    for (int i = 0; i < _catOffsets.length - 1; i++) {
      int ival = (int) data[i] - offset;
      if (ival < 0) continue;
      ival += _catOffsets[i];
      if (ival < _catOffsets[i + 1])
        output[i + destPos] = _beta[ival];
    }
    int p = destPos + _catOffsets.length - 1;
    int noff = _catOffsets[_cats] - _cats;
    for (int i = _cats; i < _beta.length - 1 - noff; i++)
      output[p++] = _beta[noff + i] * data[i];
    return output;
  }

  public double getIntercept() {
    return _beta[_beta.length - 1];
  }

  private interface Function1 extends Serializable {
    double eval(double x);
  }

  private Function1 createLinkFunction() {
    if ("identity".equals(_link))
      return new GLM_identityInv();
    else if ("logit".equals(_link))
      return new GLM_logitInv();
    else if ("log".equals(_link))
      return new GLM_logInv();
    else if ("inverse".equals(_link))
      return new GLM_inverseInv();
    else if ("tweedie".equals(_link))
      return new GLM_tweedieInv(_tweedieLinkPower);
    else
      throw new UnsupportedOperationException("Unexpected link function " + _link);
  }

  private static class GLM_identityInv implements Function1 {
    @Override public double eval(double x) { return GenModel.GLM_identityInv(x); }
  }
  private static class GLM_logitInv implements Function1 {
    @Override public double eval(double x) { return GenModel.GLM_logitInv(x); }
  }
  private static class GLM_logInv implements Function1 {
    @Override public double eval(double x) { return GenModel.GLM_logInv(x); }
  }
  private static class GLM_inverseInv implements Function1 {
    @Override
    public double eval(double x) {
      return GenModel.GLM_inverseInv(x);
    }
  }

    private static class GLM_ologitInv implements Function1 {
      @Override public double eval(double x) { return GenModel.GLM_ologitInv(x); }
  }
  private static class GLM_tweedieInv implements Function1 {
    private final double _tweedie_link_power;
    GLM_tweedieInv(double tweedie_link_power) { this._tweedie_link_power = tweedie_link_power; }
    @Override public double eval(double x) { return GenModel.GLM_tweedieInv(x, _tweedie_link_power); }
  }

}
