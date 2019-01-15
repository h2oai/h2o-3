import h2o

from h2o.tree import H2OTree
from h2o.estimators import H2OIsolationForestEstimator
from tests import pyunit_utils


def check_tree(tree, tree_number, tree_class = None):
    assert tree is not None
    assert len(tree) > 0
    assert tree._tree_number == tree_number
    assert tree._tree_class == tree_class

    assert tree.root_node is not None
    assert tree.left_children is not None
    assert tree.right_children is not None
    assert tree.thresholds is not None
    assert tree.nas is not None
    assert tree.descriptions is not None
    assert tree.node_ids is not None
    assert tree.model_id is not None
    assert tree.levels is not None
    assert tree.root_node.na_direction is not None
    assert tree.root_node.id is not None


def irf_tree_Test():
    cat_frame = h2o.create_frame(cols=10, categorical_fraction=1, seed=42)
    # check all columns are categorical
    assert set(cat_frame.types.values()) == set(['enum'])

    iso_model = H2OIsolationForestEstimator(seed=42)
    iso_model.train(training_frame=cat_frame)

    tree = H2OTree(iso_model, 5)
    check_tree(tree, 5, None)
    print(tree)

if __name__ == "__main__":
    pyunit_utils.standalone_test(irf_tree_Test)
else:
    irf_tree_Test()
