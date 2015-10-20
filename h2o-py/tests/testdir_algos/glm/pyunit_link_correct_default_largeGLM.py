import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils




def link_correct_default():
	
	

	print("Reading in original prostate data.")
	h2o_data = h2o.upload_file(path=pyunit_utils.locate("smalldata/prostate/prostate.csv.zip"))

	print("Compare models with link unspecified and canonical link specified.")
	print("GAUSSIAN: ")
	h2o_model_unspecified = h2o.glm(x=h2o_data[1:8], y=h2o_data[8], family="gaussian")
	h2o_model_specified = h2o.glm(x=h2o_data[1:8], y=h2o_data[8], family="gaussian", link="identity")
	assert h2o_model_specified._model_json['output']['coefficients_table'].cell_values == \
		   h2o_model_unspecified._model_json['output']['coefficients_table'].cell_values, "coefficient should be equal"

	print("BINOMIAL: ")
	h2o_model_unspecified = h2o.glm(x=h2o_data[2:9], y=h2o_data[1], family="binomial")
	h2o_model_specified = h2o.glm(x=h2o_data[2:9], y=h2o_data[1], family="binomial", link="logit")
	assert h2o_model_specified._model_json['output']['coefficients_table'].cell_values == \
		   h2o_model_unspecified._model_json['output']['coefficients_table'].cell_values, "coefficient should be equal"

	print("POISSON: ")
	h2o_model_unspecified = h2o.glm(x=h2o_data[2:9], y=h2o_data[1], family="poisson")
	h2o_model_specified = h2o.glm(x=h2o_data[2:9], y=h2o_data[1], family="poisson", link="log")
	assert h2o_model_specified._model_json['output']['coefficients_table'].cell_values == \
		   h2o_model_unspecified._model_json['output']['coefficients_table'].cell_values, "coefficient should be equal"

	print("GAMMA: ")
	h2o_model_unspecified = h2o.glm(x=h2o_data[3:9], y=h2o_data[2], family="gamma")
	h2o_model_specified = h2o.glm(x=h2o_data[3:9], y=h2o_data[2], family="gamma", link="inverse")
	assert h2o_model_specified._model_json['output']['coefficients_table'].cell_values == \
		   h2o_model_unspecified._model_json['output']['coefficients_table'].cell_values, "coefficient should be equal"



if __name__ == "__main__":
    pyunit_utils.standalone_test(link_correct_default)
else:
	link_correct_default()
