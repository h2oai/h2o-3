import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators.isolation_forest import H2OIsolationForestEstimator
from h2o.model import H2OBinomialModelMetrics
from h2o.model.metrics.anomaly_detection import H2OAnomalyDetectionModelMetrics
from pandas.testing import assert_frame_equal


def isolation_forest_valid():
    print("Isolation Forest Test with a provided validation frame")

    train = h2o.import_file(pyunit_utils.locate("smalldata/anomaly/ecg_discord_train.csv"))
    test = h2o.import_file(pyunit_utils.locate("smalldata/anomaly/ecg_discord_test.csv"))

    # Train a regular IF model
    if_model = H2OIsolationForestEstimator(ntrees=7, seed=12, sample_size=5)
    if_model.train(training_frame=train)
    print(if_model)

    # Label 20% of the test set as anomalies
    predict_test = if_model.predict(test)["predict"]
    threshold = predict_test.quantile(prob=[0.8])["predictQuantiles"].flatten()
    labels_test = predict_test > threshold
    print("Threshold %s" % threshold)
    test["label"] = labels_test.asfactor()

    # Run IF again with same parameters with a labeled test set
    if_model_valid = H2OIsolationForestEstimator(ntrees=7, seed=12, sample_size=5, 
                                                 validation_response_column="label")
    if_model_valid.train(training_frame=train, validation_frame=test)
    print(if_model_valid)

    # Predictions should be the same as for regular IF model 
    train_predict_regular_pd = if_model.predict(train).as_data_frame(use_pandas=True)
    train_predict_valid = if_model_valid.predict(train)
    assert train_predict_valid.names == ['predict', 'score', 'mean_length']
    train_predict_valid['predict'] = train_predict_valid['score'] 
    train_predict_valid = train_predict_valid.drop('score')
    train_predict_valid_pd = train_predict_valid.as_data_frame(use_pandas=True)
    assert_frame_equal(train_predict_regular_pd, train_predict_valid_pd, check_dtype=False)

    # Metrics for train/valid are of the expected type
    assert isinstance(if_model_valid.model_performance(train=True), H2OAnomalyDetectionModelMetrics)
    assert isinstance(if_model_valid.model_performance(valid=True), H2OBinomialModelMetrics)

    new_data = test.split_frame([0.5], seed=42)[0]
    new_data_perf = if_model_valid.model_performance(test_data=new_data)
    assert isinstance(new_data_perf, H2OBinomialModelMetrics)
    print(new_data_perf)
    test_predict_new = if_model_valid.predict(new_data).as_data_frame(use_pandas=True)
    assert list(test_predict_new.columns) == ['predict', 'score', 'mean_length']
    print(test_predict_new)


if __name__ == "__main__":
    pyunit_utils.standalone_test(isolation_forest_valid)
else:
    isolation_forest_valid()
