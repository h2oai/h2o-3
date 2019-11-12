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

    # Wrong tree class
    try:
        H2OTree(xgbModel, 0, "YES")
        assert False;
    except h2o.exceptions.H2OResponseError as e:
        assert e.args[0].dev_msg == "For binomial XGBoost model, only one tree for class NO has been built."

    # Exceeding tree number
    try:
        H2OTree(xgbModel, 1, "NO")  # There is only one tree, tree index of 1 points to a second tree
        assert False;
    except h2o.exceptions.H2OResponseError as e:
        assert e.args[0].dev_msg == "There is no such tree number for given class. Total number of trees is 1."

    # Negative tree number
    try:
        H2OTree(xgbModel, -1, "NO")  # There is only one tree, tree index of 1 points to a second tree
        assert False;
    except h2o.exceptions.H2OResponseError as e:
        assert e.args[0].dev_msg == "Invalid tree number: -1. Tree number must be >= 0."


    # Multinomial model
    xgbMultinomialModel = H2OXGBoostEstimator(ntrees = 1)
    xgbMultinomialModel.train(x = ["Origin", "Distance"], y = "Dest", training_frame=airlines)
    multinomialTree = H2OTree(xgbMultinomialModel, 0, "SFO")
    assert multinomialTree is not None

    # Non-existing class in multinomial model
    try:
        H2OTree(multinomialTree, 0, "ABCD")  # There is no such Destination mas 'ABCD'
        assert False;
    except h2o.exceptions.H2OResponseError as e:
        assert e.args[0].dev_msg == "No such class 'ABCD' in tree."

    # Class not specified for multinomial
    try:
        H2OTree(multinomialTree, 0, None)  # There is no such Destination mas 'ABCD'
        assert False;
    except h2o.exceptions.H2OResponseError as e:
        assert e.args[0].dev_msg == "Non-regressional models require tree class specified."


    # Regression
    xgbRegressionModel = H2OXGBoostEstimator(ntrees = 1)
    xgbRegressionModel.train(x = ["Origin", "IsDepDelayed"], y = "Distance", training_frame=airlines)
    regressionTree = H2OTree(xgbRegressionModel, 0, None)
    assert regressionTree is not None

    # Class  specified for regression
    try:
        H2OTree(regressionTree, 0, "SFO")  # There is no such Destination mas 'ABCD'
    except h2o.exceptions.H2OResponseError as e:
        assert e.args[0].dev_msg == "There should be no tree class specified for regression."



if __name__ == "__main__":
    pyunit_utils.standalone_test(xgboost_tree_test)
else:
    xgboost_tree_test()
