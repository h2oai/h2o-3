from __future__ import print_function
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.automl import H2OAutoML
from h2o.automl.autoh2o import get_automl

def prostate_automl_get_automl():

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

    aml = H2OAutoML(max_runtime_secs=30, project_name="py_aml0", stopping_rounds=3, stopping_tolerance=0.001, stopping_metric="AUC", max_models=10, seed=1234)
    aml.train(y="CAPSULE", training_frame=train)

    get_aml = get_automl(aml.automl_key)

    assert aml.project_name == get_aml["project_name"]
    assert aml.automl_key == get_aml["automl_key"]
    get_aml_leader = get_aml["leader"]
    assert aml.leader.model_id == get_aml_leader.model_id
    assert aml.leaderboard.get_frame_data() == get_aml["leaderboard"].get_frame_data()

if __name__ == "__main__":
    pyunit_utils.standalone_test(prostate_automl_get_automl)
else:
    prostate_automl_get_automl()