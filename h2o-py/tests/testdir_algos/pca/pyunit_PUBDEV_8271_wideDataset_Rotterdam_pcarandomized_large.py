from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from random import randint
from tests import pyunit_utils
from h2o.estimators.pca import H2OPrincipalComponentAnalysisEstimator as H2OPCA
from h2o.estimators.glrm import H2OGeneralizedLowRankEstimator


# This test aims to test that our PCA works with wide datasets for the following PCA methods:
# 1. GramSVD: PUBDEV-3694;
# 2. Power: PUBDEV-3858
#
# It will compare the eigenvalues and eigenvectors obtained with the various methods and they should agree
# to within certain tolerance.

def pca_wideDataset_rotterdam_pcarandomized():
    tol = 2e-5
    h2o.remove_all()
    print("Importing Rotterdam.csv data...")
    rotterdamH2O = h2o.upload_file(pyunit_utils.locate("bigdata/laptop/jira/rotterdam.csv.zip"))
    y = set(["relapse"])
    x = list(set(rotterdamH2O.names)-y)

    print("------  Testing Randomized PCA --------")
    gramSVD = H2OPCA(k=8, impute_missing=True, transform="NONE", seed=12345)
    gramSVD.train(x=x, training_frame=rotterdamH2O)
    randomizedPCA = H2OPCA(k=8, impute_missing=True, transform="NONE", pca_method="Randomized", seed=12345,
                           max_iterations=5)  # power
    randomizedPCA.train(x=x, training_frame=rotterdamH2O)

    # compare singular values and stuff with GramSVD
    print("@@@@@@  Comparing eigenvalues between GramSVD and Randomized...\n")
    pyunit_utils.assert_H2OTwoDimTable_equal(gramSVD._model_json["output"]["importance"],
                                             randomizedPCA._model_json["output"]["importance"],
                                             ["Standard deviation", "Cumulative Proportion", "Cumulative Proportion"],
                                             tolerance=1e-1, check_all=False)

    print("@@@@@@  Comparing eigenvectors between GramSVD and Randomized...\n")
    # compare singular vectors
    pyunit_utils.assert_H2OTwoDimTable_equal(gramSVD._model_json["output"]["eigenvectors"],
                                             randomizedPCA._model_json["output"]["eigenvectors"],
                                             randomizedPCA._model_json["output"]["names"], tolerance=tol,
                                             check_sign=True, check_all=False)


if __name__ == "__main__":
    pyunit_utils.standalone_test(pca_wideDataset_rotterdam_pcarandomized)
else:
    pca_wideDataset_rotterdam_pcarandomized()
