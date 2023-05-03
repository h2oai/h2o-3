import sys
sys.path.insert(1, "../../")
import h2o
from tests import pyunit_utils

def rename_things():
  fr = h2o.import_file(pyunit_utils.locate("smalldata/logreg/prostate.csv"))
  fr.frame_id = "mooochooo"
  print(h2o.ls())
  zz = fr[1:2]
  zz.show()
  zz.frame_id = "black_sheep_LLC"
  print(h2o.ls())
  from h2o.estimators.gbm import H2OGradientBoostingEstimator
  m = H2OGradientBoostingEstimator(ntrees=5, max_depth=2)
  m.train(x=fr.names[2:], y=fr.names[1], training_frame=fr)
  print(m.model_id)
  m.model_id = "my_gbm_model_wwwww"
  print(h2o.ls())
  print(h2o.get_model("my_gbm_model_wwwww"))
  print(h2o.ls())


pyunit_utils.standalone_test(rename_things)
