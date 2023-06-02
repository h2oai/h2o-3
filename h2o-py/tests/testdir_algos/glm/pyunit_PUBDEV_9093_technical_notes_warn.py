import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

# This test is written to obtain the correct technical note warning which should contain this URL in the warning 
# message
def test_GLM_technical_note_warning():
    hdf = h2o.upload_file(pyunit_utils.locate("smalldata/prostate/prostate_complete.csv.zip"))
    test = h2o.upload_file(pyunit_utils.locate("smalldata/prostate/prostate_complete.csv.zip"))
    wt = pyunit_utils.random_dataset_real_only(hdf.nrow, 1, misFrac=0, randSeed=12345)
    wt = wt.abs()
    wt.set_name(0, "weights")
    hdf = hdf.cbind(wt)

    y = "AGE"
    x = ["RACE","DCAPS","PSA","VOL","DPROS","GLEASON"]

    model_h2o_tweedie = H2OGeneralizedLinearEstimator(weights_column = "weights")
    model_h2o_tweedie.train(x=x, y=y, training_frame=hdf, validation_frame=test)   # this should generate a warning message
    
    # check and make sure we get the correct warning message
    warn_phrase = "https://github.com/h2oai/h2o-3/discussions/15512"
    pyunit_utils.checkLogWarning(warn_phrase, wantWarnMessage=True)

if __name__ == "__main__":
  pyunit_utils.standalone_test(test_GLM_technical_note_warning)
else:
  test_GLM_technical_note_warning()
