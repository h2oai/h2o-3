import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

def test_glm_plot():  
    training_data = h2o.import_file(pyunit_utils.locate("smalldata/logreg/benign.csv"))
    Y = 3
    X = [0, 1, 2, 4, 5, 6, 7, 8, 9, 10]

    model = H2OGeneralizedLinearEstimator(family="binomial", alpha=0, Lambda=1e-5)
    model.train(x=X, y=Y, training_frame=training_data)
    model.plot(metric="objective", server=True)     # make sure graph will not show.

    try:
        model.plot(metric="auc")
        sys.exit(1) # should have invoked an error
    except:
        sys.exit(0) # no problem
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_glm_plot)
else:
    test_glm_plot()
