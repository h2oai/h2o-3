from __future__ import division
from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
import os
import tempfile

def test_negativebinomial_GAM_MOJO():
  print("Read in prostate data.")
  h2o_data = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate_complete.csv.zip"))
  test = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate_complete.csv.zip"))
  print("Testing for family: Negative Binomial")
  myX = ["ID","AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS"]
  params = set_params()
  TMPDIR = tempfile.mkdtemp()
  gamModel = pyunit_utils.build_save_model_generic(params, myX, h2o_data, "GLEASON", "gam", TMPDIR) # build and save mojo model
  MOJONAME = pyunit_utils.getMojoName(gamModel._id)

  h2o.download_csv(test[myX], os.path.join(TMPDIR, 'in.csv'))  # save test file, h2o predict/mojo use same file
  pred_h2o, pred_mojo = pyunit_utils.mojo_predict(gamModel, TMPDIR, MOJONAME)  # load model and perform predict
  h2o.download_csv(pred_h2o, os.path.join(TMPDIR, "h2oPred.csv"))
  print("Comparing mojo predict and h2o predict...")
  pyunit_utils.compare_frames_local(pred_h2o, pred_mojo, 0.1, tol=1e-10)    # make sure operation sequence is preserved from Tomk        h2o.save_model(glmOrdinalModel, path=TMPDIR, force=True)  # save model for debugging

def set_params():
    params = {'missing_values_handling': 'MeanImputation', 'family':"negativebinomial", 'theta':0.01, 'link':"log",
              "alpha":0.5, "Lambda":0, "gam_columns":["PSA"], "num_knots":[5]}
    print(params)
    return params

if __name__ == "__main__":
  pyunit_utils.standalone_test(test_negativebinomial_GAM_MOJO)
else:
  test_negativebinomial_GAM_MOJO()
