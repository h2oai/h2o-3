import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.pca import H2OPrincipalComponentAnalysisEstimator as H2OPCA
from random import randint

def pca_max_k():
    data = h2o.upload_file(pyunit_utils.locate("smalldata/pca_test/SDSS_quasar.txt.zip"))
    x = list(set(data.names))

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

    pcaRandomized = H2OPCA(k=-1, transform="STANDARDIZE", pca_method="Randomized",
                           impute_missing=True, max_iterations=100, seed=12345)
    pcaRandomized.train(x, training_frame=data)

    # eigenvalues between the PCA and Randomize should be close, I hope...
    print("@@@@@@  Comparing eigenvalues between Randomized and Power PCA...\n")
    pyunit_utils.assert_H2OTwoDimTable_equal(pcaRandomized._model_json["output"]["importance"],
                                             pcaPower._model_json["output"]["importance"],
                                             ["Standard deviation", "Cumulative Proportion", "Cumulative Proportion"])

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
