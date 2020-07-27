import h2o
from h2o.estimators import H2OGradientBoostingEstimator
from tests import pyunit_utils


def gbm_monotone_quantile_test():
    data = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/autoclaims.csv")
    data = data.drop(['POLICYNO', 'PLCYDATE', 'CLM_FREQ5', 'CLM_FLAG', 'IN_YY'])
    train, test = data.split_frame([0.8], seed=123)
    response = "CLM_AMT5"

    gbm_regular = H2OGradientBoostingEstimator(seed=42, distribution="quantile")
    gbm_regular.train(y=response, training_frame=train, validation_frame=test)

    # train a model with 1 constraint on MVR_PTS
    gbm_mono = H2OGradientBoostingEstimator(seed=42, distribution="quantile", monotone_constraints={"MVR_PTS": 1})
    gbm_mono.train(y=response, training_frame=train, validation_frame=test)

    # train a model with -1 constraint on MVR_PTS
    gbm_adverse = H2OGradientBoostingEstimator(seed=42, distribution="quantile", monotone_constraints={"MVR_PTS": -1})
    gbm_adverse.train(y=response, training_frame=train, validation_frame=test)


if __name__ == "__main__":
    pyunit_utils.standalone_test(gbm_monotone_quantile_test)
else:
    gbm_monotone_quantile_test()
