import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


def glm_control_variables():

    cars = h2o.upload_file(pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    cars = cars[cars["economy_20mpg"].isna() == 0]
    cars["name"] = cars["name"].asfactor()
    cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()

    glm_model = H2OGeneralizedLinearEstimator(family="binomial")
    glm_model.train(x=["name", "power", "year"], y="economy_20mpg", training_frame=cars)
    
    predictions_train = glm_model.predict(cars).as_data_frame()
    metrics = glm_model.training_model_metrics()
    mse = metrics['MSE']
    auc = metrics['AUC']
    r2 = metrics['r2']

    glm_model_2 = H2OGeneralizedLinearEstimator(family="binomial", control_variables=["name", "power", "year"])
    glm_model_2.train(x=["name", "power", "year"], y="economy_20mpg", training_frame=cars)

    predictions_train_2 = glm_model_2.predict(cars).as_data_frame()
    metrics_2 = glm_model_2.training_model_metrics()
    mse_2 = metrics_2['MSE']
    auc_2 = metrics_2['AUC']
    r2_2 = metrics_2['r2']

    assert mse != mse_2 
    assert auc != auc_2
    assert r2 != r2_2
    
    assert predictions_train.iloc[0, 1] != predictions_train_2.iloc[0, 1]
    assert predictions_train.iloc[10, 1] != predictions_train_2.iloc[10, 1]
    assert predictions_train.iloc[100, 1] != predictions_train_2.iloc[100, 1]
    
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(glm_control_variables)
else:
    glm_control_variables()
