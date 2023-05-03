from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.random_forest import H2ORandomForestEstimator


def show_decision_tree():
    prostate_train = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    prostate_train["CAPSULE"] = prostate_train["CAPSULE"].asfactor()
    prostate_train = prostate_train.drop("ID")

    decision_tree = H2ORandomForestEstimator(ntrees=1, max_depth=3, sample_rate=1.0, mtries=len(prostate_train.columns) - 1)
    decision_tree.train(y="CAPSULE", training_frame=prostate_train)
    decision_tree.show()

    tm = decision_tree._model_json["output"]["training_metrics"]
    assert tm._metric_json["max_criteria_and_metric_scores"] is None
    assert tm.confusion_matrix() is None
    assert tm.gains_lift() is None


if __name__ == "__main__":
    pyunit_utils.standalone_test(show_decision_tree)
else:
    show_decision_tree()
