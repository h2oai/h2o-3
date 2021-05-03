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
    super.init();
    _linkFn = createLinkFunction();
    _binomial = _family.equals("binomial");
  }

  @Override
  double[] glmScore0(double[] data, double[] preds) {
    double eta = 0.0;

    if (!_useAllFactorLevels) { // skip level 0 of all factors
      for(int i = 0; i < _catOffsets.length-1; ++i) {
        if(data[i] != 0) {
          int ival = (int) data[i] - 1;
          if (ival != data[i] - 1) {
            throw new IllegalArgumentException("categorical value out of range");
          }
          ival += _catOffsets[i];
          if (ival < _catOffsets[i + 1]) {
            eta += _beta[ival];
          }
        }
      }
    } else { // do not skip any levels
      for(int i = 0; i < _catOffsets.length-1; ++i) {
        int ival = (int) data[i];
        if (ival != data[i]) {
          throw new IllegalArgumentException("categorical value out of range");
        }
        ival += _catOffsets[i];
        if (ival < _catOffsets[i + 1]) {
          eta += _beta[ival];
        }
      }
    }

    // Categorical-Numerical interaction scoring
    
    for (int i = 0; i < _catNumOffsets.length - 1; ++i) {
      Integer[] interaction = _interaction_mapping.get(_names[_cats + i]);
      // catOffset is index at which categorical-numerical interaction coefficients begin in beta array
      int catOffset = _catOffsets[_catOffsets.length - 1];
      int cat_index = _domains[interaction[0]] == null ? 1 : 0;
      int num_index = _domains[interaction[0]] == null ? 0 : 1;
      int enum_level = (int) data[interaction[cat_index]];
      if (enum_level != data[interaction[cat_index]]) {
        throw new IllegalArgumentException("categorical value out of range");
      }
      if(!_useAllFactorLevels && enum_level == 0) {continue;}
      // _catNumOffsets[i] + enum_level gets the offset for that particular categorical-numerical interaction column
      // within the categorical-numerical interaction portion of beta array
      eta += _beta[catOffset + _catNumOffsets[i] + enum_level] * data[interaction[num_index]];
    }
    
    // Numerical interaction scoring
    
    // noff is index at which numerical coefficients begin in beta array
    int noff = _catOffsets[_catOffsets.length - 1] + _catNumOffsets[_catNumOffsets.length - 1];
    for(int i = 0; i < _nums - _catNumOffsets.length - 1; ++i) {
      eta += _beta[noff + i] * data[i + _cats + _catNumOffsets.length - 1];
    }
    eta += _beta[_beta.length - 1]; // reduce intercept

    double mu = _linkFn.eval(eta);

    if (_binomial || _family.equals("fractionalbinomial")) {
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
