import h2o
from h2o.estimators import H2OGradientBoostingEstimator
from tests import pyunit_utils
import numpy as np


def f(x):
    """The function to predict."""
    return x * np.sin(x)


def gbm_monotone_quantile_test():
    # generate data
    x = np.atleast_1d(np.random.uniform(0, 10.0, size=100)).T

    y = f(x).ravel()

    dy = 1.5 + 1.0 * np.random.random(y.shape)
    noise = np.random.normal(0, dy)
    y += noise
    
    train = h2o.H2OFrame({'x': x.tolist(), 'y': y.tolist()})
    
    # train a model with 1 constraint on x
    gbm_mono = H2OGradientBoostingEstimator(seed=42, distribution="quantile", monotone_constraints={"x": 1})
    gbm_mono.train(y='y', training_frame=train)

    mono_pred = gbm_mono.predict(train).as_data_frame().iloc[:,0].tolist()
    x_sorted, mono_pred_sorted = zip(*sorted(zip(x, mono_pred)))
    assert all(x <= y for x, y in zip(mono_pred_sorted, mono_pred_sorted[1:])), "The prediction should be monotone."

    # train a model with -1 constraint on x
    gbm_adverse = H2OGradientBoostingEstimator(seed=42, distribution="quantile", monotone_constraints={"x": -1})
    gbm_adverse.train(y='y', training_frame=train)

    adverse_pred = gbm_adverse.predict(train).as_data_frame().iloc[:,0].tolist()
    x_sorted, adverse_pred_sorted = zip(*sorted(zip(x, adverse_pred)))
    assert all(x >= y for x, y in zip(adverse_pred_sorted, adverse_pred_sorted[1:])), \
        "The prediction should be monotone."


if __name__ == "__main__":
    pyunit_utils.standalone_test(gbm_monotone_quantile_test)
else:
    gbm_monotone_quantile_test()
