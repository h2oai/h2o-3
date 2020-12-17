#!/usr/bin/env python
# -*- encoding: utf-8 -*-
import h2o
import os
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from tests import pyunit_utils


def save_load_mode_with_cv():
    prostate = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    prostate["CAPSULE"] = prostate["CAPSULE"].asfactor()

    prostate_gbm = H2OGradientBoostingEstimator(nfolds=2, keep_cross_validation_predictions=True)
    prostate_gbm.train(x=["AGE", "RACE", "PSA", "DCAPS"], y="CAPSULE", training_frame=prostate)
    path = pyunit_utils.locate("results")

    model_path = h2o.save_model(prostate_gbm, path=path, force=True, export_cv_predictions=True)
    assert os.path.isfile(model_path), "Expected model artifact {0} to exist, but it does not.".format(model_path)

    h2o.remove_all()

    prostate_gbm_reloaded = h2o.load_model(model_path)
    assert isinstance(prostate_gbm_reloaded, H2OGradientBoostingEstimator), \
        "Expected H2OGradientBoostingEstimator, but got {0}".format(prostate_gbm_reloaded)

    holdout_frame_id = prostate_gbm.cross_validation_holdout_predictions().frame_id
    assert h2o.get_frame(holdout_frame_id) is not None


if __name__ == "__main__":
    pyunit_utils.standalone_test(save_load_mode_with_cv)
else:
    save_load_mode_with_cv()
