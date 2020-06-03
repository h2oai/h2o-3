import sys

sys.path.insert(1, "../../")
import h2o
from tests import pyunit_utils, H2ORandomForestEstimator
from h2o.tree import H2OTree


def test_reset_threshold():
    model = H2ORandomForestEstimator(
        ntrees=1,
        max_depth=1000
    )

    df = h2o.import_file('/home/pavel/train_transaction.csv')

    x = df.columns
    y = 'isFraud'
    x.remove(y)

    model.train(x=x, y=y, training_frame=df)
    tree = H2OTree(model=model, tree_number=0)

    leaf_node_ids=set()
    for i in range(0, len(tree)):
        if (tree.left_children[i] == -1):
            leaf_node_ids.add(tree.node_ids[i])


    path_assignment = model.predict_leaf_node_assignment(df, type='path')
    node_assignment = model.predict_leaf_node_assignment(df, type='Node_ID')

    print(len(leaf_node_ids))
    print(len(path_assignment.unique()))
    print(len(node_assignment.unique()))

    assert len(leaf_node_ids) == len(path_assignment.unique()) == len(node_assignment.unique())
    assert len(path_assignment.unique()) == len(node_assignment.unique())


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_reset_threshold)
else:
    test_reset_threshold()
