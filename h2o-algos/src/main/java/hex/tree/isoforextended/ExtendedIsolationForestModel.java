package hex.tree.isoforextended;

import hex.tree.SharedTreeModel;
import water.Key;
import water.util.SBPrintStream;

/**
 * 
 * @author Adam Valenta
 */
public class ExtendedIsolationForestModel extends SharedTreeModel<ExtendedIsolationForestModel,
                                                                    ExtendedIsolationForestParameters,
                                                                    ExtendedIsolationForestOutput> {

    public ExtendedIsolationForestModel(Key<ExtendedIsolationForestModel> selfKey, ExtendedIsolationForestParameters parms,
                                        ExtendedIsolationForestOutput output) {
        super(selfKey, parms, output);
    }

    @Override
    protected void toJavaUnifyPreds(SBPrintStream body) {
        // avalenta - TODO
    }
}
