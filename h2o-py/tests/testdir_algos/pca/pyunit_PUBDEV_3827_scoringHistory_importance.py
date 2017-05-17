from __future__ import print_function
from builtins import range
from random import randint
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.transforms.decomposition import H2OPCA

def pca_scoring_history_importance():
    """
    This test aims to check and make sure PCA returns the scoring history and importance which are
    reported missing for certain PCA mode.  Apart from changing the PCA mode, I throw in the transform
    type to test as well randomly.
    """
    transform_types = ["NONE", "STANDARDIZE", "NORMALIZE", "DEMEAN", "DESCALE"]
    transformN = transform_types[randint(0, len(transform_types)-1)]

    print("Importing australia.csv data...\n")
    australia = h2o.upload_file(pyunit_utils.locate("smalldata/extdata/australia.csv"))
    col_indices = list(range(0, australia.ncol))

    print("transform is {0}.\n".format(transformN))
    # checking out PCA with GramSVD
    print("@@@@@@  Building PCA with GramSVD...\n")
    gramSVD = H2OPCA(k=3, transform=transformN)
    gramSVD.train(x=col_indices, training_frame=australia)

    # check PCA with PCA set to Randomized
    print("@@@@@@  Building PCA with Randomized...\n")
    randomizedPCA = H2OPCA(k=3, transform=transformN, pca_method="Randomized", compute_metrics=True,
                         use_all_factor_levels=True)
    randomizedPCA.train(x=col_indices, training_frame=australia)

    # compare singular values and stuff with GramSVD
    print("@@@@@@  Comparing eigenvalues between GramSVD and Randomized...\n")
    pyunit_utils.assert_H2OTwoDimTable_equal(gramSVD._model_json["output"]["importance"],
                                           randomizedPCA._model_json["output"]["importance"],
                                           ["Standard deviation", "Cumulative Proportion", "Cumulative Proportion"],
                                           tolerance=1e-3)
    print("@@@@@@  Comparing eigenvectors between GramSVD and Randomized...\n")
    # compare singular vectors
    pyunit_utils.assert_H2OTwoDimTable_equal(gramSVD._model_json["output"]["eigenvectors"],
                                           randomizedPCA._model_json["output"]["eigenvectors"],
                                           randomizedPCA._model_json["output"]["names"], tolerance=5e-2,
                                           check_sign=True)

    # check PCA with PCA set to Power
    print("@@@@@@  Building PCA with Power...\n")
    powerPCA = H2OPCA(k=3, transform=transformN, pca_method="Power", compute_metrics=True, use_all_factor_levels=True)
    powerPCA.train(x=col_indices, training_frame=australia)

    # compare singular values and stuff with GramSVD
    print("@@@@@@  Comparing eigenvalues between GramSVD and Power...\n")
    pyunit_utils.assert_H2OTwoDimTable_equal(gramSVD._model_json["output"]["importance"],
                                           powerPCA._model_json["output"]["importance"],
                                           ["Standard deviation", "Cumulative Proportion", "Cumulative Proportion"])
    print("@@@@@@  Comparing eigenvectors between GramSVD and Power...\n")
    # compare singular vectors
    pyunit_utils.assert_H2OTwoDimTable_equal(gramSVD._model_json["output"]["eigenvectors"],
                                           powerPCA._model_json["output"]["eigenvectors"],
                                           powerPCA._model_json["output"]["names"], tolerance=1e-5, check_sign=True)

    # check PCA with PCA set to GLRM
    print("@@@@@@  Building PCA with GLRM...\n")
    glrmPCA = H2OPCA(k=3, transform=transformN, pca_method="GLRM", compute_metrics=True, use_all_factor_levels=True)
    glrmPCA.train(x=col_indices, training_frame=australia)

    # compare singular values and stuff with GramSVD
    print("@@@@@@  Comparing eigenvalues between GramSVD and GLRM...\n")
    pyunit_utils.assert_H2OTwoDimTable_equal(gramSVD._model_json["output"]["importance"],
                                           glrmPCA._model_json["output"]["importance"],
                                           ["Standard deviation", "Cumulative Proportion", "Cumulative Proportion"],
                                           tolerance=2e-2)
    print("@@@@@@  Comparing eigenvectors between GramSVD and GLRM...\n")
    # compare singular vectors
    pyunit_utils.assert_H2OTwoDimTable_equal(gramSVD._model_json["output"]["eigenvectors"],
                                           glrmPCA._model_json["output"]["eigenvectors"],
                                           glrmPCA._model_json["output"]["names"], tolerance=2e-1,check_sign=True)

    # make sure we find the scoring history and it is not empty for all the PCA modes
    # just check and make sure the cell_values exceed 0
    assert len(gramSVD._model_json["output"]["scoring_history"].cell_values) > 0, "PCA Scoring history setting " \
                                                                                "pca_method to GramSVD is empty."
    assert len(powerPCA._model_json["output"]["scoring_history"].cell_values) > 0, "PCA Scoring history setting " \
                                                                                 "pca_method to using is empty."
    assert len(randomizedPCA._model_json["output"]["scoring_history"].cell_values) > 0, "PCA Scoring history setting " \
                                                                                      "pca_method to Randomized is " \
                                                                                      "empty."
    assert len(glrmPCA._model_json["output"]["scoring_history"].cell_values) > 0, "PCA Scoring history setting " \
                                                                                  "pca_method to GLRM is empty."



if __name__ == "__main__":
    pyunit_utils.standalone_test(pca_scoring_history_importance)
else:
    pca_scoring_history_importance()
