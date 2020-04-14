import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.utils.model_utils import reset_model_threshold


def test_reset_threshold():
    airlines= h2o.import_file(path=pyunit_utils.locate("smalldata/airlines/allyears2k_headers.zip"))

    # convert columns to factors
    airlines["Year"] = airlines["Year"].asfactor()
    airlines["Month"] = airlines["Month"].asfactor()
    airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
    airlines["Cancelled"] = airlines["Cancelled"].asfactor()
    airlines['FlightNum'] = airlines['FlightNum'].asfactor()


    # set the predictor names and the response column name
    predictors = ["Origin", "Dest", "Year", "UniqueCarrier", "DayOfWeek", "Month", "Distance", "FlightNum"]
    response = "IsDepDelayed"

    # split into train and validation sets
    train, valid = airlines.split_frame(ratios = [.8], seed = 1234)

    # initialize the estimator
    model = H2OGradientBoostingEstimator(seed = 1234, ntrees=5)

    # train the model
    model.train(x = predictors, y = response, training_frame = train)
    old_threshold = model._model_json['output']['training_metrics']._metric_json['threshold']
    new_threshold = 0.9
    old_returned = reset_model_threshold(model, new_threshold)
    reset_model = h2o.get_model(model.model_id)
    reset_threshold = reset_model._model_json['output']['training_metrics']._metric_json['threshold']
    assert old_threshold == old_returned
    assert reset_threshold != old_threshold
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_reset_threshold)
else:
    test_reset_threshold()
 
