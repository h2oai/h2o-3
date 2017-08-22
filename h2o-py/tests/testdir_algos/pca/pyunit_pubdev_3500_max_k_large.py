from __future__ import print_function
import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.transforms.decomposition import H2OPCA
from random import randint

def pca_max_k():
    data = h2o.upload_file(pyunit_utils.locate("bigdata/laptop/jira/rotterdam.csv.zip"))
    y = set(["relapse"])
    x = list(set(data.names) - y)

    buildModel = [False, False, False, False]
    buildModel[randint(0, len(buildModel)-1)] = True
    # test 1

    if buildModel[0]:
        pcaGramSVD = H2OPCA(k=-1, transform="STANDARDIZE", pca_method="GramSVD", impute_missing=True, max_iterations=100)
        pcaGramSVD.train(x, training_frame=data)
        pcaPower = H2OPCA(k=-1, transform="STANDARDIZE", pca_method="Power", impute_missing=True, max_iterations=100,
                      seed=12345)
        pcaPower.train(x, training_frame=data)

        # compare singular values and stuff with GramSVD
        print("@@@@@@  Comparing eigenvalues between GramSVD and Power...\n")
        pyunit_utils.assert_H2OTwoDimTable_equal(pcaGramSVD._model_json["output"]["importance"],
                                             pcaPower._model_json["output"]["importance"],
                                             ["Standard deviation", "Cumulative Proportion", "Cumulative Proportion"],
                                             tolerance=1)

        correctEigNum = pcaPower.full_parameters["k"]["actual_value"]
        gramSVDNum = len(pcaGramSVD._model_json["output"]["importance"].cell_values[0]) - 1
        powerNum = len(pcaPower._model_json["output"]["importance"].cell_values[0]) - 1
        assert correctEigNum == gramSVDNum, "PCA GramSVD FAIL: expected number of eigenvalues: " + correctEigNum + \
                                        ", actual: " + gramSVDNum + "."
        assert correctEigNum == powerNum, "PCA Power FAIL: expected number of eigenvalues: " + correctEigNum + \
                                      ", actual: " + powerNum + "."

    # Randomized and GLRM does not have wide dataset implementation.  Check with smaller datasets
    # test 2
    data = h2o.upload_file(pyunit_utils.locate("smalldata/prostate/prostate_cat.csv"))
    x = list(set(data.names))
    if buildModel[1]:
        pcaRandomized = H2OPCA(k=-1, transform="STANDARDIZE", pca_method="Randomized",
                               impute_missing=True, max_iterations=100, seed=12345)
        pcaRandomized.train(x, training_frame=data)

        pcaPower = H2OPCA(k=-1, transform="STANDARDIZE", pca_method="Power",
                          impute_missing=True, max_iterations=100, seed=12345)
        pcaPower.train(x, training_frame=data)
        # eigenvalues between the PCA and Randomize should be close, I hope...
        print("@@@@@@  Comparing eigenvalues between Randomized and Power PCA...\n")
        pyunit_utils.assert_H2OTwoDimTable_equal(pcaRandomized._model_json["output"]["importance"],
                                                 pcaPower._model_json["output"]["importance"],
                                                 ["Standard deviation", "Cumulative Proportion", "Cumulative Proportion"])
     # test 3
    if buildModel[2]:
        # should still work with rank deficient dataset
        pcaRandomizedF = H2OPCA(k=-1, transform="STANDARDIZE", pca_method="Randomized", use_all_factor_levels=True,
                               impute_missing=True, max_iterations=100, seed=12345)
        pcaRandomizedF.train(x, training_frame=data)
        # should still work with rank deficient dataset
        pcaPowerF = H2OPCA(k=-1, transform="STANDARDIZE", pca_method="Power", use_all_factor_levels=True,
                            impute_missing=True, max_iterations=100, seed=12345)
        pcaPowerF.train(x, training_frame=data)



        # eigenvalues between the PCA and Randomize should be close with rank deficient dataset, I hope...
        print("@@@@@@  Comparing eigenvalues between Randomized and Power PCA with rank deficient dataset...\n")
        pyunit_utils.assert_H2OTwoDimTable_equal(pcaRandomizedF._model_json["output"]["importance"],
                                                 pcaPowerF._model_json["output"]["importance"],
                                                 ["Standard deviation", "Cumulative Proportion", "Cumulative Proportion"])

    # test 4
    if buildModel[3]:
        pcaGLRM = H2OPCA(k=-1, transform="STANDARDIZE", pca_method="GLRM", use_all_factor_levels=True,
                         max_iterations=100, seed=12345)
        pcaGLRM.train(x, training_frame=data)
        correctEigNum = pcaGLRM.full_parameters["k"]["actual_value"]
        glrmNum = len(pcaGLRM._model_json["output"]["importance"].cell_values[0]) - 1
        assert correctEigNum == glrmNum, "PCA GLRM FAIL: expected number of eigenvalues: " + correctEigNum + \
                                         ", actual: " + glrmNum + "."


if __name__ == "__main__":
    pyunit_utils.standalone_test(pca_max_k)
else:
    pca_max_k()
