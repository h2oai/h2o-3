import sys
sys.path.insert(1, "../../")
import h2o
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from tests import pyunit_utils


def pubdev_7064():
    insurance = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/insurance.csv"))
    
    predictors = insurance.columns[0:4]
    response = 'Claims'
    insurance['Group'] = insurance['Group'].asfactor()
    insurance['Age'] = insurance['Age'].asfactor()
        
    insurance_gbm_exception = H2OGradientBoostingEstimator(distribution="huber", huber_alpha = 0.9, nfolds=3, seed=1234, sample_rate=0.25)
    insurance_gbm_exception.train(x = predictors, y = response, training_frame = insurance)


if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_7064)
else:
    pubdev_7064()
