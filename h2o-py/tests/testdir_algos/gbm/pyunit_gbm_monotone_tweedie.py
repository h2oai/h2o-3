import h2o
from h2o.estimators import H2OGradientBoostingEstimator
from tests import pyunit_utils


def gbm_monotone_tweedie_test():
    data = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/autoclaims.csv"))
    data = data.drop(['POLICYNO', 'PLCYDATE', 'CLM_FREQ5', 'CLM_FLAG', 'IN_YY'])
    train, test = data.split_frame([0.8], seed=123)
    response = "CLM_AMT5"

    monotone_constraints = {
        "MVR_PTS": 1,
    }

    gbm_regular = H2OGradientBoostingEstimator(seed=42, distribution="tweedie")
    gbm_regular.train(y=response, training_frame=train, validation_frame=test)
    print(gbm_regular.varimp(use_pandas=True))
    top_3_vars_regular = gbm_regular.varimp(use_pandas=True).ix[:, 'variable'].head(3).tolist()
    assert "MVR_PTS" in top_3_vars_regular

    gbm_mono = H2OGradientBoostingEstimator(monotone_constraints=monotone_constraints, seed=42, distribution="tweedie")
    gbm_mono.train(y=response, training_frame=train, validation_frame=test)
    print(gbm_regular.varimp(use_pandas=True))
    top_3_vars_mono = gbm_mono.varimp(use_pandas=True).ix[:, 'variable'].head(3).tolist()

    # monotone constraints didn't affect the variable importance
    assert top_3_vars_mono == top_3_vars_regular

    # train a model with opposite constraint on MVR_PTS
    gbm_adverse = H2OGradientBoostingEstimator(seed=42, distribution="tweedie",
                                               monotone_constraints={"MVR_PTS": -1})
    gbm_adverse.train(y=response, training_frame=train, validation_frame=test)

    # variable becomes least important to the model
    assert ["MVR_PTS"] == gbm_adverse.varimp(use_pandas=True).ix[:, 'variable'].tail(1).tolist()


if __name__ == "__main__":
    pyunit_utils.standalone_test(gbm_monotone_tweedie_test)
else:
    gbm_monotone_tweedie_test()
