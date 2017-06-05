from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from random import randint
from tests import pyunit_utils
from h2o.transforms.decomposition import H2OPCA


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

    gramSVD = H2OPCA(k=8, impute_missing=True, transform=transformN, seed=12345)
    gramSVD.train(x=x, training_frame=rotterdamH2O)

    buildModel = [False, False]
    buildModel[randint(0, len(buildModel)-1)] = True

    expNum = 0

    if (buildModel[expNum]):
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
        randomizedPCA = H2OPCA(k=8, impute_missing=True, transform=transformN, pca_method="Randomized", seed=12345)  # power
        randomizedPCA.train(x=x, training_frame=rotterdamH2O)

        # compare singular values and stuff with GramSVD
        print("@@@@@@  Comparing eigenvalues between GramSVD and Randomized...\n")
        pyunit_utils.assert_H2OTwoDimTable_equal(gramSVD._model_json["output"]["importance"],
                                                 randomizedPCA._model_json["output"]["importance"],
                                                 ["Standard deviation", "Cumulative Proportion", "Cumulative Proportion"],
                                                 tolerance=1e-3, check_all=False)

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
