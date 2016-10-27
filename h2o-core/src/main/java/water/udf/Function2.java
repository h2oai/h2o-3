package water.udf;

/**
 * Represents a two-argument function
 */
public interface Function2<X, Y, Z> extends java.io.Serializable {
  Z apply(X x, Y y);
}
