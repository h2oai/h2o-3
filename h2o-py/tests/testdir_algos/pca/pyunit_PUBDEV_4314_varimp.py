from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.transforms.decomposition import H2OPCA
from h2o.utils.typechecks import assert_is_type
from pandas import DataFrame


# This test aims to test that our PCA works with wide datasets for the following PCA methods:
# 1. GramSVD: PUBDEV-3694;
# 2. Power: PUBDEV-3858
#
# It will compare the eigenvalues and eigenvectors obtained with the various methods and they should agree
# to within certain tolerance.

def pca_pubdev_4314():
    print("Importing prostate_cat.csv data...\n")
    prostate = h2o.upload_file(pyunit_utils.locate("smalldata/prostate/prostate_cat.csv"))
    prostate.describe()
    print("PCA with k = 3, retx = FALSE, transform = 'STANDARDIZE'")
    fitPCA = H2OPCA(k=3, transform="StANDARDIZE", pca_method="GramSVD")
    fitPCA.train(x=list(range(0,8)), training_frame=prostate)
    print(fitPCA.summary())
    varimpPandas = fitPCA.varimp(use_pandas=True)
    assert_is_type(varimpPandas, DataFrame)
    varimpList = fitPCA.varimp()
    print(varimpList)
    assert_is_type(varimpList, list)
    sys.stdout.flush()

if __name__ == "__main__":
    pyunit_utils.standalone_test(pca_pubdev_4314())
else:
    pca_pubdev_4314()
