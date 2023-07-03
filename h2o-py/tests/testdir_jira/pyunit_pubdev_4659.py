import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


def test_4659():
    
    train_h2o = h2o.import_file(pyunit_utils.locate("smalldata/logreg/prostate_train.csv"))
    test_h2o = h2o.import_file(pyunit_utils.locate("smalldata/logreg/prostate_test.csv"))
    
    # remove response column
    test_h2o = test_h2o[:,2:]
    
    h2o_glm = H2OGeneralizedLinearEstimator()
    h2o_glm.train(training_frame=train_h2o,x=[2,3,4,5,6,7], y=1)
    
    h2o_glm_predictions = h2o_glm.predict(test_data=test_h2o).as_data_frame()

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_4659)
else:
    test_4659()


