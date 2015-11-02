import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils


def glrm_arrests():
  print "Importing USArrests.csv data..."
  arrestsH2O = h2o.upload_file(pyunit_utils.locate("smalldata/pca_test/USArrests.csv"))
  arrestsH2O.describe()

  print "H2O initial Y matrix:\n"
  initial_y = [[5.412,  65.24,  -7.54, -0.032],
               [2.212,  92.24, -17.54, 23.268],
               [0.312, 123.24,  14.46,  9.768],
               [1.012,  19.24, -15.54, -1.732]]
  initial_y_h2o = h2o.H2OFrame(initial_y)
  initial_y_h2o.show()

  print "H2O GLRM on de-meaned data with quadratic loss:\n"
  glrm_h2o = h2o.glrm(x=arrestsH2O, k=4, transform="DEMEAN", loss="Quadratic", gamma_x=0, gamma_y=0, init="User", user_y=initial_y_h2o, recover_svd=True)
  glrm_h2o.show()



if __name__ == "__main__":
  pyunit_utils.standalone_test(glrm_arrests)
else:
  glrm_arrests()
