import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


def link_correct_default():
  print("Reading in original prostate data.")
  h2o_data = h2o.upload_file(path=pyunit_utils.locate("smalldata/prostate/prostate.csv.zip"))

  print("Compare models with link unspecified and canonical link specified.")
  print("GAUSSIAN: ")
  h2o_model_unspecified = H2OGeneralizedLinearEstimator(family="gaussian")
  h2o_model_unspecified.train(x=range(1,8), y=8, training_frame=h2o_data)

  h2o_model_specified = H2OGeneralizedLinearEstimator(family="gaussian", link="identity")
  h2o_model_specified.train(x=range(1,8), y=8, training_frame=h2o_data)

  assert h2o_model_specified._model_json['output']['coefficients_table'].cell_values == \
         h2o_model_unspecified._model_json['output']['coefficients_table'].cell_values, "coefficient should be equal"

  print("BINOMIAL: ")
  h2o_model_unspecified = H2OGeneralizedLinearEstimator(family="binomial")
  h2o_model_unspecified.train(x=range(2,9), y=1, training_frame=h2o_data)

  h2o_model_specified = H2OGeneralizedLinearEstimator(family="binomial", link="logit")
  h2o_model_specified.train(x=range(2,9), y=1, training_frame=h2o_data)
  assert h2o_model_specified._model_json['output']['coefficients_table'].cell_values == \
         h2o_model_unspecified._model_json['output']['coefficients_table'].cell_values, "coefficient should be equal"

  print("POISSON: ")
  h2o_model_unspecified = H2OGeneralizedLinearEstimator(family="poisson")
  h2o_model_unspecified.train(x=range(2,9), y=1, training_frame=h2o_data)
  h2o_model_specified = H2OGeneralizedLinearEstimator(family="poisson", link="log")
  h2o_model_specified.train(x=range(2,9), y=1, training_frame=h2o_data)
  assert h2o_model_specified._model_json['output']['coefficients_table'].cell_values == \
         h2o_model_unspecified._model_json['output']['coefficients_table'].cell_values, "coefficient should be equal"

  print("GAMMA: ")
  h2o_model_unspecified = H2OGeneralizedLinearEstimator(family="gamma")
  h2o_model_unspecified.train(x=range(3,9), y=2, training_frame=h2o_data)
  h2o_model_specified = H2OGeneralizedLinearEstimator(family="gamma", link="inverse")
  h2o_model_specified.train(x=range(3,9), y=2, training_frame=h2o_data)
  assert h2o_model_specified._model_json['output']['coefficients_table'].cell_values == \
         h2o_model_unspecified._model_json['output']['coefficients_table'].cell_values, "coefficient should be equal"



if __name__ == "__main__":
  pyunit_utils.standalone_test(link_correct_default)
else:
  link_correct_default()
