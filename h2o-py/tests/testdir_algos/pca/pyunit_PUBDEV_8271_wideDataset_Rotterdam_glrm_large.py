from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from random import randint
from tests import pyunit_utils
from h2o.estimators.pca import H2OPrincipalComponentAnalysisEstimator as H2OPCA
from h2o.estimators.glrm import H2OGeneralizedLowRankEstimator


# This test aims to test that our PCA works with wide datasets.  It will compare the eigenvalues and eigenvectors
# obtained with the various methods (gramSVD and GLRM) and they should agree to within certain tolerance.

def pca_wideDataset_rotterdam_glrm():
    tol = 2e-5
    h2o.remove_all()
    print("Importing Rotterdam.csv data...")
    rotterdamH2O = h2o.upload_file(pyunit_utils.locate("bigdata/laptop/jira/rotterdam.csv.zip"))
    y = set(["relapse"])
    x = list(set(rotterdamH2O.names)-y)

    # special test with GLRM.  Need use_all_levels to be true
    print("------  Testing GLRM PCA --------")
    gramSVD = H2OPCA(k=8, impute_missing=True, transform="DEMEAN", seed=12345, use_all_factor_levels=True)
    gramSVD.train(x=x, training_frame=rotterdamH2O)

    glrmPCA = H2OGeneralizedLowRankEstimator(k=8, transform="DEMEAN", seed=12345, init="Random",
                                             recover_svd=True, regularization_x="None",
                                             regularization_y="None", max_iterations=11)
    glrmPCA.train(x=x, training_frame=rotterdamH2O)

    # compare singular values and stuff with GramSVD
    print("@@@@@@  Comparing eigenvectors and eigenvalues between GramSVD and GLRM...\n")
    pyunit_utils.assert_H2OTwoDimTable_equal(gramSVD._model_json["output"]["importance"],
                                             glrmPCA._model_json["output"]["importance"],
                                             ["Standard deviation", "Cumulative Proportion", "Cumulative Proportion"],
                                             tolerance=1, check_all=False)

    # compare singular vectors
    pyunit_utils.assert_H2OTwoDimTable_equal(gramSVD._model_json["output"]["eigenvectors"],
                                             glrmPCA._model_json["output"]["eigenvectors"],
                                             glrmPCA._model_json["output"]["names"], tolerance=tol,
                                             check_sign=True, check_all=False)


if __name__ == "__main__":
    pyunit_utils.standalone_test(pca_wideDataset_rotterdam_glrm)
else:
    pca_wideDataset_rotterdam_glrm()
