import sys

from h2o import H2OFrame

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


def glm_ll_aic():
    cars = h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    s = cars[0].runif()
    train = cars[s > 0.2]
    valid = cars[s <= 0.2]
    train_pd = train.as_data_frame(use_pandas=True).dropna()
    train = H2OFrame(train_pd)
    predictors = ["displacement", "power", "weight", "acceleration", "year"]
    response_col = "economy"
    glm = H2OGeneralizedLinearEstimator(calc_like=True, nfolds=3, family="gaussian")
    glm.train(x=predictors, y=response_col, training_frame=train, validation_frame=valid)
    ll = glm.loglikelihood(train=True, valid=True)
    aic = glm.aic(train=True, valid=True)
    print(glm.family)
    print(ll)
    print(aic)

    glm = H2OGeneralizedLinearEstimator(calc_like=True, nfolds=3, family="poisson")
    glm.train(x=predictors, y=response_col, training_frame=train, validation_frame=valid)
    ll = glm.loglikelihood(train=True, valid=True)
    aic = glm.aic(train=True, valid=True)
    print(glm.family)
    print(ll)
    print(aic)

    glm = H2OGeneralizedLinearEstimator(calc_like=True, nfolds=3, family="negativebinomial")
    glm.train(x=predictors, y=response_col, training_frame=train, validation_frame=valid)
    ll = glm.loglikelihood(train=True, valid=True)
    aic = glm.aic(train=True, valid=True)
    print(glm.family)
    print(ll)
    print(aic)

    glm = H2OGeneralizedLinearEstimator(calc_like=True, nfolds=3, family="gamma")
    glm.train(x=predictors, y=response_col, training_frame=train, validation_frame=valid)
    ll = glm.loglikelihood(train=True, valid=True)
    aic = glm.aic(train=True, valid=True)
    print(glm.family)
    print(ll)
    print(aic)

    glm = H2OGeneralizedLinearEstimator(calc_like=True, nfolds=3, family="tweedie", tweedie_variance_power=1.5)
    glm.train(x=predictors, y=response_col, training_frame=train, validation_frame=valid)
    ll = glm.loglikelihood(train=True, valid=True)
    aic = glm.aic(train=True, valid=True)
    print(glm.family)
    print(ll)
    print(aic)


if __name__ == "__main__":
    pyunit_utils.standalone_test(glm_ll_aic)
else:
    glm_ll_aic()
