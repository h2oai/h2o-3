doc = dict(
    __class__="""
The naive Bayes classifier assumes independence between predictor variables
conditional on the response, and a Gaussian distribution of numeric predictors with
mean and standard deviation computed from the training dataset. When building a naive
Bayes classifier, every row in the training dataset that contains at least one NA will
be skipped completely. If the test dataset has missing values, then those predictors
are omitted in the probability calculation during prediction.
""",
)

examples = dict(
    balance_classes="""
>>> iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
>>> iris_nb = H2ONaiveBayesEstimator(balance_classes=False,
...                                  nfolds=3,
...                                  seed=1234)
>>> iris_nb.train(x=list(range(4)),
...               y=4,
...               training_frame=iris)
>>> iris_nb.mse()
""",
    class_sampling_factors="""
>>> covtype = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/covtype/covtype.20k.data")
>>> covtype[54] = covtype[54].asfactor()
>>> sample_factors = [1., 0.5, 1., 1., 1., 1., 1.]
>>> cov_nb = H2ONaiveBayesEstimator(class_sampling_factors=sample_factors,
...                                 seed=1234)
>>> predictors = covtype.columns[0:54]
>>> response = 'C55'
>>> cov_nb.train(x=predictors, y=response, training_frame=covtype)
>>> cov_nb.logloss()
""",
    compute_metrics="""
>>> prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")
>>> prostate['CAPSULE'] = prostate['CAPSULE'].asfactor()
>>> prostate['RACE'] = prostate['RACE'].asfactor()
>>> prostate['DCAPS'] = prostate['DCAPS'].asfactor()
>>> prostate['DPROS'] = prostate['DPROS'].asfactor()
>>> response_col = 'CAPSULE'
>>> prostate_nb = H2ONaiveBayesEstimator(laplace=0,
...                                      compute_metrics=False)
>>> prostate_nb.train(x=list(range(3,9)),
...                   y=response_col,
...                   training_frame=prostate)
>>> prostate_nb.show()
""",
    eps_prob="""
>>> import random
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> problem = random.sample(["binomial","multinomial"],1)
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> if problem == "binomial":
...     response_col = "economy_20mpg"
... else:
...     response_col = "cylinders"
>>> cars[response_col] = cars[response_col].asfactor()
>>> cars_nb = H2ONaiveBayesEstimator(min_prob=0.1,
...                                  eps_prob=0.5,
...                                  seed=1234)
>>> cars_nb.train(x=predictors, y=response_col, training_frame=cars)
>>> cars_nb.mse()
""",
    eps_sdev="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> problem = random.sample(["binomial","multinomial"],1)
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> if problem == "binomial":
...     response_col = "economy_20mpg"
... else:
...     response_col = "cylinders"
>>> cars[response_col] = cars[response_col].asfactor()
>>> cars_nb = H2ONaiveBayesEstimator(min_sdev=0.1,
...                                  eps_sdev=0.5,
...                                  seed=1234)
>>> cars_nb.train(x=predictors, y=response_col, training_frame=cars)
>>> cars_nb.mse()
""",
    export_checkpoints_dir="""
>>> import tempfile
>>> from os import listdir
>>> airlines = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip", destination_frame="air.hex")
>>> predictors = ["DayofMonth", "DayOfWeek"]
>>> response = "IsDepDelayed"
>>> checkpoints_dir = tempfile.mkdtemp()
>>> air_nb = H2ONaiveBayesEstimator(export_checkpoints_dir=checkpoints_dir)
>>> air_nb.train(x=predictors, y=response, training_frame=airlines)
>>> len(listdir(checkpoints_dir))
""",
    fold_assignment="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "cylinders"
>>> cars_nb = H2ONaiveBayesEstimator(fold_assignment="Random",
...                                  nfolds=5,
...                                  seed=1234)
>>> response = "economy_20mpg"
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> cars_nb.train(x=predictors, y=response, training_frame=cars)
>>> cars_nb.auc()
""",
    fold_column="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> fold_numbers = cars.kfold_column(n_folds=5, seed=1234)
>>> fold_numbers.set_names(["fold_numbers"])
>>> cars = cars.cbind(fold_numbers)
>>> cars_nb = H2ONaiveBayesEstimator(seed=1234)
>>> cars_nb.train(x=predictors,
...               y=response,
...               training_frame=cars,
...               fold_column="fold_numbers")
>>> cars_nb.auc()
""",
    ignore_const_cols="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> cars["const_1"] = 6
>>> cars["const_2"] = 7
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_nb = H2ONaiveBayesEstimator(seed=1234,
...                                  ignore_const_cols=True)
>>> cars_nb.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> cars_nb.auc()
""",
    keep_cross_validation_fold_assignment="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_nb = H2ONaiveBayesEstimator(keep_cross_validation_fold_assignment=True,
...                                  nfolds=5,
...                                  seed=1234)
>>> cars_nb.train(x=predictors,
...               y=response,
...               training_frame=train)
>>> cars_nb.cross_validation_fold_assignment()
""",
    keep_cross_validation_models="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_nb = H2ONaiveBayesEstimator(keep_cross_validation_models=True,
...                                  nfolds=5,
...                                  seed=1234)
>>> cars_nb.train(x=predictors,
...               y=response,
...               training_frame=train)
>>> cars_nb.cross_validation_models()
""",
    keep_cross_validation_predictions="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_nb = H2ONaiveBayesEstimator(keep_cross_validation_predictions=True,
