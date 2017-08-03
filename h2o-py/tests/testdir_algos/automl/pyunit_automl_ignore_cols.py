from __future__ import print_function
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.automl import H2OAutoML

"""
This test is used to check different variants of `ignored_columns` in `.train()`
"""
def prostate_automl():

    df = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))

    #Split frames
    fr = df.split_frame(ratios=[.8,.1])

    #Set up train, validation, and test sets
    train = fr[0]
    valid = fr[1]
    test = fr[2]

    aml = H2OAutoML(max_runtime_secs = 30,stopping_rounds=3,stopping_tolerance=0.001)

    train["CAPSULE"] = train["CAPSULE"].asfactor()
    valid["CAPSULE"] = valid["CAPSULE"].asfactor()
    test["CAPSULE"] = test["CAPSULE"].asfactor()

    print("AutoML with x as a str list, train, valid, and test")
    x = ["AGE","RACE","DPROS"]
    y = "CAPSULE"
    names = train.names
    aml.train(x=x,y=y, training_frame=train,validation_frame=valid, leaderboard_frame=test)
    models = aml.leaderboard["model_id"]
    pyunit_utils.check_ignore_cols_automl(models,names,x,y)

    print("AutoML with x and y as col indexes, train, valid, and test")
    aml.train(x=[2,3,4],y=1, training_frame=train,validation_frame=valid, leaderboard_frame=test)
    models = aml.leaderboard["model_id"]
    pyunit_utils.check_ignore_cols_automl(models,names,x,y)


    print("AutoML with x as a str list, y as a col index, train, valid, and test")
    aml.train(x=x,y=1, training_frame=train,validation_frame=valid, leaderboard_frame=test)
    models = aml.leaderboard["model_id"]
    pyunit_utils.check_ignore_cols_automl(models,names,x,y)

    print("AutoML with x as col indexes, y as a str, train, valid, and test")
    aml.train(x=[2,3,4],y=y, training_frame=train,validation_frame=valid, leaderboard_frame=test)
    models = aml.leaderboard["model_id"]
    pyunit_utils.check_ignore_cols_automl(models,names,x,y)

if __name__ == "__main__":
    pyunit_utils.standalone_test(prostate_automl)
else:
    prostate_automl()
