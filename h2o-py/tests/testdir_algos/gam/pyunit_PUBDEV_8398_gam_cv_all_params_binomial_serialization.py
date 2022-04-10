from __future__ import division
from __future__ import print_function
import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator
import random
import tempfile
import os

# test to make sure serlization works with saved models, frames from cv run
def test_gam_model_predict():
    print("Checking cross validation for GAM binomial")
    print("Preparing for data....")
    h2o_data = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/binomial_20_cols_10KRows.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    h2o_data["C3"] = h2o_data["C3"].asfactor()
    h2o_data["C4"] = h2o_data["C4"].asfactor()
    h2o_data["C5"] = h2o_data["C5"].asfactor()
    h2o_data["C6"] = h2o_data["C6"].asfactor()
    h2o_data["C7"] = h2o_data["C7"].asfactor()
    h2o_data["C8"] = h2o_data["C8"].asfactor()
    h2o_data["C9"] = h2o_data["C9"].asfactor()
    h2o_data["C10"] = h2o_data["C10"].asfactor()
    myY = "C21"
    h2o_data["C21"] = h2o_data["C21"].asfactor()

    nfold = random.randint(3, 8)
    h2o_model = H2OGeneralizedAdditiveEstimator(family='binomial', gam_columns=["C11"], scale=[0.0001],
                                                bs = [2],
                                                nfolds=nfold,
                                                keep_cross_validation_models=True,
                                                keep_cross_validation_predictions=True,
                                                keep_cross_validation_fold_assignment=True,
                                                fold_assignment="random")
    h2o_model.train(x=list(range(0, 20)), y=myY, training_frame=h2o_data)
    tmpdir = tempfile.mkdtemp()
    model_path = h2o.save_model(h2o_model, tmpdir)
    xval_predictions = h2o_model.cross_validation_holdout_predictions()
    xval_filename = os.path.join(tmpdir, "xval_predictions.csv")
    h2o.download_csv(xval_predictions, xval_filename)

    xval_fold_assignments = h2o_model.cross_validation_fold_assignment()
    xval_fold_filename = os.path.join(tmpdir, "xval_fold_assignments.csv")
    h2o.download_csv(xval_fold_assignments, xval_fold_filename)

    h2o.remove_all()
    loaded_model = h2o.load_model(model_path)

    xval_predictions_loaded = loaded_model.cross_validation_holdout_predictions()
    xval_predictions_loaded["predict"] = xval_predictions_loaded["predict"].asnumeric()
    xval_pred_original = h2o.import_file(xval_filename)
    pyunit_utils.compare_frames_local(xval_pred_original, xval_predictions_loaded, prob=1.0)

    xval_fold_loaded = loaded_model.cross_validation_fold_assignment()
    xval_fold_original = h2o.import_file(xval_fold_filename)
    pyunit_utils.compare_frames_local(xval_fold_loaded, xval_fold_original, prob=1.0)

    # make sure model can predict
    xval_models = loaded_model.get_xval_models()
    h2o_data = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/binomial_20_cols_10KRows.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    h2o_data["C3"] = h2o_data["C3"].asfactor()
    h2o_data["C4"] = h2o_data["C4"].asfactor()
    h2o_data["C5"] = h2o_data["C5"].asfactor()
    h2o_data["C6"] = h2o_data["C6"].asfactor()
    h2o_data["C7"] = h2o_data["C7"].asfactor()
    h2o_data["C8"] = h2o_data["C8"].asfactor()
    h2o_data["C9"] = h2o_data["C9"].asfactor()
    h2o_data["C10"] = h2o_data["C10"].asfactor()
    myY = "C21"
    h2o_data["C21"] = h2o_data["C21"].asfactor()
    pred_frame = xval_models[0].predict(h2o_data)
    assert pred_frame.nrow == h2o_data.nrow, "Expected number of rows {0}, actual number of rows " \
                                             "{1}".format(pred_frame.nrow, h2o_data.nrow)
    print("Test complete.")


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gam_model_predict)
else:
    test_gam_model_predict()
