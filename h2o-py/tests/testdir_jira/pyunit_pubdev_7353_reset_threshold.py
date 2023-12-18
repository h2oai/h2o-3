import sys
sys.path.insert(1,"../../")

import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.utils.model_utils import reset_model_threshold


def test_reset_threshold():
    """ 
    Test the model threshold can be reset. 
    Performance metric should be recalculated and also predictions should be changed based on the new threshold.
    """

    # import data
    airlines = h2o.import_file(path=pyunit_utils.locate("smalldata/airlines/modified_airlines.csv"))

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
    model.train(x=predictors, y=response, training_frame=train)
    old_threshold = model._model_json['output']['default_threshold']
    
    # predict
    preds = model.predict(airlines)

    # reset the threshold and get the old one
    new_threshold = 0.6917189903082518
    old_returned = reset_model_threshold(model, new_threshold)
    reset_model = h2o.get_model(model.model_id)
    reset_threshold = reset_model._model_json['output']['default_threshold']

    # predict with reset model
    preds_reset = reset_model.predict(airlines)

    # compare thresholds
    assert old_threshold == old_returned
    assert new_threshold == reset_threshold
    assert reset_threshold != old_threshold

    # compare predictions
    preds_local = preds.as_data_frame()
    preds_reset_local = preds_reset.as_data_frame()
    print("old threshold:", old_threshold, "new_threshold:", new_threshold)
    for i in range(airlines.nrow):
        if old_threshold <= preds_local.iloc[i, 2] < new_threshold:
            assert preds_local.iloc[i, 0] != preds_reset_local.iloc[i, 0]
        else:
            assert preds_local.iloc[i, 0] == preds_reset_local.iloc[i, 0]


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_reset_threshold)
else:
    test_reset_threshold()
