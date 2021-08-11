from __future__ import print_function
from builtins import range
import sys

from h2o.tree import H2OTree
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def decision_tree_language_rules_printing():
    print(" ----- numerical case: -----")
    df = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    df.describe()
    train = df.drop("ID")
    vol = train['VOL']
    vol[vol == 0] = None
    gle = train['GLEASON']
    gle[gle == 0] = None
    train['CAPSULE'] = train['CAPSULE'].asfactor()
    train.describe()
    my_gbm = H2OGradientBoostingEstimator(ntrees=50, learn_rate=0.1, distribution="bernoulli", max_depth = 2, seed = 12345)
    my_gbm.train(x=list(range(1, train.ncol)),
                 y="CAPSULE",
                 training_frame=train,
                 validation_frame=train)
    first_tree = H2OTree(model = my_gbm, tree_number = 0, tree_class = None, plain_language_rules = True)

    print(" -- Tree predictions: -- ")
    print(first_tree.predictions)
    
    print(" -- Language tree representation: -- ")
    assert first_tree.tree_decision_path is not None
    print(first_tree.tree_decision_path)
    assert read_fixture("pyunit_4007_language_tree_representation_numerical_case.txt") == first_tree.tree_decision_path

    print(" -- Language path representation - root node: -- ")
    assert first_tree.decision_paths[first_tree.root_node.id] is not None
    print(first_tree.decision_paths[first_tree.root_node.id])
    
    print(" -- Language path representation - node ", first_tree.predictions.index(first_tree.predictions[3]), " (with pv = ", first_tree.predictions[3], "): -- ")
    assert first_tree.decision_paths[first_tree.predictions.index(first_tree.predictions[3])] is not None
    print(first_tree.decision_paths[first_tree.predictions.index(first_tree.predictions[3])])
    assert read_fixture("pyunit_4007_language_path_representation_numerical_case.txt") == first_tree.decision_paths[first_tree.predictions.index(first_tree.predictions[3])]
    
    print(" ----- categorical case: -----")
    airlines_data = h2o.import_file(path=pyunit_utils.locate("smalldata/airlines/allyears2k_headers.zip"))
    model = H2OGradientBoostingEstimator(ntrees = 3, max_depth = 2, seed = 12345)
    model.train(x=["Origin", "Distance"], y="IsDepDelayed", training_frame=airlines_data)
    tree = H2OTree(model = model, tree_number = 0, tree_class = "NO", plain_language_rules = True)

    print(" -- Tree predictions: -- ")
    print(tree.predictions)
    
    print(" -- Language tree representation: -- ")
    assert tree.tree_decision_path is not None
    print(tree.tree_decision_path)
    assert read_fixture("pyunit_4007_language_tree_representation_categorical_case.txt") == tree.tree_decision_path

    print(" -- Language path representation - root node: -- ")
    assert tree.decision_paths[tree.root_node.id] is not None
    print(tree.decision_paths[tree.root_node.id])
    
    print(" -- Language path representation - node ", tree.predictions.index(tree.predictions[3]), " (with pv = ", tree.predictions[3], "): -- ")
    assert tree.decision_paths[tree.predictions.index(tree.predictions[3])] is not None
    print(tree.decision_paths[tree.predictions.index(tree.predictions[3])])
    assert read_fixture("pyunit_4007_language_path_representation_categorical_case.txt") == tree.decision_paths[tree.predictions.index(tree.predictions[3])]

    # mixed categorical + numerical: 
    airlines_data = h2o.import_file(path=pyunit_utils.locate("./smalldata/testng/airlines_train.csv"))
    model = H2OGradientBoostingEstimator(ntrees=10, seed = 65261, max_depth = 10)
    model.train(y = "IsDepDelayed", training_frame=airlines_data)
    tree = H2OTree(model = model, tree_number = 1, plain_language_rules = True)

    print(" -- Tree predictions: -- ")
    print(tree.predictions)

    print(" -- Language tree representation: -- ")
    assert tree.tree_decision_path is not None
    print(tree.tree_decision_path)
    
    assert read_fixture("pyunit_4007_language_tree_representation_numerical_categorical_case.txt") == tree.tree_decision_path

    print(" -- Language path representation - node ", tree.predictions.index(tree.predictions[393]), " (with pv = ", tree.predictions[393], "): -- ")
    assert tree.decision_paths[tree.predictions.index(tree.predictions[393])] is not None
    print(tree.decision_paths[tree.predictions.index(tree.predictions[393])])
    
    assert read_fixture("pyunit_4007_language_path_representation_numerical_categorical_case.txt") == tree.decision_paths[tree.predictions.index(tree.predictions[393])]


def read_fixture(path):
    text_file = open(pyunit_utils.locate(path), "r")
    expected_tree_representation = text_file.read()
    text_file.close()
    return expected_tree_representation
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(decision_tree_language_rules_printing)
else:
    decision_tree_language_rules_printing()
