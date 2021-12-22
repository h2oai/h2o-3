from __future__ import print_function
from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.pca import H2OPrincipalComponentAnalysisEstimator


def test_pca_importance():
  arrestsH2O = h2o.upload_file(pyunit_utils.locate("smalldata/pca_test/USArrests.csv"))
  fitH2O = H2OPrincipalComponentAnalysisEstimator(k=4, transform="DEMEAN")
  fitH2O.train(x=list(range(4)), training_frame=arrestsH2O)
  assert fitH2O.varimp()


def test_pca_screeplot():
  import matplotlib
  import matplotlib.pyplot as plt
  matplotlib.use("agg")
  arrestsH2O = h2o.upload_file(pyunit_utils.locate("smalldata/pca_test/USArrests.csv"))
  fitH2O = H2OPrincipalComponentAnalysisEstimator(k=4, transform="DEMEAN")
  fitH2O.train(x=list(range(4)), training_frame=arrestsH2O)

  # The following should not fail
  fitH2O.screeplot()
  fitH2O.screeplot(server=True)
  fitH2O.screeplot(type="lines", server=True)
  
  # Free the memory
  plt.close("all")


pyunit_utils.run_tests([
  test_pca_importance,
  test_pca_screeplot
])
