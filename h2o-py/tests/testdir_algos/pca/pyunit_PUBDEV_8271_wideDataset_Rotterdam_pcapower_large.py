from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from random import randint
from tests import pyunit_utils
from h2o.estimators.pca import H2OPrincipalComponentAnalysisEstimator as H2OPCA
from h2o.estimators.glrm import H2OGeneralizedLowRankEstimator


# This test aims to test that our PCA works with wide datasets.  It will compare the eigenvalues and eigenvectors
# obtained with the various methods (gramSVD and power PCA) and they should agree to within certain tolerance.

def pca_wideDataset_rotterdam_pcapower():
    tol = 2e-5
    h2o.remove_all()
    print("Importing Rotterdam.csv data...")
    rotterdamH2O = h2o.upload_file(pyunit_utils.locate("bigdata/laptop/jira/rotterdam.csv.zip"))
    y = set(["relapse"])
    x = list(set(rotterdamH2O.names)-y)

    transform_types = ["NONE", "STANDARDIZE", "NORMALIZE", "DEMEAN", "DESCALE"]
    transformN = transform_types[randint(0, len(transform_types)-1)]
    print("transform used on dataset is {0}.\n".format(transformN))
    buildModel = [False, False, False]
    buildModel[randint(0, len(buildModel)-1)] = True

    print("------  Testing Power PCA --------")
    gramSVD = H2OPCA(k=8, impute_missing=True, transform=transformN, seed=12345)
    gramSVD.train(x=x, training_frame=rotterdamH2O)
    powerPCA = H2OPCA(k=8, impute_missing=True, transform=transformN, pca_method="Power", seed=12345)  # power
    powerPCA.train(x=x, training_frame=rotterdamH2O)
    # compare singular values and stuff with GramSVD
    print("@@@@@@  Comparing eigenvalues between GramSVD and Power...\n")
    pyunit_utils.assert_H2OTwoDimTable_equal(gramSVD._model_json["output"]["importance"],
                                             powerPCA._model_json["output"]["importance"],
                                             ["Standard deviation", "Cumulative Proportion", "Cumulative Proportion"],
                                             tolerance=1e-6, check_all=False)
    print("@@@@@@  Comparing eigenvectors between GramSVD and Power...\n")
    # compare singular vectors

    pyunit_utils.assert_H2OTwoDimTable_equal(gramSVD._model_json["output"]["eigenvectors"],
                                             powerPCA._model_json["output"]["eigenvectors"],
                                             powerPCA._model_json["output"]["names"], tolerance=tol, check_sign=True,
                                             check_all=False)

if __name__ == "__main__":
    pyunit_utils.standalone_test(pca_wideDataset_rotterdam_pcapower)
else:
    pca_wideDataset_rotterdam_pcapower()
