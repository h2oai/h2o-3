from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from random import randint
from tests import pyunit_utils
from h2o.transforms.decomposition import H2OPCA
from h2o.estimators.glrm import H2OGeneralizedLowRankEstimator


# This test aims to test that our PCA works with wide datasets for the following PCA methods:
# 1. GramSVD: PUBDEV-3694;
# 2. Power: PUBDEV-3858
#
# It will compare the eigenvalues and eigenvectors obtained with the various methods and they should agree
# to within certain tolerance.

def pca_wideDataset_rotterdam():
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

    expNum = 0
    if (buildModel[expNum]):
        # special test with GLRM.  Need use_all_levels to be true
        print("------  Testing GLRM PCA --------")
        gramSVD = H2OPCA(k=8, impute_missing=True, transform=transformN, seed=12345, use_all_factor_levels=True)
        gramSVD.train(x=x, training_frame=rotterdamH2O)

        glrmPCA = H2OGeneralizedLowRankEstimator(k=8, transform=transformN, seed=12345, init="Random",
                                                 max_iterations=10, recover_svd=True, regularization_x="None",
                                                 regularization_y="None")
        glrmPCA.train(x=x, training_frame=rotterdamH2O)

        # compare singular values and stuff with GramSVD
        print("@@@@@@  Comparing eigenvectors between GramSVD and GLRM...\n")
        print("@@@@@@  Comparing eigenvalues between GramSVD and GLRM...\n")
        pyunit_utils.assert_H2OTwoDimTable_equal(gramSVD._model_json["output"]["importance"],
                                                 glrmPCA._model_json["output"]["importance"],
                                                 ["Standard deviation", "Cumulative Proportion", "Cumulative Proportion"],
                                                 tolerance=1, check_all=False)

        # compare singular vectors
        pyunit_utils.assert_H2OTwoDimTable_equal(gramSVD._model_json["output"]["eigenvectors"],
                                                 glrmPCA._model_json["output"]["eigenvectors"],
                                                 glrmPCA._model_json["output"]["names"], tolerance=1e-6,
                                                 check_sign=True, check_all=False)
        h2o.remove(gramSVD)
        h2o.remove(glrmPCA)

    expNum=expNum+1
    if (buildModel[expNum]):
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
                                             powerPCA._model_json["output"]["names"], tolerance=1e-6, check_sign=True,
                                             check_all=False)

    expNum=expNum+1
    if (buildModel[expNum]):
        print("------  Testing Randomized PCA --------")
        gramSVD = H2OPCA(k=8, impute_missing=True, transform=transformN, seed=12345)
        gramSVD.train(x=x, training_frame=rotterdamH2O)
        randomizedPCA = H2OPCA(k=8, impute_missing=True, transform=transformN, pca_method="Randomized", seed=12345,
                               max_iterations=5)  # power
        randomizedPCA.train(x=x, training_frame=rotterdamH2O)

        # compare singular values and stuff with GramSVD
        print("@@@@@@  Comparing eigenvalues between GramSVD and Randomized...\n")
        pyunit_utils.assert_H2OTwoDimTable_equal(gramSVD._model_json["output"]["importance"],
                                                 randomizedPCA._model_json["output"]["importance"],
                                                 ["Standard deviation", "Cumulative Proportion", "Cumulative Proportion"],
                                                 tolerance=1e-1, check_all=False)

        print("@@@@@@  Comparing eigenvectors between GramSVD and Power...\n")
        # compare singular vectors
        pyunit_utils.assert_H2OTwoDimTable_equal(gramSVD._model_json["output"]["eigenvectors"],
                                                 randomizedPCA._model_json["output"]["eigenvectors"],
                                                 randomizedPCA._model_json["output"]["names"], tolerance=1e-6,
                                                 check_sign=True, check_all=False)
    h2o.remove_all()


if __name__ == "__main__":
    pyunit_utils.standalone_test(pca_wideDataset_rotterdam)
else:
    pca_wideDataset_rotterdam()
