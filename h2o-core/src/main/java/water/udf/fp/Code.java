package water.udf.fp;

import java.io.Serializable;

/**
 * A marker interface for the classes that have no data inside
 * Meaning, two instances are equal if they have the same class
 * 
 * If you implement this interface, but want to pass around class members,
 * you should override hashCode() and equals()
 */
public interface Code extends Serializable {
}
