def update_param(name, param):
    if name == 'stopping_metric':
        param['values'] = ['AUTO', 'anomaly_score']
        return param
    return None  # param untouched



doc = dict(
    __class__="""
Builds an Extended Isolation Forest model. Extended Isolation Forest algorithm samples the training frame
and in each iteration builds a tree that partitions the space of the sample observations until
it isolates each observation. Length of the path from root to a leaf node of the resulting tree
is used to calculate the anomaly score. Anomalies are easier to isolate and their average
tree path is expected to be shorter than paths of regular observations.
"""
)

examples = dict(
    categorical_encoding="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> encoding = "one_hot_explicit"
>>> airlines_eif = H2OExtendedIsolationForestEstimator(categorical_encoding = encoding,
...                                                    seed = 1234)
>>> airlines_eif.train(x = predictors,
...                   training_frame = airlines)
>>> airlines_eif.model_performance()
""",
    export_checkpoints_dir="""
>>> import tempfile
>>> from os import listdir
>>> airlines = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip", destination_frame="air.hex")
>>> predictors = ["DayofMonth", "DayOfWeek"]
>>> checkpoints_dir = tempfile.mkdtemp()
>>> air_eif = H2OExtendedIsolationForestEstimator(max_depth = 3,
...                                               seed = 1234,
...                                               export_checkpoints_dir = checkpoints_dir)
>>> air_eif.train(x = predictors,
...              training_frame = airlines)
>>> len(listdir(checkpoints_dir))
""",
    ignore_const_cols="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> cars["const_1"] = 6
>>> cars["const_2"] = 7
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> cars_eif = H2OExtendedIsolationForestEstimator(seed = 1234,
...                                                ignore_const_cols = True)
>>> cars_eif.train(x = predictors,
...               training_frame = cars)
>>> cars_eif.model_performance()
""",
    max_runtime_secs="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> cars_eif = H2OExtendedIsolationForestEstimator(max_runtime_secs = 10,
...                                                ntrees = 10000,
...                                                max_depth = 10,
...                                                seed = 1234)
>>> cars_eif.train(x = predictors,
...               training_frame = cars)
>>> cars_eif.model_performance()
""",
    ntrees="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> predictors = titanic.columns
>>> tree_num = [20, 50, 80, 110, 140, 170, 200]
>>> label = ["20", "50", "80", "110", "140", "170", "200"]
>>> for key, num in enumerate(tree_num):
...     titanic_eif = H2OExtendedIsolationForestEstimator(ntrees = num,
...                                                       seed = 1234)
...     titanic_eif.train(x = predictors,
...                      training_frame = titanic) 
...     print(label[key], 'training score', titanic_eif.mse(train = True))
""",
    sample_size="""
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/anomaly/ecg_discord_train.csv")
>>> test = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/anomaly/ecg_discord_test.csv")
>>> extisofor_model = H2OExtendedIsolationForestEstimator(sample_size = 5,
...                                                       ntrees=7)
>>> extisofor_model.train(training_frame = train)
>>> extisofor_model.model_performance()
>>> extisofor_model.model_performance(test)
""",
    extension_level="""
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/anomaly/single_blob.csv")
>>> extisofor_model = H2OExtendedIsolationForestEstimator(extension_level = 1,
...                                                       ntrees=7)
>>> extisofor_model.train(training_frame = train)
>>> extisofor_model.model_performance()
>>> extisofor_model.model_performance(test)
""",
    seed="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> extisofor_w_seed = H2OExtendedIsolationForestEstimator(seed = 1234) 
>>> extisofor_w_seed.train(x = predictors,
...                        training_frame = airlines)
>>> extisofor_wo_seed = H2OExtendedIsolationForestEstimator()
>>> extisofor_wo_seed.train(x = predictors,
...                         training_frame = airlines)
>>> extisofor_w_seed.model_performance()
>>> extisofor_wo_seed.model_performance()
""",
    training_frame="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> cars_eif = H2OExtendedIsolationForestEstimator(seed = 1234)
>>> cars_eif.train(x = predictors,
...                training_frame = cars)
>>> cars_eif.model_performance()
"""
)
