from past.utils import old_div
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


def interactions():
  df = h2o.import_file(pyunit_utils.locate("smalldata/airlines/allyears2k_headers.zip"))
  XY = [df.names[i-1] for i in [1,2,3,4,6,8,9,13,17,18,19,31]]
  interactions = [XY[i-1] for i in [5,7,9]]
  assert interactions == ["CRSDepTime", "UniqueCarrier", "Origin"]
  m = H2OGeneralizedLinearEstimator(lambda_search=True, family="binomial", interactions=interactions)
  m.train(x=XY[:len(XY)], y=XY[-1],training_frame=df)
  coef_m = m._model_json['output']['coefficients_table']

  interaction_pairs = [("CRSDepTime", "UniqueCarrier"), ("CRSDepTime", "Origin"), ("UniqueCarrier", "Origin")]
  mexp = H2OGeneralizedLinearEstimator(lambda_search=True, family="binomial", interaction_pairs=interaction_pairs)
  mexp.train(x=XY[:len(XY)], y=XY[-1],training_frame=df)
  coef_mexp = mexp._model_json['output']['coefficients_table']

  assert coef_m["names"] == coef_mexp["names"]


if __name__ == "__main__":
  pyunit_utils.standalone_test(interactions)
else:
  interactions()
