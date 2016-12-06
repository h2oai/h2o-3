from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glrm import H2OGeneralizedLowRankEstimator
from h2o.transforms.decomposition import H2OPCA


def glrm_arrests():
  print("Importing USArrests.csv data...")
  arrestsH2O = h2o.upload_file(pyunit_utils.locate("smalldata/pca_test/USArrests.csv"))

  pca_h2o = H2OPCA(k = 4, transform="STANDARDIZE")
  pca_h2o.train(x=list(range(4)), training_frame=arrestsH2O)
  pca_h2o.summary()
  pca_h2o.show()

  print("H2O GLRM on standardized data with quadratic loss:\n")
  glrm_h2o = H2OGeneralizedLowRankEstimator(k=4, transform="STANDARDIZE", loss="Quadratic", gamma_x=0, gamma_y=0,
                                            init="SVD", recover_svd=True)
  glrm_h2o.train(x=arrestsH2O.names, training_frame=arrestsH2O)
  glrm_h2o.show()

  # compare table values and make sure they are the same between PCA and GLRM
  assert pyunit_utils.equal_2D_tables(pca_h2o._model_json["output"]["importance"]._cell_values,
                                      glrm_h2o._model_json["output"]["importance"]._cell_values, tolerance=1e-4), \
    "PCA and GLRM variance metrics do not agree.  Fix it please."

  sys.stdout.flush()

if __name__ == "__main__":
  pyunit_utils.standalone_test(glrm_arrests)
else:
  glrm_arrests()
