from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

# check varimp for Binomial, Multinomial, Regression directly from model.output._varimp instead of the 
# model._output._standardized_coefficients.
def testvarimp():
    print("Checking variable importance for binomials....")
    training_data = h2o.import_file(pyunit_utils.locate("smalldata/logreg/benign.csv"))
    Y = 3
    X = [0, 1, 2, 4, 5, 6, 7, 8, 9, 10]
    buildModelCheckVarimp(training_data, X, Y, "binomial")

    print("Checking variable importance for multinomials....")
    train = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
    myY = "class"
    mX = list(range(0,4))
    buildModelCheckVarimp(train, mX, myY, "multinomial")

    print("Checking variable importance for regression....")
    h2o_data = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate_complete.csv.zip"))
    myY = "GLEASON"
    myX = ["ID","AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS"]
    buildModelCheckVarimp(h2o_data, myX, myY, "gaussian")
 

def buildModelCheckVarimp(training_frame, x_indices, y_index, family):
    model = H2OGeneralizedLinearEstimator(family=family)
    model.train(training_frame=training_frame, x=x_indices, y=y_index)
    varimp = model.varimp()
    print(varimp)
    standardized_coeff = model._model_json["output"]["standardized_coefficient_magnitudes"]
    # check to make sure varimp and standardized coefficient magnitudes agree
    for ind in range(len(varimp)):
        assert abs(standardized_coeff.cell_values[ind][1]-varimp[ind][1]) < 1e-6, \
            "Expected value: {0}, actual: {1}".format(standardized_coeff.cell_values[ind][1], varimp[ind][1])

if __name__ == "__main__":
  pyunit_utils.standalone_test(testvarimp)
else:
    testvarimp()
