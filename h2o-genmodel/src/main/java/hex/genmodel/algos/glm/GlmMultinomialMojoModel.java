package hex.genmodel.algos.glm;

public class GlmMultinomialMojoModel extends GlmMojoModelBase {

  private int P;
  private int noff;

  GlmMultinomialMojoModel(String[] columns, String[][] domains) {
    super(columns, domains);
  }

  @Override
  void init() {
    P = _beta.length / _nclasses;
    if (P * _nclasses != _beta.length)
      throw new IllegalStateException("Incorrect coding of Beta.");
    noff = _catOffsets[_cats];
  }

  @Override
  double[] glmScore0(double[] data, double[] preds) {
    preds[0] = 0;
    for (int c = 0; c < _nclasses; ++c) {
      preds[c + 1] = 0;
      if (_cats > 0) {
        if (! _useAllFactorLevels) { // skip level 0 of all factors
          for (int i = 0; i < _catOffsets.length-1; ++i) if(data[i] != 0) {
            int ival = (int) data[i] - 1;
            if (ival != data[i] - 1) throw new IllegalArgumentException("categorical value out of range");
            ival += _catOffsets[i];
            if (ival < _catOffsets[i + 1])
              preds[c + 1] += _beta[ival + c*P];
          }
        } else { // do not skip any levels
          for(int i = 0; i < _catOffsets.length-1; ++i) {
            int ival = (int) data[i];
            if (ival != data[i]) throw new IllegalArgumentException("categorical value out of range");
            ival += _catOffsets[i];
            if(ival < _catOffsets[i + 1])
              preds[c + 1] += _beta[ival + c*P];
          }
        }
      }
      for (int i = 0; i < _nums; ++i)
        preds[c+1] += _beta[noff+i + c*P]*data[i];
      preds[c+1] += _beta[(P-1) + c*P]; // reduce intercept
    }
    double max_row = 0;
    for (int c = 1; c < preds.length; ++c) if (preds[c] > max_row) max_row = preds[c];
    double sum_exp = 0;
    for (int c = 1; c < preds.length; ++c) { sum_exp += (preds[c] = Math.exp(preds[c]-max_row));}
    sum_exp = 1/sum_exp;
    double max_p = 0;
    for (int c = 1; c < preds.length; ++c) if ((preds[c] *= sum_exp) > max_p) { max_p = preds[c]; preds[0] = c-1; }
    return preds;
  }

}
