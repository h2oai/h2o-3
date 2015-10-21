import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils


def checkpoint_new_category_in_predictor():

  sv1 = h2o.upload_file(pyunit_utils.locate("smalldata/iris/setosa_versicolor.csv"))
  sv2 = h2o.upload_file(pyunit_utils.locate("smalldata/iris/setosa_versicolor.csv"))
  vir = h2o.upload_file(pyunit_utils.locate("smalldata/iris/virginica.csv"))

  m1 = h2o.deeplearning(x=sv1[[0,1,2,4]], y=sv1[3], epochs=100)

  m2 = h2o.deeplearning(x=sv2[[0,1,2,4]], y=sv2[3], epochs=200, checkpoint=m1.model_id)

  # attempt to continue building model, but with an expanded categorical predictor domain.
  # this should fail
  try:
    m3 = h2o.deeplearning(x=vir[[0,1,2,4]], y=vir[3], epochs=200, checkpoint=m1.model_id)
    assert False, "Expected continued model-building to fail with new categories introduced in predictor"
  except EnvironmentError:
    pass

  # attempt to predict on new model, but with observations that have expanded categorical predictor domain.
  predictions = m2.predict(vir)

if __name__ == "__main__":
  pyunit_utils.standalone_test(checkpoint_new_category_in_predictor)
else:
  checkpoint_new_category_in_predictor()