...                                  nfolds=5,
...                                  seed=1234)
>>> cars_nb.train(x=predictors,
...               y=response,
...               training_frame=train)
>>> cars_nb.cross_validation_predictions()
""",
    laplace="""
>>> prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")
>>> prostate['CAPSULE'] = prostate['CAPSULE'].asfactor()
>>> prostate['RACE'] = prostate['RACE'].asfactor()
>>> prostate['DCAPS'] = prostate['DCAPS'].asfactor()
>>> prostate['DPROS'] = prostate['DPROS'].asfactor()
>>> prostate_nb = H2ONaiveBayesEstimator(laplace=1)
>>> prostate_nb.train(x=list(range(3,9)),
...                   y=response_col,
...                   training_frame=prostate)
>>> prostate_nb.mse()
""",
    max_after_balance_size="""
>>> covtype = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/covtype/covtype.20k.data")
>>> covtype[54] = covtype[54].asfactor()
>>> predictors = covtype.columns[0:54]
>>> response = 'C55'
>>> train, valid = covtype.split_frame(ratios=[.8], seed=1234)
>>> max = .85
>>> cov_nb = H2ONaiveBayesEstimator(max_after_balance_size=max,
...                                 seed=1234) 
>>> cov_nb.train(x=predictors,
...              y=response,
...              training_frame=train,
...              validation_frame=valid)
>>> cars_nb.logloss()
""",
    max_runtime_secs="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_nb = H2ONaiveBayesEstimator(max_runtime_secs=10,
...                                  seed=1234) 
>>> cars_nb.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> cars_nb.auc()
""",
    min_prob="""
>>> import random
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> problem = random.sample(["binomial","multinomial"],1)
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> if problem == "binomial":
...     response_col = "economy_20mpg"
... else:
...     response_col = "cylinders"
>>> cars[response_col] = cars[response_col].asfactor()
>>> cars_nb = H2ONaiveBayesEstimator(min_prob=0.1,
...                                  eps_prob=0.5,
...                                  seed=1234)
>>> cars_nb.train(x=predictors,
...               y=response_col,
...               training_frame=cars)
>>> cars_nb.show()
""",
    min_sdev="""
>>> import random
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> problem = random.sample(["binomial","multinomial"],1)
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> if problem == "binomial":
...     response_col = "economy_20mpg"
... else:
...     response_col = "cylinders"
>>> cars[response_col] = cars[response_col].asfactor()
>>> cars_nb = H2ONaiveBayesEstimator(min_sdev=0.1,
...                                  eps_sdev=0.5,
...                                  seed=1234)
>>> cars_nb.train(x=predictors,
...               y=response_col,
...               training_frame=cars)
>>> cars_nb.show()
""",
    nfolds="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> cars_nb = H2ONaiveBayesEstimator(nfolds=5,
...                                  seed=1234)
>>> cars_nb.train(x=predictors,
...               y=response,
...               training_frame=cars)
>>> cars_nb.auc()
""",
    score_each_iteration="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_nb = H2ONaiveBayesEstimator(score_each_iteration=True,
...                                  seed=1234)
>>> cars_nb.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> cars_nb.auc()
""",
    seed="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> airlines["Year"] = airlines["Year"].asfactor()
>>> airlines["Month"] = airlines["Month"].asfactor()
>>> airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
>>> airlines["Cancelled"] = airlines["Cancelled"].asfactor()
>>> airlines['FlightNum'] = airlines['FlightNum'].asfactor()
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> response = "IsDepDelayed"
>>> train, valid= airlines.split_frame(ratios=[.8], seed=1234)
>>> nb_w_seed = H2ONaiveBayesEstimator(seed=1234)
>>> nb_w_seed.train(x=predictors,
...                 y=response,
...                 training_frame=train,
...                  validation_frame=valid)
>>> nb_wo_seed = H2ONaiveBayesEstimator()
>>> nb_wo_seed.train(x=predictors,
...                  y=response,
...                  training_frame=train,
...                  validation_frame=valid)
>>> nb_w_seed.auc()
>>> nb_wo_seed.auc()
""",
    training_frame="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_nb = H2ONaiveBayesEstimator()
>>> cars_nb.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> cars_nb.auc()
""",
    validation_frame="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_nb = H2ONaiveBayesEstimator()
>>> cars_nb.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> cars_nb.auc()
""",
    gainslift_bins="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/testng/airlines_train.csv")
>>> model = H2ONaiveBayesEstimator(gainslift_bins=20)
>>> model.train(x=["Origin", "Distance"],
...             y="IsDepDelayed",
...             training_frame=airlines)
>>> model.gains_lift()
"""
)
