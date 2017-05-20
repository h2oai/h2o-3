from __future__ import print_function
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.automl import H2OAutoML

def prostate_automl():

    df = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))

    #Split frames
    fr = df.split_frame(ratios=[.8,.1])

    #Set up train, validation, and test sets
    train = fr[0]
    valid = fr[1]
    test = fr[2]

    train["CAPSULE"] = train["CAPSULE"].asfactor()
    valid["CAPSULE"] = valid["CAPSULE"].asfactor()
    test["CAPSULE"] = test["CAPSULE"].asfactor()

    print("Check arguments to H2OAutoML class")
    aml = H2OAutoML(max_runtime_secs = 10,project_name="aml",stopping_rounds=3,stopping_tolerance=0.001)
    aml.train(y="CAPSULE", training_frame=train)
    assert aml.max_runtime_secs == 10, "max_runtime_secs is not set to 10 secs"
    assert aml.project_name == "aml", "Project name is not set"

    print("AutoML run with x not provided and train set only")
    aml.build_control["project"] = "Project1"
    aml.train(y="CAPSULE", training_frame=train)
    assert aml.max_runtime_secs == 10, "max_runtime_secs is not set to 10 secs"

    print("AutoML run with x not provided with train and valid")
    aml.build_control["project"] = "Project2"
    aml.train(y="CAPSULE", training_frame=train,validation_frame=valid)
    assert aml.max_runtime_secs == 10, "max_runtime_secs is not set to 10 secs"

    print("AutoML run with x not provided with train and test")
    aml.build_control["project"] = "Project3"
    aml.train(y="CAPSULE", training_frame=train,leaderboard_frame=test)
    assert aml.max_runtime_secs == 10, "max_runtime_secs is not set to 10 secs"

    print("AutoML run with x not provided with train, valid, and test")
    aml.build_control["project"] = "Project4"
    aml.train(y="CAPSULE", training_frame=train,validation_frame=valid, leaderboard_frame=test)
    assert aml.max_runtime_secs == 10, "max_runtime_secs is not set to 10 secs"

    print("AutoML run with x not provided and y as col idx with train, valid, and test")
    aml.build_control["project"] = "Project5"
    aml.train(y=1, training_frame=train,validation_frame=valid, leaderboard_frame=test)
    assert aml.max_runtime_secs == 10, "max_runtime_secs is not set to 10 secs"

    print("Check predict, leader, and leaderboard")
    print("AutoML run with x not provided and train set only")
    aml.build_control["project"] = "Project6"
    aml.train(y="CAPSULE", training_frame=train)
    print("Check leader")
    aml.leader
    print("Check leaderboard")
    aml.leaderboard
    print("Check predictions")
    aml.predict(train)

if __name__ == "__main__":
    pyunit_utils.standalone_test(prostate_automl)
else:
    prostate_automl()
