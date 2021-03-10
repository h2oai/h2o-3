import h2o
import os, sys

sys.path.insert(1, os.path.join("..", ".."))

from h2o.tree import H2OTree
from h2o.estimators import H2OXGBoostEstimator
from tests import pyunit_utils

# PUBDDEV-7267
def test_terminal_xgboost_nodes():
    df = h2o.import_file(pyunit_utils.locate("smalldata/demos/bank-additional-full.csv"))

    xgboost = H2OXGBoostEstimator(max_depth=1, ntrees=1)
    model = xgboost.train(y="y", training_frame=df)
    tree = H2OTree(xgboost, 0)
    assert len(tree.node_ids) == 3

    # tree.descriptions is deprecated
    # Depth is 1 - last two nodes should be described as terminal
    assert "terminal node" in tree.descriptions[1]
    assert "terminal node" in tree.descriptions[2]

    # Prediction is part of the description for terminal nodes
    assert "Prediction: " in tree.descriptions[1]
    assert "Prediction: " in tree.descriptions[2]

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_terminal_xgboost_nodes)
else:
    test_terminal_xgboost_nodes()
