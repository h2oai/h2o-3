package hex.tree.xgboost.predict;

import hex.genmodel.algos.xgboost.AuxNodeWeightsHelper;
import water.Key;
import water.Keyed;

/**
 * Represents Auxiliary Tree-Node Weights; Auxiliary arbitrary weight the user chooses to store in addition
 * to the weights XGBoost seen in model training
 */
public class AuxNodeWeights extends Keyed<AuxNodeWeights> {

    public final byte[] _nodeWeightBytes; 

    public AuxNodeWeights(Key<AuxNodeWeights> key, double[][] nodeWeights) {
        super(key);
        _nodeWeightBytes = AuxNodeWeightsHelper.toBytes(nodeWeights);
    }

}
