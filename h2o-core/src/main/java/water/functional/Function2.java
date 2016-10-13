package water.functional;

/**
 * Represents a two-argument function
 */
public interface Function2<X, Y, Z> extends java.io.Serializable {
  public Z apply(X x, Y y);
}
