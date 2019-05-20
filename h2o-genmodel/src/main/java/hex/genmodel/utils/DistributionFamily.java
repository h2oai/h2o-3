package hex.genmodel.utils;


/**
 * Used to be `hex.Distribution.Family`.
 */
public enum DistributionFamily {
  AUTO,  // model-specific behavior
  bernoulli,
  quasibinomial,
  modified_huber,
  multinomial,
  ordinal,
  gaussian,
  poisson,
  gamma,
  tweedie,
  huber,
  laplace,
  quantile;
}
