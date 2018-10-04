import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator

def test_staged_predict_proba():
    prostate = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    prostate["CAPSULE"] = prostate["CAPSULE"].asfactor()

    prostate_gbm_50 = H2OGradientBoostingEstimator(ntrees=50, seed=123)
    prostate_gbm_50.train(x=["AGE","RACE","DPROS","DCAPS","PSA","VOL","GLEASON"],y="CAPSULE", training_frame=prostate)
    preds_50 = prostate_gbm_50.predict(prostate)

    prostate_gbm_10 = H2OGradientBoostingEstimator(ntrees=10, seed=123)
    prostate_gbm_10.train(x=["AGE","RACE","DPROS","DCAPS","PSA","VOL","GLEASON"],y="CAPSULE", training_frame=prostate)
    preds_10 = prostate_gbm_10.predict(prostate)

    staged_preds = prostate_gbm_50.staged_predict_proba(prostate)

    pyunit_utils.compare_frames_local(preds_50["p0"], staged_preds["T50.C1"], prob=1.0)
    pyunit_utils.compare_frames_local(preds_10["p0"], staged_preds["T10.C1"], prob=1.0)

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_staged_predict_proba)
else:
    test_staged_predict_proba()
