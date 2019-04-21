from __future__ import print_function
import sys
import os

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glrm import H2OGeneralizedLowRankEstimator

def test_load_glrm():
  print("Importing iris_wheader.csv data...")
  irisH2O = h2o.upload_file(pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
  irisH2O.describe()

  g_model = H2OGeneralizedLowRankEstimator(k=3)
  g_model.train(x=irisH2O.names, training_frame=irisH2O)
  yarch_old = g_model.archetypes()
  x_old = h2o.get_frame(g_model._model_json["output"]["representation_name"])
  predOld = g_model.predict(irisH2O)
  TMPDIR = os.path.normpath(os.path.join(os.path.dirname(os.path.realpath('__file__')), "../..", "results"))

  try:
    TMPDIR = pyunit_utils.locate("results")    # find directory path to results folder
  except:
    os.makedirs(TMPDIR)
  h2o.save_model(g_model, path=TMPDIR, force=True)       # save model
  full_path_filename = os.path.join(TMPDIR, g_model._id)

  h2o.remove(g_model)
  model_reloaded = h2o.load_model(full_path_filename)
  pred = model_reloaded.predict(irisH2O)
  yarch = model_reloaded.archetypes()
  x = h2o.get_frame(model_reloaded._model_json["output"]["representation_name"])

  # assert difference between old and new are close, archetypes should be the same
  pyunit_utils.compare_frames_local(x, x_old, tol=1e-6)
  pyunit_utils.compare_frames_local(pred[0], predOld[0], tol=1)
  for k in range(3):
    pyunit_utils.equal_two_arrays(yarch_old[k], yarch[k], eps = 1e-4, tolerance=1e-10)

  print("glrm model successfully loaded...")


if __name__ == "__main__":
  pyunit_utils.standalone_test(test_load_glrm)
else:
  test_load_glrm()
