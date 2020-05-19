from __future__ import print_function
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.automl import H2OAutoML


def test_automl_creates_interpretable_SE_iff_monotonic_models_exist():
    train = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    y = 'CAPSULE'
    train[y] = train[y].asfactor()

    aml_mono = H2OAutoML(project_name="test_automl_creates_interpretable_se",
                    max_models=2,
                    include_algos=["GBM", "XGBoost", "StackedEnsemble"],
                    monotone_constraints=dict(
                    AGE=1,DPROS=1,DCAPS=1,PSA=1,VOL=1,GLEASON=1
                    ),
                    seed=1234)
    aml_mono.train(y=y, training_frame=train)

    assert (aml_mono
            .leaderboard
            .as_data_frame()["model_id"]
            .apply(lambda model_name: "MonotonicallyConstrainedModels" in model_name).any())

    # If we don't have monotonic constraints we shouldn't have monotonically constrained SE
    aml = H2OAutoML(project_name="test_automl_doesnt_create_interpretable_se",
                    max_models=2,
                    include_algos=["GBM", "XGBoost", "StackedEnsemble"],
                    seed=1234)
    aml.train(y=y, training_frame=train)

    assert not (aml
            .leaderboard
            .as_data_frame()["model_id"]
            .apply(lambda model_name: "MonotonicallyConstrainedModels" in model_name).any())

pyunit_utils.run_tests([test_automl_creates_interpretable_SE_iff_monotonic_models_exist])
