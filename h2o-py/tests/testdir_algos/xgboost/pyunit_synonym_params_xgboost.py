from builtins import range
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
from h2o.estimators.xgboost import *


def xgboost_synonym_params():
    df = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    model = H2OXGBoostEstimator(ntrees=10, max_depth=3, learn_rate=0.2)
    x = list(range(1, df.ncol-2))
    y = df.names[len(df.names) - 1]
    pairs = [
        [ "eta", "learn_rate", [0.1, 0.2, 0.3]],
        [ "subsample", "sample_rate", [0.1, 0.2, 0.3]],
        [ "colsample_bytree", "col_sample_rate_per_tree", [0.1, 0.2, 0.3]],
        [ "colsample_bylevel", "col_sample_rate", [0.1, 0.2, 0.3]],
        [ "max_delta_step", "max_abs_leafnode_pred", [0.1, 0.2, 0.3 ]],
        [ "gamma", "min_split_improvement", [0.1, 0.2, 0.3]],
    ]
    for a in pairs:
        p1 = a[0]
        p2 = a[1]
        vals = a[2] 
        print("check parity of %s and %s via %s" % (p1, p2, vals))
        # check default values end up to same value
        model.train(x=x, y=y, training_frame=df )
        assert model.parms[p1]['actual_value'] == model.parms[p2]['actual_value']
        # changing p2 modifies both
        setattr(model, p2, vals[0])
        model.train(x=x, y=y, training_frame=df )
        assert model.parms[p1]['actual_value'] == vals[0]
        assert model.parms[p1]['actual_value'],  model.parms[p2]['actual_value']
        # changing p1 modifies both
        setattr(model, p1, vals[1])
        model.train(x=x, y=y, training_frame=df )
        assert model.parms[p1]['actual_value'] == vals[1]
        assert model.parms[p1]['actual_value'], model.parms[p2]['actual_value']
        # changing p2 has no effect since p1 has precedence
        setattr(model, p2, vals[2])
        model.train(x=x, y=y, training_frame=df)
        assert model.parms[p1]['actual_value'] == vals[1]
        assert model.parms[p1]['actual_value'], model.parms[p2]['actual_value']


if __name__ == "__main__":
    pyunit_utils.standalone_test(xgboost_synonym_params)
else:
    xgboost_synonym_params()
