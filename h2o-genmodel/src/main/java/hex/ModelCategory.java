package hex;

/** Different prediction categories for models.
 *
 * This code is shared between runtime models and generated models.
 *
 * NOTE: the values list in the API annotation ModelOutputSchema needs to match. */
public enum ModelCategory {
  Unknown,
  Binomial,
  Multinomial,
  Ordinal,
  Regression,
  HGLMRegression,
  Clustering,
  AutoEncoder,
  TargetEncoder,
  DimReduction,
  WordEmbedding,
  CoxPH,
  AnomalyDetection,
  KLime,
  BinomialUplift
}
