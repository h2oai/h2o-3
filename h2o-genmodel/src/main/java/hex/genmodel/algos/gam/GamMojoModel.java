package hex.genmodel.algos.gam;

import static hex.genmodel.utils.DistributionFamily.*;

public class GamMojoModel extends GamMojoModelBase {
  private boolean _classifier;
  
  GamMojoModel(String[] columns, String[][] domains, String responseColumn) {
    super(columns, domains, responseColumn);
  }
  
  void init() {
    super.init();
    _classifier = _family.equals(bernoulli) || _family.equals(fractionalbinomial) || _family.equals(quasibinomial);
  }
  
  // generate prediction for binomial/fractional binomial/negative binomial, poisson, tweedie families
  @Override
  double[] gamScore0(double[] data, double[] preds) {
    double eta = generateEta(_beta_center, data);  // generate eta, inner product of beta and data
    double mu = evalLink(eta);
    if (_classifier) {
      preds[0] = (mu >= _defaultThreshold) ? 1 : 0; // threshold given by ROC
      preds[1] = 1.0 - mu; // class 0
      preds[2] = mu; // class 1
    } else {
      preds[0] = mu;
    }
    return preds;
  }
}
