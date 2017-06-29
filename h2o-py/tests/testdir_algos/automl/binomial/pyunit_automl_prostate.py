from __future__ import print_function
import sys, os
sys.path.insert(1, os.path.join("..","..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.automl import H2OAutoML

def prostate_automl():

    df = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))

    # Split frames; make the splits repeatable to test multiple runs
    # TODO: note that frames with the following names get created, but some Python binding temp
    # magic gives random names to the frames that are given to AutoML.  See PUBDEV-4634.
    fr = df.split_frame(ratios=[.8,.1], destination_frames=["prostate_train", "prostate_valid", "prostate_test"], seed=42)

    #Set up train, validation, and test sets
    train = fr[0]
    valid = fr[1]
    test = fr[2]

#    aml = H2OAutoML(max_runtime_secs = 30, stopping_rounds=3, stopping_tolerance=0.001, project_name='prostate')
    aml = H2OAutoML(max_runtime_secs = 300, stopping_rounds=2, stopping_tolerance=0.05, project_name='prostate')
    # aml = H2OAutoML(max_models=8, stopping_rounds=2, seed=42, project_name='prostate')

    train["CAPSULE"] = train["CAPSULE"].asfactor()
    valid["CAPSULE"] = valid["CAPSULE"].asfactor()
    test["CAPSULE"] = test["CAPSULE"].asfactor()

    print("AutoML (Binomial) run with x not provided with train, valid, and test")
    aml.train(y="CAPSULE", training_frame=train,validation_frame=valid, leaderboard_frame=test)
    print(aml.leader)
    print(aml.leaderboard)
    assert set(aml.leaderboard.columns) == set(["model_id","auc","logloss"])

# Should we allow models to accumulate in the leaderboard across runs?
removeall_before_running = True
if __name__ == "__main__":
    if removeall_before_running:
        pyunit_utils.standalone_test(prostate_automl)
    else:
        h2o.init(strict_version_check=False)
        prostate_automl()

else:
    prostate_automl()
