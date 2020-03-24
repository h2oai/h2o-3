package hex.tree.xgboost.predict;

import water.fvec.Chunk;

public interface XGBoostPredictContrib extends XGBoostPredict {

    float[][] predictContrib(final Chunk[] cs);
    
}
