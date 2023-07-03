from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.pca import H2OPrincipalComponentAnalysisEstimator as H2OPCA



def pca_scoring():


  print("Importing arrests.csv data...")
  arrestsH2O = h2o.upload_file(pyunit_utils.locate("smalldata/pca_test/USArrests.csv"))

  print("Run PCA with transform = 'DEMEAN'")


  fitH2O = H2OPCA(k = 4, transform = "DEMEAN")
  fitH2O.train(x=list(range(4)), training_frame=arrestsH2O)
  # TODO: fitH2O.show()

  print("Project training data into eigenvector subspace")
  predH2O = fitH2O.predict(arrestsH2O)
  print("H2O Projection:")
  predH2O.head()



if __name__ == "__main__":
  pyunit_utils.standalone_test(pca_scoring)
else:
  pca_scoring()
