package hex.tree.dt;

/**
 * Limits for one feature.
 */
public abstract class AbstractFeatureLimits {

  public abstract AbstractFeatureLimits clone();

  public abstract double[] toDoubles();
  
  public abstract boolean equals(AbstractFeatureLimits other);
}
