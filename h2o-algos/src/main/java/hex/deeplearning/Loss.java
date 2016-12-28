package hex.deeplearning;

/**
 * Loss functions
 * Absolute, Quadratic, Huber, Quantile for regression
 * Quadratic, ModifiedHuber or CrossEntropy for classification
 */
public enum Loss {
  Automatic, Quadratic, CrossEntropy, ModifiedHuber, Huber, Absolute, Quantile
}
