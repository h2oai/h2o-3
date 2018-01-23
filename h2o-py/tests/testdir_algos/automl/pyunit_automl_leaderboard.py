from __future__ import print_function
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.automl import H2OAutoML

"""
This test is used to check arguments passed into H2OAutoML along with different ways of using `.train()`
"""
def automl_leaderboard():

    # Test each ML task to make sure the leaderboard is working as expected:
    # Leaderboard columns are correct for each ML task 
    # Check that correct algos are in the leaderboard


    all_algos = ["GLM", "DeepLearning", "GBM", "DRF", "StackedEnsemble"]


    # Binomial
    fr1 = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    fr1["CAPSULE"] = fr1["CAPSULE"].asfactor()
    exclude_algos = ["GLM", "DeepLearning", "DRF"]
    aml = H2OAutoML(max_models=2, project_name="py_lb_test_aml1", exclude_algos=exclude_algos)
    aml.train(y="CAPSULE", training_frame=fr1)
    lb = aml.leaderboard
    lb
    # check that correct leaderboard columns exist
    assert lb.names == ["model_id", "auc", "logloss"]
    model_ids = list(h2o.as_list(aml.leaderboard['model_id'])['model_id'])
    # check that no exluded algos are present in leaderboard
    assert len([a for a in exclude_algos if len([b for b in model_ids if a in b])>0]) == 0
    include_algos = list(set(all_algos) - set(exclude_algos))
    # check that expected algos are included in leaderboard
    assert len([a for a in include_algos if len([b for b in model_ids if a in b])>0]) == len(include_algos)


    # Regression
    # TO DO: Change this dataset
    fr2 = h2o.import_file(path=pyunit_utils.locate("smalldata/covtype/covtype.20k.data"))
    exclude_algos = ["GBM", "DeepLearning"]
    aml = H2OAutoML(max_models=10, project_name="py_lb_test_aml2", exclude_algos=exclude_algos)
    aml.train(y=4, training_frame=fr2)
    lb = aml.leaderboard
    lb
    # check that correct leaderboard columns exist
    assert lb.names == ["model_id", "mean_residual_deviance","rmse", "mae", "rmsle"]
    model_ids = list(h2o.as_list(aml.leaderboard['model_id'])['model_id'])
    # check that no exluded algos are present in leaderboard
    assert len([a for a in exclude_algos if len([b for b in model_ids if a in b])>0]) == 0
    include_algos = list(set(all_algos) - set(exclude_algos)) + ["XRT"]
    # check that expected algos are included in leaderboard
    assert len([a for a in include_algos if len([b for b in model_ids if a in b])>0]) == len(include_algos)


    # Multinomial
    fr3 = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
    exclude_algos = ["GBM"]
    aml = H2OAutoML(max_models=6, project_name="py_lb_test_aml3", exclude_algos=exclude_algos)
    aml.train(y=4, training_frame=fr3)
    lb = aml.leaderboard
    lb
    # check that correct leaderboard columns exist
    assert lb.names == ["model_id", "mean_per_class_error"]
    model_ids = list(h2o.as_list(aml.leaderboard['model_id'])['model_id'])
    # check that no exluded algos are present in leaderboard
    assert len([a for a in exclude_algos if len([b for b in model_ids if a in b])>0]) == 0
    include_algos = list(set(all_algos) - set(exclude_algos)) + ["XRT"]
    # check that expected algos are included in leaderboard
    assert len([a for a in include_algos if len([b for b in model_ids if a in b])>0]) == len(include_algos)


    # Exclude all the algorithms, check for empty leaderboard
    fr1 = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    fr1["CAPSULE"] = fr1["CAPSULE"].asfactor()
    exclude_algos = ["GLM", "DRF", "GBM", "DeepLearning", "StackedEnsemble"]
    aml = H2OAutoML(max_runtime_secs=5, project_name="py_lb_test_aml4", exclude_algos=exclude_algos)
    aml.train(y="CAPSULE", training_frame=fr1)
    lb = aml.leaderboard
    lb
    # check that correct leaderboard columns exist
    assert lb.names == ["model_id", "auc", "logloss"]
    assert lb.nrows == 0


    # Include all algorithms (all should be there, given large enough max_models)
    aml = H2OAutoML(max_models=10, project_name="py_lb_test_aml5")
    aml.train(y=4, training_frame=fr3)
    lb = aml.leaderboard
    lb
    model_ids = list(h2o.as_list(aml.leaderboard['model_id'])['model_id'])
    include_algos = list(set(all_algos) - set(exclude_algos)) + ["XRT"]
    # check that expected algos are included in leaderboard
    assert len([a for a in include_algos if len([b for b in model_ids if a in b])>0]) == len(include_algos)




if __name__ == "__main__":
    pyunit_utils.standalone_test(automl_leaderboard)
else:
    automl_leaderboard()
