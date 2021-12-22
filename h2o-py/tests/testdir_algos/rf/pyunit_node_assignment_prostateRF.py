from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.random_forest import H2ORandomForestEstimator
from h2o.tree.tree import H2OTree


def map_node_ids_to_paths():
    prostate_train = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    prostate_train["CAPSULE"] = prostate_train["CAPSULE"].asfactor()
    prostate_train = prostate_train.drop("ID")

    decision_tree = H2ORandomForestEstimator(ntrees=1, sample_rate=1.0, mtries=len(prostate_train.columns) - 1)
    decision_tree.train(y="AGE", training_frame=prostate_train)

    tree = H2OTree(
        model=decision_tree,
        tree_number=0,
        plain_language_rules=True
    )

    predictions = list(decision_tree.predict(prostate_train).as_data_frame()["predict"])
    node_ids = list(decision_tree.predict_leaf_node_assignment(prostate_train, type="Node_ID").as_data_frame()["T1"])

    # figure out how node ids map to decision paths
    decision_path_ids = list(map(lambda x: tree.node_ids.index(x), node_ids))

    # check that the paths produce correct predictions
    predictions_to_paths = list(zip(predictions, [tree.decision_paths[i] for i in decision_path_ids]))
    for (prediction, path) in predictions_to_paths:
        prediction_from_path = float(path[path.index("Prediction: ") + len("Prediction: "):])
        assert abs(prediction_from_path - prediction) < 1e-6


if __name__ == "__main__":
    pyunit_utils.standalone_test(map_node_ids_to_paths)
else:
    map_node_ids_to_paths()
