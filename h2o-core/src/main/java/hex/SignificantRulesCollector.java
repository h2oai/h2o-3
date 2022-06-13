package hex;

import water.fvec.Frame;
import water.util.TwoDimTable;

/**
 * Implementors of this interface have significant rules collection implemented.
 */
public interface SignificantRulesCollector {
    
    TwoDimTable getRuleImportanceTable();
}
