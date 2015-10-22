import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils




def shuffling_large():
    
    

    print("Reading in Arcene training data for binomial modeling.")
    train_data = h2o.upload_file(path=pyunit_utils.locate("smalldata/arcene/shuffle_test_version/arcene.csv"))
    train_data_shuffled = h2o.upload_file(path=pyunit_utils.locate("smalldata/arcene/shuffle_test_version/arcene_shuffled.csv"))


    print("Create model on original Arcene dataset.")
    h2o_model = h2o.glm(x=train_data[0:1000], y=train_data[1000], family="binomial", lambda_search=True, alpha=[0.5])

    print("Create second model on original Arcene dataset.")
    h2o_model_2 = h2o.glm(x=train_data[0:1000], y=train_data[1000], family="binomial", lambda_search=True, alpha=[0.5])

    print("Create model on shuffled Arcene dataset.")
    h2o_model_s = h2o.glm(x=train_data_shuffled[0:1000], y=train_data_shuffled[1000], family="binomial",
                          lambda_search=True, alpha=[0.5])

    print("Assert that number of predictors remaining and their respective coefficients are equal.")

    for x, y in zip(h2o_model._model_json['output']['coefficients_table'].cell_values,h2o_model_2.
            _model_json['output']['coefficients_table'].cell_values):
        assert (type(x[1]) == type(y[1])) and (type(x[2]) == type(y[2])), "coefficients should be the same type"
        if isinstance(x[1],float):
            assert abs(x[1] - y[1]) < 5e-10, "coefficients should be equal"
        if isinstance(x[2],float):
            assert abs(x[2] - y[2]) < 5e-10, "coefficients should be equal"

    for x, y in zip(h2o_model._model_json['output']['coefficients_table'].cell_values,h2o_model_s.
            _model_json['output']['coefficients_table'].cell_values):
        assert (type(x[1]) == type(y[1])) and (type(x[2]) == type(y[2])), "coefficients should be the same type"
        if isinstance(x[1],float):
            assert abs(x[1] - y[1]) < 5e-10, "coefficients should be equal"
        if isinstance(x[2],float):
            assert abs(x[2] - y[2]) < 5e-10, "coefficients should be equal"



if __name__ == "__main__":
    pyunit_utils.standalone_test(shuffling_large)
else:
    shuffling_large()
