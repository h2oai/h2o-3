from __future__ import print_function
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
import random
import sys
from tests import pyunit_utils
from h2o.automl import H2OAutoML

"""
This test is used to check that sort_metric is working
"""
def automl_leaderboard_sort_metric():

    #Random positive seed for AutoML
    if sys.version_info[0] < 3: #Python 2
        automl_seed = random.randint(0,sys.maxint)
    else: #Python 3
        automl_seed = random.randint(0,sys.maxsize)
    print("Random Seed for pyunit_automl_leaderboard.py = " + str(automl_seed))

    all_algos = ["GLM", "DeepLearning", "GBM", "DRF", "StackedEnsemble"]

    # Binomial
    print("Check leaderboard for Binomial")
    fr1 = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    fr1["CAPSULE"] = fr1["CAPSULE"].asfactor()
    exclude_algos = ["GLM", "DeepLearning", "DRF"]
    aml = H2OAutoML(max_models=2, project_name="py_lb_test_aml1", exclude_algos=exclude_algos, seed=automl_seed, sort_metric="logloss")
    aml.train(y="CAPSULE", training_frame=fr1)
    lb = aml.leaderboard
    print("AutoML leaderboard")
    print(lb)
    # check that correct leaderboard columns exist
    assert lb.names == ["model_id", "auc", "logloss", "mean_per_class_error", "rmse", "mse"]
    model_ids = list(h2o.as_list(aml.leaderboard['model_id'])['model_id'])
    # check that no exluded algos are present in leaderboard
    assert len([a for a in exclude_algos if len([b for b in model_ids if a in b])>0]) == 0
    include_algos = list(set(all_algos) - set(exclude_algos))
    # check that expected algos are included in leaderboard
    assert len([a for a in include_algos if len([b for b in model_ids if a in b])>0]) == len(include_algos)


    # Regression
    # TO DO: Change this dataset
    print("Check leaderboard for Regression")
    fr2 = h2o.import_file(path=pyunit_utils.locate("smalldata/covtype/covtype.20k.data"))
    exclude_algos = ["GBM", "DeepLearning"]
    aml = H2OAutoML(max_models=10, project_name="py_lb_test_aml2", exclude_algos=exclude_algos, seed=automl_seed,sort_metric="RMSE")
    aml.train(y=4, training_frame=fr2)
    lb = aml.leaderboard
    print("AutoML leaderboard")
    print(lb)
    # check that correct leaderboard columns exist
    assert lb.names == ["model_id", "mean_residual_deviance","rmse", "mse", "mae", "rmsle"]
    model_ids = list(h2o.as_list(aml.leaderboard['model_id'])['model_id'])
    # check that no exluded algos are present in leaderboard
    assert len([a for a in exclude_algos if len([b for b in model_ids if a in b])>0]) == 0
    include_algos = list(set(all_algos) - set(exclude_algos)) + ["XRT"]
    # check that expected algos are included in leaderboard
    assert len([a for a in include_algos if len([b for b in model_ids if a in b])>0]) == len(include_algos)


    # Multinomial
    print("Check leaderboard for Multinomial")
    fr3 = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
    exclude_algos = ["GBM"]
    aml = H2OAutoML(max_models=6, project_name="py_lb_test_aml3", exclude_algos=exclude_algos, seed=automl_seed, sort_metric="logloss")
    aml.train(y=4, training_frame=fr3)
    lb = aml.leaderboard
    print("AutoML leaderboard")
    print(lb)
    # check that correct leaderboard columns exist
    assert lb.names == ["model_id", "mean_per_class_error", "logloss", "rmse", "mse"]
    model_ids = list(h2o.as_list(aml.leaderboard['model_id'])['model_id'])
    # check that no exluded algos are present in leaderboard
    assert len([a for a in exclude_algos if len([b for b in model_ids if a in b])>0]) == 0
    include_algos = list(set(all_algos) - set(exclude_algos)) + ["XRT"]
    # check that expected algos are included in leaderboard
    assert len([a for a in include_algos if len([b for b in model_ids if a in b])>0]) == len(include_algos)


if __name__ == "__main__":
    pyunit_utils.standalone_test(automl_leaderboard_sort_metric)
else:
    automl_leaderboard_sort_metric()
