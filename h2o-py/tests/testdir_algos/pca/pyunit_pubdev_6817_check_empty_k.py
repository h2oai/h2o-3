from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.pca import H2OPrincipalComponentAnalysisEstimator as H2OPCA



def pca_prostate():
  print("Importing prostate.csv data...\n")
  prostate = h2o.upload_file(pyunit_utils.locate("smalldata/logreg/prostate.csv"))

  print("Converting CAPSULE, RACE, DPROS and DCAPS columns to factors")
  prostate["CAPSULE"] = prostate["CAPSULE"].asfactor()
  prostate["RACE"] = prostate["RACE"].asfactor()
  prostate["DPROS"] = prostate["DPROS"].asfactor()
  prostate["DCAPS"] = prostate["DCAPS"].asfactor()
  prostate.describe()

  fitPCA = H2OPCA(k=1, transform="NONE", pca_method="Power", seed=1234)
  fitPCA.train(x=list(range(2,9)), training_frame=prostate)
  fitPCA_noK = H2OPCA(transform="NONE", pca_method="Power", seed=1234)
  fitPCA_noK.train(x=list(range(2,9)), training_frame=prostate)
  pred = fitPCA.predict(prostate)
  predNoK = fitPCA_noK.predict(prostate)
  pyunit_utils.compare_frames_local(pred, predNoK, prob=1, tol=1e-10)

if __name__ == "__main__":
  pyunit_utils.standalone_test(pca_prostate)
else:
  pca_prostate()
