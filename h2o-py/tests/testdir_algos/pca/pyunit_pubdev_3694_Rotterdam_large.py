from __future__ import print_function
from builtins import str
from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.transforms.decomposition import H2OPCA



def pca_3694_rotterdam():


  print("Importing Rotterdam.csv data...")
  rotterdamH2O = h2o.upload_file(pyunit_utils.locate("bigdata/laptop/jira/rotterdam.csv.zip"))

  y = set(["relapse"])
  x = list(set(rotterdamH2O.names)-y)

  pca_h2o = H2OPCA(k=8, impute_missing=True, transform="STANDARDIZE")
  pca_h2o.train(x=x, training_frame=rotterdamH2O)
  pred = pca_h2o.predict(rotterdamH2O)
  assert pred.ncols == 8, "Incorrect column number in prediction frame."

if __name__ == "__main__":
  pyunit_utils.standalone_test(pca_3694_rotterdam)
else:
  pca_3694_rotterdam()
