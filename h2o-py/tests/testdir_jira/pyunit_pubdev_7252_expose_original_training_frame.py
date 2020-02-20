import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.isolation_forest import H2OIsolationForestEstimator
from h2o.estimators.random_forest import H2ORandomForestEstimator
from h2o.estimators.xgboost import H2OXGBoostEstimator



def test_expose_original_training_frame():
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

    

    for estimator in [H2OGradientBoostingEstimator, H2ORandomForestEstimator, H2OXGBoostEstimator, H2OIsolationForestEstimator]:
        # initialize the estimator
        airlines_m_enc = estimator(categorical_encoding = "one_hot_explicit", seed = 1234, ntrees=5)
        airlines_m = estimator(seed = 1234, ntrees=5)

        # train the model
        if (estimator == H2OIsolationForestEstimator):
            airlines_m_enc.train(x = predictors, training_frame = train)
            airlines_m.train(x = predictors, training_frame = train)
        else:    
            airlines_m_enc.train(x = predictors, y = response, training_frame = train)
            airlines_m.train(x = predictors, y = response, training_frame = train)

        assert(sorted(airlines_m._model_json['output']['names']) == sorted(airlines_m_enc._model_json['output']['original_names']))

        if (estimator == H2OIsolationForestEstimator):
            assert(sorted(predictors) == sorted(airlines_m_enc._model_json['output']['original_names']))
        else:
            assert(sorted(predictors + [response]) == sorted(airlines_m_enc._model_json['output']['original_names']))

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_expose_original_training_frame)
else:
    test_expose_original_training_frame()
