import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glrm import H2OGeneralizedLowRankEstimator


def glrm_benign():
  print "Importing benign.csv data..."
  benignH2O = h2o.upload_file(pyunit_utils.locate("smalldata/logreg/benign.csv"))
  benignH2O.describe()

  for i in range(8,16,2):
    print "H2O GLRM with rank " + str(i) + " decomposition:\n"
    glrm_h2o = H2OGeneralizedLowRankEstimator(k=i, init="SVD", recover_svd=True)
    glrm_h2o.train(x=benignH2O.names, training_frame=benignH2O)
    glrm_h2o.show()



if __name__ == "__main__":
  pyunit_utils.standalone_test(glrm_benign)
else:
  glrm_benign()
