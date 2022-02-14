from __future__ import print_function
import sys
import h2o
sys.path.insert(1,"../../../")
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def test_gbm_split_weights():
    data = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    data = data.drop("ID")
    model_original = H2OGradientBoostingEstimator(ntrees=1, max_depth=2, seed=1234)
    model_original.train(y="CAPSULE", training_frame=data)
    
    orig_varimp = model_original.varimp()
    print(orig_varimp)

    # Suppress the most important feature by giving the associated split-error a large weight
    top_orig_feature = orig_varimp[0][0]
    split_weight_frame = h2o.H2OFrame([[top_orig_feature, 10]])
    print(split_weight_frame)

    model_modified = H2OGradientBoostingEstimator(ntrees=1, max_depth=2, seed=1234,
                                                  split_weights_key=split_weight_frame.frame_id)
    model_modified.train(y="CAPSULE", training_frame=data)

    mod_varimp = model_modified.varimp()
    print(mod_varimp)

    assert mod_varimp[0][0] != top_orig_feature


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gbm_split_weights)
else:
    test_gbm_split_weights()
