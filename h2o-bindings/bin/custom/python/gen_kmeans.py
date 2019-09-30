doc = dict(
    __class__="""Performs k-means clustering on an H2O dataset.""",
)

examples = dict(
    categorical_encoding="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> encoding = "one_hot_explicit"
>>> airlines_km = H2OKMeansEstimator(categorical_encoding = encoding,
...                                  seed = 1234)
>>> airlines_km.train(x = predictors, training_frame = airlines)
>>> airlines_km.scoring_history()
""",
    estimate_k="""
>>> iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
>>> iris['class'] = iris['class'].asfactor()
>>> predictors = iris.columns[:-1]
>>> train, valid = iris.split_frame(ratios = [.8], seed = 1234)
>>> iris_kmeans = H2OKMeansEstimator(k = 10,
...                                  estimate_k = True,
...                                  standardize = False,
...                                  seed = 1234)
>>> iris_kmeans.train(x = predictors,
...                   training_frame = train,
...                   validation_frame=valid)
>>> isis_kmeans.scoring_history()
""",
    export_checkpoints_dir="""
>>> import tempfile
>>> from os import listdir
>>> airlines = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip", destination_frame="air.hex")
>>> predictors = ["DayofMonth", "DayOfWeek"]
>>> checkpoints_dir = tempfile.mkdtemp()
>>> air_km = H2OKMeansEstimator(export_checkpoints_dir = checkpoints_dir,
...                             seed = 1234)
>>> air_km.train(x = predictors, training_frame = airlines)
>>> len(listdir(checkpoints_dir))
""",
    fold_assignment="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> assignment_type = "Random"
>>> cars_km = H2OKMeansEstimator(fold_assignment = assignment_type,
...                              nfolds = 5,
...                              seed = 1234)
>>> cars_km.train(x = predictors, training_frame = cars)
>>> cars_km.scoring_history()
""",
    fold_column="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> fold_numbers = cars.kfold_column(n_folds = 5, seed = 1234)
>>> fold_numbers.set_names(["fold_numbers"])
>>> cars = cars.cbind(fold_numbers)
>>> print(cars['fold_numbers'])
>>> cars_km = H2OKMeansEstimator(seed = 1234)
>>> cars_km.train(x = predictors,
...               training_frame = cars,
...               fold_column = "fold_numbers")
>>> cars_km.scoring_history()
""",
    ignore_const_cols="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> cars["const_1"] = 6
>>> cars["const_2"] = 7
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> cars_km = H2OKMeansEstimator(ignore_const_cols = True,
...                              seed = 1234)
>>> cars_km.train(x = predictors,
...               training_frame = train,
...               validation_frame = valid)
>>> cars_km.scoring_history()
""",
    ignored_columns="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> predictors = airlines.columns[:9]
>>> train, valid= airlines.split_frame(ratios = [.8], seed = 1234)
>>> col_list = ['DepTime','CRSDepTime','ArrTime','CRSArrTime']
>>> airlines_km = H2OKMeansEstimator(ignored_columns = col_list,
...                                  seed = 1234)
>>> airlines_km.train(training_frame = train,
...                   validation_frame = valid)
>>> airlines_km.scoring_history()
""",
    init="""
>>> seeds = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/flow_examples/seeds_dataset.txt")
>>> predictors = seeds.columns[0:7]
>>> train, valid = seeds.split_frame(ratios = [.8], seed = 1234)
>>> seeds_kmeans = H2OKMeansEstimator(k = 3,
...                                   init='Furthest',
...                                   seed = 1234)
>>> seeds_kmeans.train(x = predictors,
...                    training_frame = train,
...                    validation_frame= valid)
>>> seeds_kmeans.scoring_history()
""",
    k="""
>>> seeds = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/flow_examples/seeds_dataset.txt")
>>> predictors = seeds.columns[0:7]
>>> train, valid = seeds.split_frame(ratios = [.8], seed = 1234)
>>> seeds_kmeans = H2OKMeansEstimator(k = 3, seed = 1234)
>>> seeds_kmeans.train(x = predictors,
...                    training_frame = train,
...                    validation_frame=valid)
>>> seeds_kmeans.totss()
""",
    keep_cross_validation_fold_assignment="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> cars_km = H2OKMeansEstimator(keep_cross_validation_fold_assignment = True,
...                              nfolds = 5,
...                              seed = 1234)
>>> cars_km.train(x = predictors,
...               training_frame = train)
>>> cars_km.scoring_history()
""",
    keep_cross_validation_models="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> cars_km = H2OKMeansEstimator(keep_cross_validation_models = True,
...                              nfolds = 5,
...                              seed = 1234)
>>> cars_km.train(x = predictors,
...               training_frame = train,
...               validation_frame = valid)
>>> cars_km.scoring_history()
""",
    max_iterations="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> train, valid = cars.split_frame(ratios = [.8])
>>> cars_km = H2OKMeansEstimator(max_iterations = 50)
>>> cars_km.train(x = predictors,
...               training_frame = train,
...               validation_frame = valid)
>>> cars_km.scoring_history()
""",
    max_runtime_secs="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> cars_km = H2OKMeansEstimator(max_runtime_secs = 10,
