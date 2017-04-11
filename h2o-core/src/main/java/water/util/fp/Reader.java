package water.util.fp;

import java.io.Serializable;

/**
 * Represents a zero-argument function (that is, not a function, but a reader)
 * 
 * @see <a href="http://stackoverflow.com/questions/14178889/what-is-the-purpose-of-the-reader-monad">Stackoverflow</a> for details.
 * 
 */
public interface Reader<X> extends Serializable {
  X read();
}
