package water.functional;

/**
 * Represents a single-argument function
 */
public interface Function<X, Y> extends java.io.Serializable {
  public Y apply(X x);
}
