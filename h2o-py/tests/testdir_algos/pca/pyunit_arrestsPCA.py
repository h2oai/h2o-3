from builtins import str
from builtins import range
import sys
sys.path.insert(1,"../../../")

import h2o
from tests import pyunit_utils
from h2o.transforms.decomposition import H2OPCA
from h2o.estimators.pca import H2OPrincipalComponentAnalysisEstimator


def pca_arrests():

  print("Importing USArrests.csv data...")
  arrests = h2o.upload_file(pyunit_utils.locate("smalldata/pca_test/USArrests.csv"))
  arrests.describe()

  # import from h2o.transforms.decomposition
  for i in range(4):
    print("H2O PCA with " + str(i) + " dimensions:\n")
    print("Using these columns: {0}".format(arrests.names))
    pca_h2o = H2OPCA(k = i+1)
    pca_h2o.train(x=list(range(4)), training_frame=arrests)
    # TODO: pca_h2o.show()

  # import from h2o.estimators.pca 
  for i in range(4):
    print("H2O PCA with " + str(i) + " dimensions:\n")
    print("Using these columns: {0}".format(arrests.names))
    pca_h2o = H2OPrincipalComponentAnalysisEstimator(k = i+1)
    pca_h2o.train(x=list(range(4)), training_frame=arrests)


if __name__ == "__main__":
  pyunit_utils.standalone_test(pca_arrests)
else:
  pca_arrests()
