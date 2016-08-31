package hex.genmodel.utils;

import java.util.Map;

import static hex.genmodel.utils.Distribution.Family.bernoulli;
import static hex.genmodel.utils.Distribution.Family.modified_huber;
import static hex.genmodel.utils.Distribution.Family.multinomial;
import static java.lang.Math.exp;

/**
 * Copy of `hex.Distribution.Family`.
 */
public class Distribution {
  private Family _family;

  public enum Family {
    AUTO,           // model-specific behavior
    bernoulli,      // binomial classification (nclasses == 2)
    modified_huber, // modified huber: quadratically smoothed hinge loss for 0/1 outcome
    multinomial,    // classification (nclasses >= 2)
    gaussian,       // regression
    poisson,
    gamma,
    tweedie,
    huber,
    laplace,
    quantile
  }

  public Distribution(Map<String, Object> info) {
    _family = Family.valueOf((String) info.get("distribution"));
  }

  public Family family() { return _family; }
  public boolean isMultinomial() { return _family == multinomial; }
  public boolean isBernoulliOrModhuber() { return _family == bernoulli || _family == modified_huber; }


  public double linkInv(double f) {
    switch (_family) {
      case AUTO:
      case gaussian:
      case huber:
      case laplace:
      case quantile:
        return f;
      case modified_huber:
      case bernoulli:
        return 1 / (1 + exp(-f));
      case multinomial:
      case poisson:
      case gamma:
      case tweedie:
        return exp(f);
      default:
        throw new UnsupportedOperationException("Unknown family " + _family);
    }
  }

}
