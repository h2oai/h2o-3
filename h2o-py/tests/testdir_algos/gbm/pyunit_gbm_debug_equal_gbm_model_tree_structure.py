import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators import H2OGradientBoostingEstimator, H2ORandomForestEstimator
from h2o.debug import equal_gbm_model_tree_structure


def gbm_debug_equal_gbm_model_tree_structure():
    fr = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    target = "CAPSULE"
    ntrees = 10

    model_1 = H2OGradientBoostingEstimator(ntrees=ntrees, sample_rate=1, seed=1234)
    model_1.train(y=target, training_frame=fr)
    model_2 = H2OGradientBoostingEstimator(ntrees=ntrees, sample_rate=1, seed=1234)
    model_2.train(y=target, training_frame=fr)
    assert equal_gbm_model_tree_structure(model_1, model_2), "Regression models are not the same"

    model_1 = H2OGradientBoostingEstimator(ntrees=ntrees, sample_rate=1, seed=1234)
    model_1.train(y=target, training_frame=fr)
    model_2 = H2OGradientBoostingEstimator(ntrees=20, sample_rate=1, seed=1234)
    model_2.train(y=target, training_frame=fr)
    assert not equal_gbm_model_tree_structure(model_1, model_2), "Given True but models has different ntrees parameter"

    model_1 = H2OGradientBoostingEstimator(ntrees=ntrees, sample_rate=1, seed=1234)
    model_1.train(y=target, training_frame=fr)
    fr[target] = fr[target].asfactor()
    model_2 = H2OGradientBoostingEstimator(ntrees=ntrees, sample_rate=1, seed=1234)
    model_2.train(y=target, training_frame=fr)
    assert not equal_gbm_model_tree_structure(model_1, model_2), "Given True but models has different model_type"

    model_1 = H2OGradientBoostingEstimator(ntrees=ntrees, sample_rate=1, seed=1234)
    model_1.train(y=target, training_frame=fr)
    model_2 = H2OGradientBoostingEstimator(ntrees=ntrees, sample_rate=1, seed=1234)
    model_2.train(y=target, training_frame=fr)
    assert equal_gbm_model_tree_structure(model_1, model_2), "Binomial models are not the same"

    model_1 = H2OGradientBoostingEstimator(ntrees=ntrees, sample_rate=1, seed=1234)
    model_1.train(y=target, training_frame=fr)
    target = "RACE"
    fr[target] = fr[target].asfactor()
    model_2 = H2OGradientBoostingEstimator(ntrees=ntrees, sample_rate=1, seed=1234)
    model_2.train(y=target, training_frame=fr)
    assert not equal_gbm_model_tree_structure(model_1, model_2), "Given True but models has different categorical"

    model_1 = H2OGradientBoostingEstimator(ntrees=ntrees, sample_rate=1, seed=1234)
    model_1.train(y=target, training_frame=fr)
    model_2 = H2OGradientBoostingEstimator(ntrees=ntrees, sample_rate=1, seed=1234)
    model_2.train(y=target, training_frame=fr)
    assert equal_gbm_model_tree_structure(model_1, model_2), "Multinomial models are not the same"

    model_1 = H2ORandomForestEstimator(ntrees=ntrees, sample_rate=1, seed=1234)
    model_1.train(y=target, training_frame=fr)
    model_2 = H2OGradientBoostingEstimator(ntrees=ntrees, sample_rate=1, seed=1234)
    model_2.train(y=target, training_frame=fr)
    assert not equal_gbm_model_tree_structure(model_1, model_2), "Given True but models has different algorithms"


if __name__ == "__main__":
    pyunit_utils.standalone_test(gbm_debug_equal_gbm_model_tree_structure)
else:
    gbm_debug_equal_gbm_model_tree_structure()
