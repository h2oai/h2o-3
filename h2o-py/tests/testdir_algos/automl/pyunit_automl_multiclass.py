import sys, os

sys.path.insert(1, os.path.join("..","..","..",".."))
from h2o.automl import H2OAutoML
from tests import pyunit_utils as pu

from _automl_utils import import_dataset


def test_default_automl_with_multiclass_task():
    ds = import_dataset('multiclass')
    aml = H2OAutoML(max_models=2,
                    project_name='aml_multiclass')

    aml.train(y=ds.target, training_frame=ds.train, validation_frame=ds.valid, leaderboard_frame=ds.test)
    print(aml.leader)
    print(aml.leaderboard)
    assert aml.leaderboard.columns == ["model_id", "mean_per_class_error", "logloss", "rmse", "mse"]

    # test setting auc_type
    auc_type = "WEIGHTED_OVR"
    aml2 = H2OAutoML(max_models=2,
                     project_name='aml_multiclass_auc_type',
                     algo_parameters={"auc_type": auc_type})
    model = aml2.train(y=ds.target, training_frame=ds.train, validation_frame=ds.valid, leaderboard_frame=ds.test)
    print(model)
    print(model.params["auc_type"])
    assert auc_type == model.params["auc_type"]["input"], "The auc_type parameter should be the same."
    auc_table = model.model_performance().multinomial_auc_table()
    print(auc_table)
    assert auc_table is not None, "The multinomial AUC table should not be None"


pu.run_tests([
    test_default_automl_with_multiclass_task
])
