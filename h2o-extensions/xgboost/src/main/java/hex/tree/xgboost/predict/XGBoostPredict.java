package hex.tree.xgboost.predict;

import hex.Model;
import water.fvec.Chunk;

public interface XGBoostPredict extends Model.BigScoreChunkPredict {

    enum OutputType {PREDICT, PREDICT_CONTRIB_APPROX}

    float[][] predict(final Chunk[] cs);
    
}
