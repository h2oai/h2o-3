package hex.tree.xgboost.predict;

import hex.Model;
import water.fvec.Chunk;
import water.fvec.Frame;

public interface XGBoostBigScorePredict extends Model.BigScorePredict {

    @Override
    XGBoostPredict initMap(Frame fr, Chunk[] chks);

}