...                              seed = 1234) 
>>> cars_km.train(x = predictors,
...               training_frame = train,
...               validation_frame = valid)
>>> cars_km.scoring_history()
""",
    nfolds="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> cars_km = H2OKMeansEstimator(nfolds = 5, seed = 1234)
>>> cars_km.train(x = predictors,
...               training_frame=train,
...               validation_frame=valid)
>>> cars_km.train(x = predictors, training_frame = cars)
>>> cars_km.scoring_history()
""",
    score_each_iteration="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> cars_km = H2OKMeansEstimator(score_each_iteration = True,
...                              seed = 1234) 
>>> cars_km.train(x = predictors,
...               training_frame = train,
...               validation_frame = valid)
>>> cars_km.scoring_history()
""",
    seed="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> km_w_seed = H2OKMeansEstimator(seed = 1234)
>>> km_w_seed.train(x = predictors,
...                 training_frame = train,
...                 validation_frame = valid)
>>> km_wo_seed = H2OKMeansEstimator()
>>> km_wo_seed.train(x = predictors,
...                  training_frame = train,
...                  validation_frame = valid)
>>> km_w_seed.scoring_history()
>>> km_wo_seed.scoring_history()
""",
    standardize="""
>>> boston = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/BostonHousing.csv")
>>> predictors = boston.columns[:-1]
>>> boston['chas'] = boston['chas'].asfactor()
>>> train, valid = boston.split_frame(ratios = [.8])
>>> boston_km = H2OKMeansEstimator(standardize = True)
>>> boston_km.train(x = predictors,
...                 training_frame = train,
...                 validation_frame = valid)
>>> boston_km.scoring_history()
""",
    training_frame="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> cars_km = H2OKMeansEstimator(seed = 1234)
>>> cars_km.train(x = predictors,
...               training_frame = train,
...               validation_frame = valid)
>>> cars_km.scoring_history()
""",
    user_points="""
>>> iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
>>> iris['class'] = iris['class'].asfactor()
>>> predictors = iris.columns[:-1]
>>> train, valid = iris.split_frame(ratios = [.8], seed = 1234)
>>> point1 = [4.9,3.0,1.4,0.2]
>>> point2 = [5.6,2.5,3.9,1.1]
>>> point3 = [6.5,3.0,5.2,2.0]
>>> points = h2o.H2OFrame([point1, point2, point3])
>>> iris_km = H2OKMeansEstimator(k = 3,
...                              user_points = points,
...                              seed = 1234)
>>> iris_km.train(x=predictors,
...               training_frame=iris,
...               validation_frame=valid)
>>> iris_kmeans.tot_withinss(valid = True)
""",
    validation_frame="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> cars_km = H2OKMeansEstimator(seed = 1234)
>>> cars_km.train(x = predictors,
...               training_frame = train,
...               validation_frame = valid)
>>> cars_km.scoring_history()
""",
    keep_cross_validation_predictions="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> cars_km = H2OKMeansEstimator(keep_cross_validation_predictions = True,
...                              nfolds = 5,
...                              seed = 1234)
>>> cars_km.train(x = predictors,
...               training_frame = train,
...               validation_frame = valid)
>>> cars_km.scoring_history()
"""
)
