from __future__ import print_function
import sys, os
sys.path.insert(1, os.path.join("..","..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.automl import H2OAutoML

def prostate_automl():

    train = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate_train.csv"))
    valid = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate_test.csv"))

    build_control = {
        'stopping_criteria': {
            'stopping_rounds': 3,
            'stopping_tolerance': 0.001
        }
    }
    aml = H2OAutoML(max_runtime_secs = 30,build_control=build_control)
    train["CAPSULE"] = train["CAPSULE"].asfactor()
    valid["CAPSULE"] = valid["CAPSULE"].asfactor()
    aml.train(y="CAPSULE", training_frame=train,validation_frame=valid)

if __name__ == "__main__":
    pyunit_utils.standalone_test(prostate_automl)
else:
    prostate_automl()
