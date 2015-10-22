import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
import random
from h2o.estimators.glrm import H2OGeneralizedLowRankEstimator


def glrm_iris():
  print "Importing iris_wheader.csv data..."
  irisH2O = h2o.upload_file(pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
  irisH2O.describe()

  for trans in ["NONE", "DEMEAN", "DESCALE", "STANDARDIZE"]:
    rank = random.randint(1,7)
    gx = random.uniform(0,1)
    gy = random.uniform(0,1)

    print "H2O GLRM with rank k = " + str(rank) + ", gamma_x = " + str(gx) + ", gamma_y = " + str(gy) + ", transform = " + trans
    glrm_h2o = H2OGeneralizedLowRankEstimator(k=rank, loss="Quadratic", gamma_x=gx, gamma_y=gy, transform=trans)
    glrm_h2o.train(x=irisH2O.names, training_frame=irisH2O)
    glrm_h2o.show()

    print "Impute original data from XY decomposition"
    pred_h2o = glrm_h2o.predict(irisH2O)
    pred_h2o.describe()



if __name__ == "__main__":
  pyunit_utils.standalone_test(glrm_iris)
else:
  glrm_iris()
