import sys, os

sys.path.insert(1, os.path.join("..","..","..",".."))
from h2o.automl import H2OAutoML
from tests import pyunit_utils as pu

from _automl_utils import import_dataset


def test_default_automl_with_multiclass_task():
    ds = import_dataset('multiclass')
    aml = H2OAutoML(max_models=2,
                    project_name='aml_multiclass')

    model = aml.train(y=ds.target, training_frame=ds.train, validation_frame=ds.valid, leaderboard_frame=ds.test)
    print(aml.leader)
    print(aml.leaderboard)
    assert aml.leaderboard.columns == ["model_id", "mean_per_class_error", "logloss", "rmse", "mse"]
    auc_table = model.model_performance().multinomial_auc_table()
    print(auc_table)
    assert "AUC table was not computed" in auc_table, "The multinomial AUC table should not be computed."

    # test setting auc_type
    auc_type = "WEIGHTED_OVR"
    aml2 = H2OAutoML(max_models=2,
                     project_name='aml_multiclass_auc_type',
                     # auc_type is not implemented and used in StackedEnsemble model 
                     # (see https://github.com/h2oai/h2o-3/issues/16373)
                     exclude_algos=["StackedEnsemble"],  
                     auc_type=auc_type)
    model = aml2.train(y=ds.target, training_frame=ds.train, validation_frame=ds.valid, leaderboard_frame=ds.test)
    print(model)
    print(model.params["auc_type"])
    assert auc_type == model.params["auc_type"]["input"], "The auc_type parameter should be the same."
    auc_table = model.model_performance().multinomial_auc_table()
    print(auc_table)
    assert "AUC table was not computed" not in str(auc_table), "The multinomial AUC table should be calculated"

    # wrong auc_type
    try:
        H2OAutoML(max_models=2,
                         project_name='aml_multiclass_auc_type',
                         auc_type="ABC")
    except AssertionError as e:
        assert "The auc_type must be one of ['MACRO_OVO', 'WEIGHTED_OVO', 'MACRO_OVR', 'WEIGHTED_OVR', 'AUTO', 'NONE']" in str(e), "Model build should fail."


pu.run_tests([
    test_default_automl_with_multiclass_task
])
