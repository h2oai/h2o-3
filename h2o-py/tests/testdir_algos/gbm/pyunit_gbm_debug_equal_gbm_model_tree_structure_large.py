import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators import H2OGradientBoostingEstimator
from h2o.debug import equal_gbm_model_tree_structure


def gbm_debug_equal_gbm_model_tree_structure_large():
    fr = h2o.import_file(pyunit_utils.locate("bigdata/laptop/covtype/covtype.full.csv"))
    target = "Cover_Type"
    ntrees = 10

    model_1 = H2OGradientBoostingEstimator(ntrees=ntrees, sample_rate=1, seed=1234)
    model_1.train(y=target, training_frame=fr)
    model_2 = H2OGradientBoostingEstimator(ntrees=ntrees, sample_rate=1, seed=1234)
    model_2.train(y=target, training_frame=fr)
    assert equal_gbm_model_tree_structure(model_1, model_2), "Regression models are not the same"

    fr[target] = fr[target].asfactor()
    model_1 = H2OGradientBoostingEstimator(ntrees=ntrees, sample_rate=1, seed=1234)
    model_1.train(y=target, training_frame=fr)
    model_2 = H2OGradientBoostingEstimator(ntrees=ntrees, sample_rate=1, seed=1234)
    model_2.train(y=target, training_frame=fr)
    assert equal_gbm_model_tree_structure(model_1, model_2), "Multinomial models are not the same"


if __name__ == "__main__":
    pyunit_utils.standalone_test(gbm_debug_equal_gbm_model_tree_structure_large)
else:
    gbm_debug_equal_gbm_model_tree_structure_large()
