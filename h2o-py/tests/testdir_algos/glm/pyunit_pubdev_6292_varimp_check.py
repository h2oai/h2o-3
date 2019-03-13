from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

# check varimp for Binomial, Multinomial, Regression at least.

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
    buildModelCheckVarimp(h2o_data, myX, myY, "gaussian", pValues=True)
 

def buildModelCheckVarimp(training_frame, x_indices, y_index, family, pValues=False):
    model = H2OGeneralizedLinearEstimator(family=family, Lambda=0, compute_p_values=pValues)
    model.train(training_frame=training_frame, x=x_indices, y=y_index)
    varimp = model.varimp()
    print(varimp)
    assert len(varimp)==len(x_indices), "expected varimp length: {0}, actual varimp length:" \
                                        " {1}".format(len(x_indices), len(varimp))
    #model.varimp_plot()


if __name__ == "__main__":
  pyunit_utils.standalone_test(testvarimp)
else:
    testvarimp()
