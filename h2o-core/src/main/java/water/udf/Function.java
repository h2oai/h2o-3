package water.udf;

/**
 * Represents a single-argument function
 */
public interface Function<X, Y> extends java.io.Serializable {
  Y apply(X x);
}
