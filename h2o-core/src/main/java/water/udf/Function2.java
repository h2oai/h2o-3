package water.udf;

/**
 * Represents a two-argument function
 *
 * We could as well use Google guava library, but Guava's functions are not serializable.
 * We need serializable functions, to be able to pass them over the cloud.
 *
 * A two-argument function, in abstract settings, is something that takes values of given type (X and Y) and returns a value of a given type (Z). 
 * @see <a href="https://en.wikipedia.org/wiki/Function_(mathematics)">wikipedia</a> for details.
 */
public interface Function2<X, Y, Z> extends JustCode {
  Z apply(X x, Y y);
}
