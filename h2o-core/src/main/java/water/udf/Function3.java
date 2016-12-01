package water.udf;

/**
 * Represents a three-argument function
 *
 * We could as well use Google guava library, but Guava's functions are not serializable.
 * We need serializable functions, to be able to pass them over the cloud.
 *
 * A three-argument function, in abstract settings, is something that takes values of given type (X, Y and Z) and returns a value of a given type (T). 
 * @see <a href="https://en.wikipedia.org/wiki/Function_(mathematics)">wikipedia</a> for details.
 */
public interface Function3<X, Y, Z, T> extends JustCode {
  T apply(X x, Y y, Z z);
}
