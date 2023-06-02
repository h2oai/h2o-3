from builtins import range
import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils, assert_equals, compare_frames, assert_not_equal
from h2o.estimators import H2OGradientBoostingEstimator
from h2o.tree import H2OTree


def assert_list_equals(expected, actual, delta=0):
    assert_equals(len(expected), len(actual))
    for i in range(0, len(expected)):
        assert_equals(expected[i], actual[i], delta=delta)


def models_are_equal(model_1, model_2, pred_1, pred_2):
    assert_equals(True, compare_frames(pred_1, pred_2, pred_1.nrows))
    for tree_id in range(model_1.ntrees):
        for output_class in model_1._model_json["output"]["domains"][-1]:
            tree = H2OTree(model=model_1, tree_number=tree_id, tree_class=output_class)
            tree2 = H2OTree(model=model_2, tree_number=tree_id, tree_class=output_class)
            assert_list_equals(tree.predictions, tree2.predictions)
            assert_list_equals(tree.thresholds, tree2.thresholds, delta=1e-50) # need to specify delta to check nans
            assert_list_equals(tree.decision_paths, tree2.decision_paths)


def gbm_check_variable_importance_and_model(data, target):
    fr = h2o.import_file(pyunit_utils.locate(data))
    fr[target] = fr[target].asfactor()
    variable_importance_same = []
    ntrees=50
    for i in range(5):
        model_1 = H2OGradientBoostingEstimator(ntrees=ntrees, sample_rate=1, seed=1234)
        model_1.train(y=target, training_frame=fr)
        pred_1 = model_1.predict(fr)
        model_2 = H2OGradientBoostingEstimator(ntrees=ntrees, sample_rate=1, seed=1234)
        model_2.train(y=target, training_frame=fr)
        pred_2 = model_2.predict(fr)

        relative_importance1 = model_1.varimp()
        relative_importance2 = model_2.varimp()
        # This is a bug, will be fixed in PUBDEV-9000
        variable_importance_same.append(relative_importance1 == relative_importance2)
        print(relative_importance1)
        print(relative_importance2)
        # Even thought there is a difference in variable importance the models should be the same
        models_are_equal(model_1, model_2, pred_1, pred_2)
    return variable_importance_same


def gbm_reproducibility_variable_importance_different_but_same_model_small():
    variable_importance_same = gbm_check_variable_importance_and_model("smalldata/prostate/prostate.csv", "RACE")
    print(variable_importance_same)
    # This is a bug, will be fixed in PUBDEV-9000
    assert_equals(False, all(variable_importance_same))  # should be equal to True


def gbm_reproducibility_variable_importance_different_but_same_model_large():
    variable_importance_same = gbm_check_variable_importance_and_model("bigdata/laptop/covtype/covtype.full.csv", "Cover_Type")
    print(variable_importance_same)
    # This is a bug, will be fixed in PUBDEV-9000
    assert_equals(False, all(variable_importance_same))  # should be equal to True


if __name__ == "__main__":
    pyunit_utils.run_tests([gbm_reproducibility_variable_importance_different_but_same_model_small, gbm_reproducibility_variable_importance_different_but_same_model_large])
else:
    pyunit_utils.run_tests([gbm_reproducibility_variable_importance_different_but_same_model_small, gbm_reproducibility_variable_importance_different_but_same_model_large])
