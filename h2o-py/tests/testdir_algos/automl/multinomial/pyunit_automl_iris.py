from __future__ import print_function
import sys, os
sys.path.insert(1, os.path.join("..","..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.automl import H2OAutoML

def iris_automl():

    df = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))

    #Split frames
    fr = df.split_frame(ratios=[.8,.1])

    #Set up train, validation, and test sets
    train = fr[0]
    valid = fr[1]
    test = fr[2]

    aml = H2OAutoML(max_runtime_secs = 3,stopping_rounds=3,stopping_tolerance=0.001)

    print("AutoML (Multinomial) run with x not provided with train, valid, and test")
    aml.train(y="class", training_frame=train,validation_frame=valid, leaderboard_frame=test)
    print(aml.leader)
    print(aml.leaderboard)
    assert set(aml.leaderboard.columns) == set(["model_id","mean_per_class_error"])

if __name__ == "__main__":
    pyunit_utils.standalone_test(iris_automl)
else:
    iris_automl()