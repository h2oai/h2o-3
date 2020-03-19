package hex.tree.isoforextended;

import hex.tree.SharedTreeModel;

public class ExtendedIsolationForestParameters extends SharedTreeModel.SharedTreeParameters {
    
    @Override
    public String algoName() {
        return "ExtendedIsolationForest";
    }

    @Override
    public String fullName() {
        return "Extended Isolation Forest";
    }

    @Override
    public String javaName() {
        return ExtendedIsolationForestModel.class.getName();
    }
    
    // Maximum is N - 1 (N = numCols). Minimum is 0. EIF with extension_level = 0 behaves like Isolation Forest.
    public int extensionLevel;
    
    public int sampleSize = 256;
}
