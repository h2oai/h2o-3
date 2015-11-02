import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils


def weights_gamma():

  htable  = h2o.upload_file(pyunit_utils.locate("smalldata/gbm_test/moppe.csv"))
  htable["premiekl"] = htable["premiekl"].asfactor()
  htable["moptva"] = htable["moptva"].asfactor()
  htable["zon"] = htable["zon"]
  from h2o.estimators.gbm import H2OGradientBoostingEstimator
  hh = H2OGradientBoostingEstimator(distribution="gamma",
                                    ntrees=20,
                                    max_depth=1,
                                    min_rows=1,
                                    learn_rate=1)
  hh.train(x=range(3), y="medskad", training_frame=htable, weights_column="antskad")
  ph = hh.predict(htable)

  assert abs(8.804447-hh._model_json['output']['init_f']) < 1e-6*8.804447
  assert abs(3751.01-ph[0].min()) < 1e-4*3751.01
  assert abs(15298.87-ph[0].max()) < 1e-4*15298.87
  assert abs(8121.98-ph[0].mean()[0]) < 1e-4*8121.98



if __name__ == "__main__":
  pyunit_utils.standalone_test(weights_gamma)
else:
  weights_gamma()
