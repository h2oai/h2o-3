package hex.tree.isoforextended;

import hex.ModelCategory;
import hex.tree.SharedTree;
import hex.tree.SharedTreeModel;
import hex.tree.isofor.IsolationForest;
import water.util.TwoDimTable;

public class ExtendedIsolationForestOutput extends SharedTreeModel.SharedTreeOutput {
    
    public ExtendedIsolationForestOutput(SharedTree b) {
        super(b);
    }

    @Override
    public ModelCategory getModelCategory() {
        return ModelCategory.AnomalyDetection;
    }
}
