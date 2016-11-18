package water.udf;

/**
 * Represents a single-argument function
 * 
 * We could as well use Google guava library, but Guava's functions are not serializable.
 * We need serializable functions, to be able to pass them over the cloud.
 * 
 * A function, in abstract settings, is something that takes a value of a given type (X) and 
 * returns a value of (another) given type (Y). 
 * @see <a href="https://en.wikipedia.org/wiki/Function_(mathematics)">wikipedia</a> for details.
 * 
 */
public interface Function<X, Y> extends JustCode {
  Y apply(X x);
}
