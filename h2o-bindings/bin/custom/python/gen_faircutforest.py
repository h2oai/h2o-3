supervised_learning = False

doc = dict(
    __class__="""
Builds a Fair Cut Forest model for anomaly detection. The algorithm is an extension of Isolation Forest, 
which uses non-axis-parallel splits (similarly as the Extended Isolation Forest) and adds a split guiding metric,
which replaces the uniformly random selection of the split point. The "extension_level" parameter decides, 
how many features (extension_level + 1) will be used for the linear combination to generate 
the separating hyperplane in each step. Setting this parameter to 0 means only one feature will be chosen, 
is equivalent to the standard Isolation Forest algorithm.The rest of the algorithm is analogical 
to the Isolation Forest algorithm. A number of hyperplanes can be generated in each step, 
set by the "k_planes" parameter, and the one, that maximizes the pooled gain metric is selected. 
Each iteration builds a tree that partitions the sample observations' space until it isolates observation. 
The length of the path from root to a leaf node of the resulting tree 
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
>>> airlines_fcf = H2OFairCutForestEstimator(categorical_encoding = encoding,
...                                                    seed = 1234)
>>> airlines_fcf.train(x = predictors,
...                   training_frame = airlines)
>>> airlines_fcf.model_performance()
""",
    ignore_const_cols="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> predictors = ["displacement","power","weight","acceleration","year","const_1","const_2"]
>>> cars["const_1"] = 6
>>> cars["const_2"] = 7
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> cars_fcf = H2OFairCutForestEstimator(seed = 1234,
...                                                ignore_const_cols = True)
>>> cars_fcf.train(x = predictors,
...               training_frame = cars)
>>> cars_fcf.model_performance()
""",
    ntrees="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> predictors = titanic.columns
>>> tree_num = [20, 50, 80, 110, 140, 170, 200]
>>> label = ["20", "50", "80", "110", "140", "170", "200"]
>>> for key, num in enumerate(tree_num):
...     titanic_fcf = H2OFairCutForestEstimator(ntrees = num,
...                                                       seed = 1234,
...                                                       extension_level = titanic.dim[1] - 1)
...     titanic_fcf.train(x = predictors,
...                      training_frame = titanic) 
""",
    sample_size="""
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/anomaly/ecg_discord_train.csv")
>>> fcf_model = H2OFairCutForestEstimator(sample_size = 5,
...                                                 ntrees=7)
>>> fcf_model.train(training_frame = train)
>>> print(fcf_model)
""",
    extension_level="""
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/anomaly/single_blob.csv")
>>> fcf_model = H2OFairCutForestEstimator(extension_level = 1,
...                                                 ntrees=7)
>>> fcf_model.train(training_frame = train)
>>> print(fcf_model)
""",
    k_planes="""
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/anomaly/single_blob.csv")
>>> fcf_model = H2OFairCutForestEstimator(k_planes = 5,
...                                                 ntrees=7)
>>> fcf_model.train(training_frame = train)
>>> print(fcf_model)
""",
    seed="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> fcf_w_seed = H2OFairCutForestEstimator(seed = 1234) 
>>> fcf_w_seed.train(x = predictors,
...                        training_frame = airlines)
>>> fcf_wo_seed = H2OFairCutForestEstimator()
>>> fcf_wo_seed.train(x = predictors,
...                         training_frame = airlines)
>>> print(fcf_w_seed)
>>> print(fcf_wo_seed)
""",
    training_frame="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> cars_fcf = H2OFairCutForestEstimator(seed = 1234, 
...                                                sample_size = 256, 
...                                                extension_level = cars.dim[1] - 1)
>>> cars_fcf.train(x = predictors,
...                training_frame = cars)
>>> print(cars_fcf)
"""
)
