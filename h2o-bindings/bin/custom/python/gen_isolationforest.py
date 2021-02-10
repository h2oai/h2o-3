def update_param(name, param):
    if name == 'stopping_metric':
        param['values'] = ['AUTO', 'anomaly_score', 'deviance', 'logloss', 'mse', 'rmse', 'mae', 'rmsle',
                            'auc', 'aucpr', 'misclassification', 'mean_per_class_error']
        return param
    return None  # param untouched



doc = dict(
    __class__="""
Builds an Isolation Forest model. Isolation Forest algorithm samples the training frame
and in each iteration builds a tree that partitions the space of the sample observations until
it isolates each observation. Length of the path from root to a leaf node of the resulting tree
is used to calculate the anomaly score. Anomalies are easier to isolate and their average
tree path is expected to be shorter than paths of regular observations.
"""
)

examples = dict(
    build_tree_one_node="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> cars_if = H2OIsolationForestEstimator(build_tree_one_node=True,
...                                       seed=1234)
>>> cars_if.train(x=predictors,
...               training_frame=cars)
>>> cars_if.model_performance()
""",
    categorical_encoding="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> encoding = "one_hot_explicit"
>>> airlines_if = H2OIsolationForestEstimator(categorical_encoding=encoding,
...                                           seed=1234)
>>> airlines_if.train(x=predictors,
...                   training_frame=airlines)
>>> airlines_if.model_performance()
""",
    col_sample_rate_change_per_level="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> airlines_if = H2OIsolationForestEstimator(col_sample_rate_change_per_level=.9,
...                                           seed=1234)
>>> airlines_if.train(x=predictors,
...                   training_frame=airlines)
>>> airlines_if.model_performance()
""",
    col_sample_rate_per_tree="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> airlines_if = H2OIsolationForestEstimator(col_sample_rate_per_tree=.7,
...                                           seed=1234)
>>> airlines_if.train(x=predictors,
...                   training_frame=airlines)
>>> airlines_if.model_performance()
""",
    export_checkpoints_dir="""
>>> import tempfile
>>> from os import listdir
>>> airlines = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip", destination_frame="air.hex")
>>> predictors = ["DayofMonth", "DayOfWeek"]
>>> checkpoints_dir = tempfile.mkdtemp()
>>> air_if = H2OIsolationForestEstimator(max_depth=3,
...                                      seed=1234,
...                                      export_checkpoints_dir=checkpoints_dir)
>>> air_if.train(x=predictors,
...              training_frame=airlines)
>>> len(listdir(checkpoints_dir))
""",
    ignore_const_cols="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> predictors = ["displacement","power","weight","acceleration","year","const_1","const_2"]
>>> cars["const_1"] = 6
>>> cars["const_2"] = 7
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_if = H2OIsolationForestEstimator(seed=1234,
...                                       ignore_const_cols=True)
>>> cars_if.train(x=predictors,
...               training_frame=cars)
>>> cars_if.model_performance()
""",
    max_depth="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> cars_if = H2OIsolationForestEstimator(max_depth=2,
...                                       seed=1234)
>>> cars_if.train(x=predictors,
...               training_frame=cars)
>>> cars_if.model_performance()
""",
    max_runtime_secs="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> cars_if = H2OIsolationForestEstimator(max_runtime_secs=10,
...                                       ntrees=10000,
...                                       max_depth=10,
...                                       seed=1234)
>>> cars_if.train(x=predictors,
...               training_frame=cars)
>>> cars_if.model_performance()
""",
    min_rows="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> cars_if = H2OIsolationForestEstimator(min_rows=16,
...                                       seed=1234)
>>> cars_if.train(x=predictors,
...               training_frame=cars)
>>> cars_if.model_performance()
""",
    mtries="""
>>> covtype = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/covtype/covtype.20k.data")
>>> predictors = covtype.columns[0:54]
>>> cov_if = H2OIsolationForestEstimator(mtries=30, seed=1234)
>>> cov_if.train(x=predictors,
...              training_frame=covtype)
>>> cov_if.model_performance()
""",
    ntrees="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> predictors = titanic.columns
>>> tree_num = [20, 50, 80, 110, 140, 170, 200]
>>> label = ["20", "50", "80", "110", "140", "170", "200"]
>>> for key, num in enumerate(tree_num):
...     titanic_if = H2OIsolationForestEstimator(ntrees=num,
...                                              seed=1234)
...     titanic_if.train(x=predictors,
...                      training_frame=titanic) 
...     print(label[key], 'training score', titanic_if.mse(train=True))
""",
    sample_rate="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> airlines_if = H2OIsolationForestEstimator(sample_rate=.7,
...                                           seed=1234)
>>> airlines_if.train(x=predictors,
...                   training_frame=airlines)
>>> airlines_if.model_performance()
""",
    sample_size="""
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/anomaly/ecg_discord_train.csv")
>>> isofor_model = H2OIsolationForestEstimator(sample_size=5,
...                                            ntrees=7)
>>> isofor_model.train(training_frame=train)
>>> isofor_model.model_performance()
""",
    score_each_iteration="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> cars_if = H2OIsolationForestEstimator(score_each_iteration=True,
...                                       ntrees=55,
...                                       seed=1234)
>>> cars_if.train(x=predictors,
...               training_frame=cars)
>>> cars_if.model_performance()
""",
    score_tree_interval="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> cars_if = H2OIsolationForestEstimator(score_tree_interval=5,
...                                       seed=1234)
>>> cars_if.train(x=predictors,
...               training_frame=cars)
>>> cars_if.model_performance()
""",
    seed="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> isofor_w_seed = H2OIsolationForestEstimator(seed=1234) 
>>> isofor_w_seed.train(x=predictors,
...                     training_frame=airlines)
>>> isofor_wo_seed = H2OIsolationForestEstimator()
>>> isofor_wo_seed.train(x=predictors,
...                      training_frame=airlines)
>>> isofor_w_seed.model_performance()
>>> isofor_wo_seed.model_performance()
""",
    stopping_metric="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> airlines_if = H2OIsolationForestEstimator(stopping_metric="auto",
...                                           stopping_rounds=3,
...                                           stopping_tolerance=1e-2,
...                                           seed=1234)
>>> airlines_if.train(x=predictors,
...                   training_frame=airlines)
>>> airlines_if.model_performance()
""",
    stopping_rounds="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> airlines_if = H2OIsolationForestEstimator(stopping_metric="auto",
...                                           stopping_rounds=3,
...                                           stopping_tolerance=1e-2,
...                                           seed=1234)
>>> airlines_if.train(x=predictors,
...                   training_frame=airlines)
>>> airlines_if.model_performance()
""",
    stopping_tolerance="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> airlines_if = H2OIsolationForestEstimator(stopping_metric="auto",
...                                           stopping_rounds=3,
...                                           stopping_tolerance=1e-2,
...                                           seed=1234)
>>> airlines_if.train(x=predictors,
...                   training_frame=airlines)
>>> airlines_if.model_performance()
""",
    training_frame="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> cars_if = H2OIsolationForestEstimator(seed=1234)
>>> cars_if.train(x=predictors,
...               training_frame=cars)
>>> cars_if.model_performance()
"""
)
