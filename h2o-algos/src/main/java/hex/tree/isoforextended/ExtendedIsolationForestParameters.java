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
    
    public int _extension_level;
}
