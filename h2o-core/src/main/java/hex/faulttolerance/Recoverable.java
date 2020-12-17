package hex.faulttolerance;

import water.Key;
import water.Keyed;

import java.util.Set;

public interface Recoverable<T extends Keyed> {

    /**
     * @return key of this keyed object
     */
    Key<T> getKey();

    /**
     * @param location directory where this recoverable will be written into a single file
     * @return path to where data was written
     */
    String exportBinary(String location);

    /**
     * @return list of all keys of objects this recoverable needs to resume operation after recovery
     */
    Set<Key<?>> getDependentKeys();
    
}
