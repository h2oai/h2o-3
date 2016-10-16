package water.functional;

/**
 * Represents a three-argument function
 */
public interface Function3<X, Y, Z, T> extends java.io.Serializable {
  public T apply(X x, Y y, Z z);
}
