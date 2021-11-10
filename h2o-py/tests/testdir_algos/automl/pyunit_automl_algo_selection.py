from __future__ import print_function
import sys, os

sys.path.insert(1, os.path.join("..","..",".."))
from h2o.automl import H2OAutoML
from tests import pyunit_utils as pu

from _automl_utils import import_dataset, get_partitioned_model_names


max_models = 5


def test_exclude_algos():
    print("AutoML doesn't train models for algos listed in exclude_algos")
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_exclude_algos",
                    exclude_algos=['DRF', 'GLM'],
                    max_models=max_models,
                    seed=1)
    aml.train(y=ds.target, training_frame=ds.train, validation_frame=ds.valid)
    models = get_partitioned_model_names(aml.leaderboard)
    assert not any(['DRF' in name or 'GLM' in name for name in models.base])
    assert len(models.se) >= 1


def test_include_algos():
    print("AutoML trains only models for algos listed in include_algos")
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_include_algos",
                    include_algos=['GBM'],
                    max_models=max_models,
                    seed=1)
    aml.train(y=ds.target, training_frame=ds.train, validation_frame=ds.valid)
    models = get_partitioned_model_names(aml.leaderboard)
    assert all(['GBM' in name for name in models.base])
    assert len(models.se) == 0, "No StackedEnsemble should have been trained if not explicitly included to the existing include_algos"


def test_include_exclude_algos():
    print("include_algos and exclude_algos parameters are mutually exclusive")
    try:
        H2OAutoML(project_name="py_include_exclude_algos",
                  exclude_algos=['DRF', 'XGBoost'],
                  include_algos=['GBM'],
                  max_models=max_models,
                  seed=1)
        assert False, "Should have thrown AssertionError"
    except AssertionError as e:
        assert "Use either `exclude_algos` or `include_algos`, not both" in str(e)


pu.run_tests([
    test_exclude_algos,
    test_include_algos,
    test_include_exclude_algos,
])
