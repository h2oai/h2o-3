from h2o.estimators.xgboost import *
from h2o.estimators.gbm import *
from tests import pyunit_utils


def xgboost_vs_gbm_monotone_test():
    assert H2OXGBoostEstimator.available() is True

    monotone_constraints = {
        "AGE": 1
    }

    xgboost_params = {
        "tree_method": "exact",
        "seed": 123,
        "backend": "cpu", # CPU Backend is forced for the results to be comparable
        "monotone_constraints": monotone_constraints
    }

    gbm_params = {
        "seed": 42,
        "monotone_constraints": monotone_constraints
    }

    prostate_hex = h2o.import_file(pyunit_utils.locate('smalldata/prostate/prostate.csv'))
    prostate_hex["CAPSULE"] = prostate_hex["CAPSULE"].asfactor()

    xgboost_model = H2OXGBoostEstimator(**xgboost_params)
    xgboost_model.train(y="CAPSULE", ignored_columns=["ID"], training_frame=prostate_hex)
    
    gbm_model = H2OGradientBoostingEstimator(**gbm_params)
    gbm_model.train(y="CAPSULE", ignored_columns=["ID"], training_frame=prostate_hex)

    xgb_varimp_percentage = dict(map(lambda x: (x[0], x[3]), xgboost_model.varimp(use_pandas=False)))
    gbm_varimp_percentage = dict(map(lambda x: (x[0], x[3]), gbm_model.varimp(use_pandas=False)))

    # We expect the variable importances of AGE to be similar

    assert xgb_varimp_percentage["VOL"] > xgb_varimp_percentage["AGE"]
    assert xgb_varimp_percentage["AGE"] > xgb_varimp_percentage["RACE"]

    print("XGBoost varimp of AGE = %s" % xgb_varimp_percentage["AGE"])
    print("GBM varimp of AGE = %s" % gbm_varimp_percentage["AGE"])
    assert abs(xgb_varimp_percentage["AGE"] - gbm_varimp_percentage["AGE"]) < 0.02


if __name__ == "__main__":
    pyunit_utils.standalone_test(xgboost_vs_gbm_monotone_test)
else:
    xgboost_vs_gbm_monotone_test()
