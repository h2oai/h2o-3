from builtins import range
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.deeplearning import H2ODeepLearningEstimator
from h2o.estimators.kmeans import H2OKMeansEstimator

def get_model_test():
    prostate = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))

    r = prostate[0].runif()
    train = prostate[r < 0.70]
    test = prostate[r >= 0.70]

    # Regression
    regression_gbm1 = H2OGradientBoostingEstimator(distribution="gaussian")
    regression_gbm1.train(x=[2,3,4,5,6,7,8], y=1, training_frame=train)
    predictions1 = regression_gbm1.predict(test)

    regression_gbm2 = h2o.get_model(regression_gbm1._id)
    assert regression_gbm2._model_json['output']['model_category'] == "Regression"
    predictions2 = regression_gbm2.predict(test)

    for r in range(predictions1.nrow):
        p1 = predictions1[r,0]
        p2 = predictions2[r,0]
        assert p1 == p2, "expected regression predictions to be the same for row {}, but got {} and {}".format(r, p1, p2)

    # Binomial
    train[1] = train[1].asfactor()
    bernoulli_gbm1 = H2OGradientBoostingEstimator(distribution="bernoulli")

    bernoulli_gbm1.train(x=[2,3,4,5,6,7,8],y=1,training_frame=train)
    predictions1 = bernoulli_gbm1.predict(test)

    bernoulli_gbm2 = h2o.get_model(bernoulli_gbm1._id)
    assert bernoulli_gbm2._model_json['output']['model_category'] == "Binomial"
    predictions2 = bernoulli_gbm2.predict(test)

    for r in range(predictions1.nrow):
        p1 = predictions1[r,0]
        p2 = predictions2[r,0]
        assert p1 == p2, "expected binomial predictions to be the same for row {}, but got {} and {}".format(r, p1, p2)

    # Clustering
    benign_h2o = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/benign.csv"))
    km_h2o = H2OKMeansEstimator(k=3)
    km_h2o.train(x=list(range(benign_h2o.ncol)), training_frame=benign_h2o)
    benign_km = h2o.get_model(km_h2o._id)
    assert benign_km._model_json['output']['model_category'] == "Clustering"

    # Multinomial
    train[4] = train[4].asfactor()
    multinomial_dl1 = H2ODeepLearningEstimator(loss="CrossEntropy")
    multinomial_dl1.train(x=[0,1], y=4, training_frame=train)
    predictions1 = multinomial_dl1.predict(test)

    multinomial_dl2 = h2o.get_model(multinomial_dl1._id)
    assert multinomial_dl2._model_json['output']['model_category'] == "Multinomial"
    predictions2 = multinomial_dl2.predict(test)

    for r in range(predictions1.nrow):
        p1 = predictions1[r,0]
        p2 = predictions2[r,0]
        assert p1 == p2, "expected multinomial predictions to be the same for row {0}, but got {1} and {2}" \
                         "".format(r, p1, p2)



if __name__ == "__main__":
    pyunit_utils.standalone_test(get_model_test)
else:
    get_model_test()
