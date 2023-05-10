import h2o
from h2o.estimators import H2OGradientBoostingEstimator
from tests import pyunit_utils
from sklearn.datasets import fetch_california_housing


def gbm_monotone_smoke_test():
    cal_housing = fetch_california_housing()
    data = h2o.H2OFrame(cal_housing.data, column_names=cal_housing.feature_names)

    data["target"] = h2o.H2OFrame(cal_housing.target)

    train, test = data.split_frame([0.6], seed=123)

    feature_names = ['MedInc', 'AveOccup', 'HouseAge']
    monotone_constraints = {"MedInc": 1, "AveOccup": -1, "HouseAge": 1}

    gbm_mono = H2OGradientBoostingEstimator(monotone_constraints=monotone_constraints, seed=42)
    gbm_mono.train(x=feature_names, y="target", training_frame=train, validation_frame=test)


if __name__ == "__main__":
    pyunit_utils.standalone_test(gbm_monotone_smoke_test)
else:
    gbm_monotone_smoke_test()
