import h2o

from h2o.tree import H2OTree
from h2o.estimators import H2OXGBoostEstimator
from tests import pyunit_utils

def xgboost_tree_test():

    airlines = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/airlines_train.csv"))
    xgbModel = H2OXGBoostEstimator(ntrees = 1)
    xgbModel.train(x = ["Origin", "Distance"], y = "IsDepDelayed", training_frame=airlines)

    tree = H2OTree(xgbModel, 0, "NO")
    assert tree is not None

    print(tree)
    assert len(tree) > 0
    assert tree.root_node is not None
    assert tree.left_children is not None
    assert tree.right_children is not None
    assert tree.thresholds is not None
    assert tree.nas is not None
    assert tree.descriptions is not None
    assert tree.node_ids is not None
    assert tree.model_id is not None
    assert tree.levels is not None
    assert tree.tree_number is not None

    assert tree._tree_number == 0
    assert tree._tree_class == "NO"

    print(tree._root_node)

    assert tree.root_node.na_direction is not None
    assert tree.root_node.id is not None


    xgbRegressionModel =  H2OXGBoostEstimator(ntrees = 1)
    xgbRegressionModel.train(x = ["Origin", "Dest"], y = "Distance", training_frame=airlines)

    xgbRegressionTree = H2OTree(xgbRegressionModel, 0, None)
    assert xgbRegressionTree is not None
    print(xgbRegressionTree)
    print(xgbRegressionTree.root_node)

if __name__ == "__main__":
    pyunit_utils.standalone_test(xgboost_tree_test)
else:
    xgboost_tree_test()
