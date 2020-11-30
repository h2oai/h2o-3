package hex.genmodel.utils;

/**
 * Used to be `hex.Distribution.Family`.
 * NOTE: The moving to hex.DistributionFamily is not possible without resolving dependencies between 
 * h2o-genmodel and h2o-algos project
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
  quantile,
  fractionalbinomial,
  negativebinomial,
  custom
}
