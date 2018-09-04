import h2o

from h2o.tree import H2OTree
from h2o.estimators import H2OGradientBoostingEstimator
from h2o.estimators import H2ORandomForestEstimator
from tests import pyunit_utils

def tree_test():

    airlines = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/airlines_train.csv"))
    gbm = H2OGradientBoostingEstimator(ntrees = 1)
    gbm.train(x = ["Origin", "Dest", "Distance"], y = "IsDepDelayed", training_frame=airlines)

    tree = H2OTree(gbm, 0, "NO") # Indexing from 0 in Python. There is exactly one tree built
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
    assert tree.tree_class is not None
    assert tree.tree_number is not None

    assert tree._tree_class == "NO"
    assert tree._tree_number == 0

    print(tree._root_node)

    assert tree.root_node.na_direction is not None
    assert tree.root_node.id is not None

    airlines = h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars_nice_header.csv"))
    drf = H2ORandomForestEstimator(ntrees = 1)
    drf.train(x = ["power", "acceleration"], y="cylinders", training_frame=airlines)

    drf_tree = H2OTree(drf, 0, None)

    print(drf_tree)
    assert len(drf_tree) > 0
    assert drf_tree.root_node is not None
    assert drf_tree.left_children is not None
    assert drf_tree.right_children is not None
    assert drf_tree.thresholds is not None
    assert drf_tree.nas is not None
    assert drf_tree.descriptions is not None
    assert drf_tree.node_ids is not None
    assert drf_tree.model_id is not None
    assert drf_tree.levels is not None
    assert drf_tree.tree_class is None # Regression - no class
    assert drf_tree.tree_number is not None

    assert drf_tree._tree_number == 0

    assert drf_tree.root_node.na_direction is not None
    assert drf_tree.root_node.id is not None


if __name__ == "__main__":
    pyunit_utils.standalone_test(tree_test)
else:
    tree_test()
