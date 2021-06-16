supervised_learning = False

doc = dict(
    __class__="""
Builds an Extended Isolation Forest model. Extended Isolation Forest generalizes its predecessor algorithm, 
Isolation Forest. The original Isolation Forest algorithm suffers from bias due to tree branching. Extension of the 
algorithm mitigates the bias by adjusting the branching, and the original algorithm becomes just a special case.
Extended Isolation Forest's attribute "extension_level" allows leveraging the generalization. The minimum value is 0 and
means the Isolation Forest's behavior. Maximum value is (numCols - 1) and stands for full extension. The rest of the 
algorithm is analogical to the Isolation Forest algorithm. Each iteration builds a tree that partitions the sample 
observations' space until it isolates observation. The length of the path from root to a leaf node of the resulting tree
is used to calculate the anomaly score. Anomalies are easier to isolate, and their average
tree path is expected to be shorter than paths of regular observations. Anomaly score is a number between 0 and 1. 
A number closer to 0 is a normal point, and a number closer to 1 is a more anomalous point.
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
    ignore_const_cols="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> predictors = ["displacement","power","weight","acceleration","year","const_1","const_2"]
>>> cars["const_1"] = 6
>>> cars["const_2"] = 7
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> cars_eif = H2OExtendedIsolationForestEstimator(seed = 1234,
...                                                ignore_const_cols = True)
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
...                                                       seed = 1234,
...                                                       extension_level = titanic.dim[1] - 1)
...     titanic_eif.train(x = predictors,
...                      training_frame = titanic) 
""",
    sample_size="""
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/anomaly/ecg_discord_train.csv")
>>> eif_model = H2OExtendedIsolationForestEstimator(sample_size = 5,
...                                                 ntrees=7)
>>> eif_model.train(training_frame = train)
>>> print(eif_model)
""",
    extension_level="""
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/anomaly/single_blob.csv")
>>> eif_model = H2OExtendedIsolationForestEstimator(extension_level = 1,
...                                                 ntrees=7)
>>> eif_model.train(training_frame = train)
>>> print(eif_model)
""",
    seed="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> eif_w_seed = H2OExtendedIsolationForestEstimator(seed = 1234) 
>>> eif_w_seed.train(x = predictors,
...                        training_frame = airlines)
>>> eif_wo_seed = H2OExtendedIsolationForestEstimator()
>>> eif_wo_seed.train(x = predictors,
...                         training_frame = airlines)
>>> print(eif_w_seed)
>>> print(eif_wo_seed)
""",
    training_frame="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> cars_eif = H2OExtendedIsolationForestEstimator(seed = 1234, 
...                                                sample_size = 256, 
...                                                extension_level = cars.dim[1] - 1)
>>> cars_eif.train(x = predictors,
...                training_frame = cars)
>>> print(cars_eif)
"""
)
