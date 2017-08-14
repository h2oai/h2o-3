from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glrm import H2OGeneralizedLowRankEstimator


def glrm_size_benchmark():
  frame = h2o.upload_file(pyunit_utils.locate("smalldata/prostate/prostate_cat.csv"))
  model = H2OGeneralizedLowRankEstimator(k=8, init="svd", recover_svd=True)


  model.train(x=frame.names, training_frame=frame)
  try:
    result_dir = pyunit_utils.locate("results")
    h2o.download_pojo(model, path=result_dir)
    assert False, "Java backend did not return correct client error message."
  except Exception as e:
    print(e)
    if "GLRM" not in e.args[0]:
      assert False, "Java backend did not return correct client error message."

if __name__ == "__main__":
  pyunit_utils.standalone_test(glrm_size_benchmark)
else:
  glrm_size_benchmark()
