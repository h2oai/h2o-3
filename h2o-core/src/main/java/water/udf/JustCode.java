package water.udf;

import java.io.Serializable;

/**
 * A marker interface for the classes that have no data inside
 * Meaning, two instances are equal if they have the same class
 */
public interface JustCode extends Serializable {
}
