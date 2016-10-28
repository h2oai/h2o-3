package water.udf;

/**
 * Represents a single-argument function
 */
public interface Foldable<X, Y> extends java.io.Serializable {
  Y initial();
  Y apply(Y y, X x);
}
