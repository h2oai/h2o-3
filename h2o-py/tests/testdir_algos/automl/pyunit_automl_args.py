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

    #Make build control for automl
    build_control = {
        'stopping_criteria': {
            'stopping_rounds': 3,
            'stopping_tolerance': 0.001
        }
    }
    aml = H2OAutoML(max_runtime_secs = 10,build_control=build_control)

    train["CAPSULE"] = train["CAPSULE"].asfactor()
    valid["CAPSULE"] = valid["CAPSULE"].asfactor()
    test["CAPSULE"] = test["CAPSULE"].asfactor()

    print("Check arguments to H2OAutoML class")
    aml2 = H2OAutoML(max_runtime_secs = 10,project_name="aml2",build_control=build_control)
    aml2.train(y="CAPSULE", training_frame=train)
    assert aml2.max_runtime_secs == 10, "max_runtime_secs is not set to 10 secs"
    assert aml2.project_name == "aml2", "Project name is not set"
    assert aml2.build_control == build_control, "build_control is not correctly set"

    print("AutoML run with x not provided and train set only")
    build_control["project"] = "Project1"
    aml.train(y="CAPSULE", training_frame=train)
    assert aml.max_runtime_secs == 10, "max_runtime_secs is not set to 10 secs"

    print("AutoML run with x not provided with train and valid")
    build_control["project"] = "Project2"
    aml.train(y="CAPSULE", training_frame=train,validation_frame=valid)
    assert aml.max_runtime_secs == 10, "max_runtime_secs is not set to 10 secs"

    print("AutoML run with x not provided with train and test")
    build_control["project"] = "Project3"
    aml.train(y="CAPSULE", training_frame=train,test_frame=test)
    assert aml.max_runtime_secs == 10, "max_runtime_secs is not set to 10 secs"

    print("AutoML run with x not provided with train, valid, and test")
    build_control["project"] = "Project4"
    aml.train(y="CAPSULE", training_frame=train,validation_frame=valid, test_frame=test)
    assert aml.max_runtime_secs == 10, "max_runtime_secs is not set to 10 secs"

    print("AutoML run with x not provided and y as col idx with train, valid, and test")
    build_control["project"] = "Project5"
    aml.train(y=1, training_frame=train,validation_frame=valid, test_frame=test)
    assert aml.max_runtime_secs == 10, "max_runtime_secs is not set to 10 secs"

    print("Check predict, leader, and leaderboard")
    print("AutoML run with x not provided and train set only")
    build_control["project"] = "Project6"
    aml.train(y="CAPSULE", training_frame=train)
    print("Check leader")
    aml.get_leader()
    print("Check leaderboard")
    aml.get_leaderboard()
    print("Check predictions")
    aml.predict(train)

if __name__ == "__main__":
    pyunit_utils.standalone_test(prostate_automl)
else:
    prostate_automl()
