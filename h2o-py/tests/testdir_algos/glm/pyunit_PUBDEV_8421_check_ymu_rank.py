import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm

# check parameters ymu and rank were passed 
def test_binomial_ymu_rank():
    training_data = h2o.import_file(pyunit_utils.locate("smalldata/logreg/benign.csv"))

    Y = 3
    X = [0, 1, 2, 4, 5, 6, 7, 8, 9, 10]
    training_data[Y] = training_data[Y].asfactor()
    model = glm(family="binomial", alpha=0, lambda_=1e-5)
    model.train(x=X, y=Y, training_frame=training_data)
    
    assert len(model._model_json["output"]["ymu"]) > 0
    assert model._model_json["output"]["rank"] == len(model.coef())

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_binomial_ymu_rank)
else:
    test_binomial_ymu_rank()
