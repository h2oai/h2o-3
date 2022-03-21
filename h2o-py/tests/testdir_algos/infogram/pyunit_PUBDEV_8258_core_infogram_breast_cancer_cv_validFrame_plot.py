from __future__ import print_function
import os
import sys

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from h2o.estimators.infogram import H2OInfogram
from tests import pyunit_utils
    
def test_infogram_breast_cancer_cv_fold_column():
    """
    Test to make sure cross-validation are implemented properly using fold_column
    """
    fr = h2o.import_file(path=pyunit_utils.locate("smalldata/admissibleml_test/wdbc_changed.csv"))
    target = "diagnosis"
    fr[target] = fr[target].asfactor()
    
    x = ["radius_mean", "texture_mean", "perimeter_mean", "area_mean",
         "smoothness_mean", "compactness_mean", "concavity_mean", "concave_points_mean", "symmetry_mean",
         "fractal_dimension_mean", "radius_se", "texture_se", "perimeter_se", "area_se", "smoothness_se",
         "compactness_se", "concavity_se", "concave_points_se", "symmetry_se", "fractal_dimension_se",
         "radius_worst", "texture_worst", "perimeter_worst", "area_worst", "smoothness_worst", "compactness_worst",
         "concavity_worst", "concave_points_worst", "symmetry_worst", "fractal_dimension_worst"]
    splits = fr.split_frame(ratios=[0.80])
    train = splits[0]
    test = splits[1]
    n_fold = 5
    infogram_model_cv_valid = H2OInfogram(seed = 12345, top_n_features=50, nfolds=n_fold, fold_assignment="modulo") # model with cross-validation
    infogram_model_cv_valid.train(x=x, y=target, training_frame=train, validation_frame=test)
    infogram_model_cv_valid.plot(title="infogram from training dataset 1", server=True)
    infogram_model_cv_valid.plot(train=True, valid=True, title="infogram from traiing/validation dataset 1", server=True)
    infogram_model_cv_valid.plot(train=True, valid=True, xval=True, title="infogram from training/validation/cv holdout"
                                                                          " dataset 1", server=True)
    relcmi_valid = infogram_model_cv_valid.get_admissible_score_frame(valid=True)
    relcmi_cv = infogram_model_cv_valid.get_admissible_score_frame(xval=True)
    assert relcmi_valid.nrow==relcmi_cv.nrow

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_infogram_breast_cancer_cv_fold_column)
else:
    test_infogram_breast_cancer_cv_fold_column()
