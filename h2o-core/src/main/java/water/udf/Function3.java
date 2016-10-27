package water.udf;

/**
 * Represents a three-argument function
 */
public interface Function3<X, Y, Z, T> extends java.io.Serializable {
  T apply(X x, Y y, Z z);
}
