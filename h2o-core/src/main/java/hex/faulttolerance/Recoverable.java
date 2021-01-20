package hex.faulttolerance;

import hex.ModelExportOption;
import water.Key;
import water.Keyed;

import java.util.List;
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
    List<String> exportBinary(String location, boolean includingModels, ModelExportOption... options);

    /**
     * @return list of all keys of objects this recoverable needs to resume operation after recovery
     */
    Set<Key<?>> getDependentKeys();
    
}
