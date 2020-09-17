def update_param(name, param):
    if name == 'distribution':
        param['values'].remove('ordinal')
        return param
    return None  # param untouched


doc = dict(
    __class__="""
Builds gradient boosted trees on a parsed data set, for regression or classification.
The default distribution function will guess the model type based on the response column type.
Otherwise, the response column must be an enum for "bernoulli" or "multinomial", and numeric
for all other distributions.
"""
)

examples = dict(
    training_frame="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_gbm = H2OGradientBoostingEstimator(seed=1234)
>>> cars_gbm.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> cars_gbm.auc(valid=True)
""",
    validation_frame="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_gbm = H2OGradientBoostingEstimator(seed=1234)
>>> cars_gbm.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> cars_gbm.auc(valid=True)
""",
    nfolds="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> folds = 5
>>> cars_gbm = H2OGradientBoostingEstimator(nfolds=folds,
...                                         seed=1234
>>> cars_gbm.train(x=predictors,
...                y=response,
...                training_frame=cars)
>>> cars_gbm.auc()
""",
   keep_cross_validation_models="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> folds = 5
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_gbm = H2OGradientBoostingEstimator(keep_cross_validation_models=True,
...                                         nfolds=5,
...                                         seed=1234)
>>> cars_gbm.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> cars_gbm.auc()
""",
    keep_cross_validation_predictions="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> folds = 5
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_gbm = H2OGradientBoostingEstimator(keep_cross_validation_predictions=True,
...                                         nfolds=5,
...                                         seed=1234)
>>> cars_gbm.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> cars_gbm.auc()
""",
    keep_cross_validation_fold_assignment="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> folds = 5
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_gbm = H2OGradientBoostingEstimator(keep_cross_validation_fold_assignment=True,
...                                         nfolds=5,
...                                         seed=1234)
>>> cars_gbm.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> cars_gbm.auc()
""",
    score_each_iteration="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8],
...                                 seed=1234)
>>> cars_gbm = H2OGradientBoostingEstimator(score_each_iteration=True,
...                                         ntrees=55,
...                                         seed=1234)
>>> cars_gbm.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> cars_gbm.scoring_history()
""",
    score_tree_interval="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8],
...                                 seed=1234)
>>> cars_gbm = H2OGradientBoostingEstimator(score_tree_interval=True,
...                                         ntrees=55,
...                                         seed=1234)
>>> cars_gbm.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> cars_gbm.scoring_history()
""",
    fold_assignment="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> assignment_type = "Random"
>>> cars_gbm = H2OGradientBoostingEstimator(fold_assignment=assignment_type,
...                                         nfolds=5,
...                                         seed=1234)
>>> cars_gbm.train(x=predictors, y=response, training_frame=cars)
>>> cars_gbm.auc(xval=True)
""",
    fold_column="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> fold_numbers = cars.kfold_column(n_folds=5,
...                                  seed=1234)
>>> fold_numbers.set_names(["fold_numbers"])
>>> cars = cars.cbind(fold_numbers)
>>> cars_gbm = H2OGradientBoostingEstimator(seed=1234)
>>> cars_gbm.train(x=predictors,
...                y=response,
...                training_frame=cars,
...                fold_column="fold_numbers")
>>> cars_gbm.auc(xval=True)
""",
    ignore_const_cols="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> cars["const_1"] = 6
>>> cars["const_2"] = 7
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_gbm = H2OGradientBoostingEstimator(seed=1234,
...                                         ignore_const_cols=True)
>>> cars_gbm.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> cars_gbm.auc(valid=True)
""",
    offset_column="""
>>> boston = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/BostonHousing.csv")
>>> predictors = boston.columns[:-1]
>>> response = "medv"
>>> boston['chas'] = boston['chas'].asfactor()
>>> boston["offset"] = boston["medv"].log()
>>> train, valid = boston.split_frame(ratios=[.8], seed=1234)
>>> boston_gbm = H2OGradientBoostingEstimator(offset_column="offset",
...                                           seed=1234)
>>> boston_gbm.train(x=predictors,
...                  y=response,
...                  training_frame=train,
...                  validation_frame=valid)
>>> boston_gbm.mse(valid=True)
""",
    weights_column="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_gbm = H2OGradientBoostingEstimator(seed=1234)
>>> cars_gbm.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid,
...                weights_column="weight")
>>> cars_gbm.auc(valid=True)
""",
    balance_classes="""
>>> covtype = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/covtype/covtype.20k.data")
>>> covtype[54] = covtype[54].asfactor()
>>> predictors = covtype.columns[0:54]
>>> response = 'C55'
>>> train, valid = covtype.split_frame(ratios=[.8], seed=1234)
>>> cov_gbm = H2OGradientBoostingEstimator(balance_classes=True,
...                                        seed=1234)
>>> cov_gbm.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> cov_gbm.logloss(valid=True)
""",
    class_sampling_factors="""
>>> covtype = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/covtype/covtype.20k.data")
>>> covtype[54] = covtype[54].asfactor()
>>> predictors = covtype.columns[0:54]
>>> response = 'C55'
>>> train, valid = covtype.split_frame(ratios=[.8], seed=1234)
>>> sample_factors = [1., 0.5, 1., 1., 1., 1., 1.]
>>> cov_gbm = H2OGradientBoostingEstimator(balance_classes=True,
...                                        class_sampling_factors=sample_factors,
...                                        seed=1234)
>>> cov_gbm.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> cov_gbm.logloss(valid=True)
""",
    max_after_balance_size="""
>>> covtype = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/covtype/covtype.20k.data")
>>> covtype[54] = covtype[54].asfactor()
>>> predictors = covtype.columns[0:54]
>>> response = 'C55'
>>> train, valid = covtype.split_frame(ratios=[.8], seed=1234)
>>> max = .85
>>> cov_gbm = H2OGradientBoostingEstimator(balance_classes=True,
...                                        max_after_balance_size=max,
...                                        seed=1234)
>>> cov_gbm.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> cov_gbm.logloss(valid=True)
""",
    ntrees="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> titanic['survived'] = titanic['survived'].asfactor()
>>> predictors = titanic.columns
>>> del predictors[1:3]
>>> response = 'survived'
>>> train, valid = titanic.split_frame(ratios=[.8], seed=1234)
>>> tree_num = [20, 50, 80, 110, 140, 170, 200]
>>> label = ["20", "50", "80", "110", "140", "170", "200"]
>>> for key, num in enumerate(tree_num):
...     titanic_gbm = H2OGradientBoostingEstimator(ntrees=num,
...                                                seed=1234)
...     titanic_gbm.train(x=predictors,
...                       y=response,
...                       training_frame=train,
...                       validation_frame=valid)
...     print(label[key], 'training score', titanic_gbm.auc(train=True))
...     print(label[key], 'validation score', titanic_gbm.auc(valid=True))
""",
    max_depth="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_gbm = H2OGradientBoostingEstimator(ntrees=100,
...                                         max_depth=2,
...                                         seed=1234)
>>> cars_gbm.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> cars_gbm.auc(valid=True)
""",
    min_rows="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_gbm = H2OGradientBoostingEstimator(min_rows=16,
...                                         seed=1234)
>>> cars_gbm.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> cars_gbm.auc(valid=True)
""",
    nbins="""
>>> eeg = h2o.import_file("https://h2o-public-test-data.s3.amazonaws.com/smalldata/eeg/eeg_eyestate.csv")
>>> eeg['eyeDetection'] = eeg['eyeDetection'].asfactor()
>>> predictors = eeg.columns[:-1]
>>> response = 'eyeDetection'
>>> train, valid = eeg.split_frame(ratios=[.8], seed=1234)
>>> bin_num = [16, 32, 64, 128, 256, 512]
>>> label = ["16", "32", "64", "128", "256", "512"]
>>> for key, num in enumerate(bin_num):
...     eeg_gbm = H2OGradientBoostingEstimator(nbins=num, seed=1234)
...     eeg_gbm.train(x=predictors,
...                   y=response,
...                   training_frame=train,
...                   validation_frame=valid)
...     print(label[key], 'training score', eeg_gbm.auc(train=True)) 
...     print(label[key], 'validation score', eeg_gbm.auc(valid=True))
""",
    nbins_top_level="""
>>> eeg = h2o.import_file("https://h2o-public-test-data.s3.amazonaws.com/smalldata/eeg/eeg_eyestate.csv")
>>> eeg['eyeDetection'] = eeg['eyeDetection'].asfactor()
>>> predictors = eeg.columns[:-1]
>>> response = 'eyeDetection'
>>> train, valid = eeg.split_frame(ratios=[.8], seed=1234)
>>> bin_num = [32, 64, 128, 256, 512, 1024, 2048, 4096]
>>> label = ["32", "64", "128", "256", "512", "1024", "2048", "4096"]
>>> for key, num in enumerate(bin_num):
...     eeg_gbm = H2OGradientBoostingEstimator(nbins_top_level=num, seed=1234)
...     eeg_gbm.train(x=predictors,
...                   y=response,
...                   training_frame=train,
...                   validation_frame=valid)
...     print(label[key], 'training score', eeg_gbm.auc(train=True)) 
...     print(label[key], 'validation score', eeg_gbm.auc(valid=True))
""",
    nbins_cats="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> airlines["Year"] = airlines["Year"].asfactor()
>>> airlines["Month"] = airlines["Month"].asfactor()
>>> airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
>>> airlines["Cancelled"] = airlines["Cancelled"].asfactor()
>>> airlines['FlightNum'] = airlines['FlightNum'].asfactor()
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> response = "IsDepDelayed"
>>> train, valid = airlines.split_frame(ratios=[.8], seed=1234)
>>> bin_num = [8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096]
>>> label = ["8", "16", "32", "64", "128", "256", "512", "1024", "2048", "4096"]
>>> for key, num in enumerate(bin_num):
...     airlines_gbm = H2OGradientBoostingEstimator(nbins_cats=num, seed=1234)
...     airlines_gbm.train(x=predictors,
...                        y=response,
...                        training_frame=train,
...                        validation_frame=valid)
...     print(label[key], 'training score', airlines_gbm.auc(train=True))
...     print(label[key], 'validation score', airlines_gbm.auc(valid=True))
""",
    stopping_rounds="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> airlines["Year"] = airlines["Year"].asfactor()
>>> airlines["Month"] = airlines["Month"].asfactor()
>>> airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
>>> airlines["Cancelled"] = airlines["Cancelled"].asfactor()
>>> airlines['FlightNum'] = airlines['FlightNum'].asfactor()
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> response = "IsDepDelayed"
>>> train, valid = airlines.split_frame(ratios=[.8], seed=1234)
>>> airlines_gbm = H2OGradientBoostingEstimator(stopping_metric="auc",
...                                             stopping_rounds=3,
...                                             stopping_tolerance=1e-2,
...                                             seed=1234)
>>> airlines_gbm.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> airlines_gbm.auc(valid=True)
""",
    stopping_metric="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> airlines["Year"] = airlines["Year"].asfactor()
>>> airlines["Month"] = airlines["Month"].asfactor()
>>> airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
>>> airlines["Cancelled"] = airlines["Cancelled"].asfactor()
>>> airlines['FlightNum'] = airlines['FlightNum'].asfactor()
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> response = "IsDepDelayed"
>>> train, valid = airlines.split_frame(ratios=[.8], seed=1234)
>>> airlines_gbm = H2OGradientBoostingEstimator(stopping_metric="auc",
...                                             stopping_rounds=3,
...                                             stopping_tolerance=1e-2,
...                                             seed=1234)
>>> airlines_gbm.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> airlines_gbm.auc(valid=True)
""",
    stopping_tolerance="""
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
>>> airlines_gbm = H2OGradientBoostingEstimator(stopping_metric="auc",
...                                             stopping_rounds=3,
...                                             stopping_tolerance=1e-2,
...                                             seed=1234)
>>> airlines_gbm.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> airlines_gbm.auc(valid=True)
""",
    max_runtime_secs="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_gbm = H2OGradientBoostingEstimator(max_runtime_secs=10,
...                                         ntrees=10000,
...                                         max_depth=10,
...                                         seed=1234)
>>> cars_gbm.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> cars_gbm.auc(valid=True)
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
>>> train, valid = airlines.split_frame(ratios=[.8], seed=1234)
>>> gbm_w_seed_1 = H2OGradientBoostingEstimator(col_sample_rate=.7,
...                                             seed=1234)
>>> gbm_w_seed_1.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> print('auc for the 1st model built with a seed:', gbm_w_seed_1.auc(valid=True))
""",
    build_tree_one_node="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_gbm = H2OGradientBoostingEstimator(build_tree_one_node=True,
...                                         seed=1234)
>>> cars_gbm.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> cars_gbm.auc(valid=True)
""",
    learn_rate="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> titanic['survived'] = titanic['survived'].asfactor()
>>> predictors = titanic.columns
>>> del predictors[1:3]
>>> response = 'survived'
>>> train, valid = titanic.split_frame(ratios=[.8], seed=1234)
>>> titanic_gbm = H2OGradientBoostingEstimator(ntrees=10000,
...                                            learn_rate=0.01,
...                                            stopping_rounds=5,
...                                            stopping_metric="AUC",
...                                            stopping_tolerance=1e-4,
...                                            seed=1234)
>>> titanic_gbm.train(x=predictors,
...                   y=response,
...                   training_frame=train,
...                   validation_frame=valid)
>>> titanic_gbm.auc(valid=True)
""",
    learn_rate_annealing="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> titanic['survived'] = titanic['survived'].asfactor()
>>> predictors = titanic.columns
>>> del predictors[1:3]
>>> response = 'survived'
>>> train, valid = titanic.split_frame(ratios=[.8], seed=1234)
>>> titanic_gbm = H2OGradientBoostingEstimator(ntrees=10000,
...                                            learn_rate=0.05,
...                                            learn_rate_annealing=.9,
...                                            stopping_rounds=5,
...                                            stopping_metric="AUC",
...                                            stopping_tolerance=1e-4,
...                                            seed=1234)
>>> titanic_gbm.train(x=predictors,
...                   y=response,
...                   training_frame=train,
...                   validation_frame=valid)
>>> titanic_gbm.auc(valid=True)
""",
    distribution="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> response = "cylinders"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_gbm = H2OGradientBoostingEstimator(distribution="poisson",
...                                         seed=1234)
>>> cars_gbm.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> cars_gbm.mse(valid=True)
""",
    quantile_alpha="""
>>> boston = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/BostonHousing.csv")
>>> predictors = boston.columns[:-1]
>>> response = "medv"
>>> boston['chas'] = boston['chas'].asfactor()
>>> train, valid = boston.split_frame(ratios=[.8], seed=1234)
>>> boston_gbm = H2OGradientBoostingEstimator(distribution="quantile",
...                                           quantile_alpha=.8,
...                                           seed=1234)
>>> boston_gbm.train(x=predictors,
...                  y=response,
...                  training_frame=train,
...                  validation_frame=valid)
>>> boston_gbm.mse(valid=True)
""",
    tweedie_power="""
>>> insurance = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/insurance.csv")
>>> predictors = insurance.columns[0:4]
>>> response = 'Claims'
>>> insurance['Group'] = insurance['Group'].asfactor()
>>> insurance['Age'] = insurance['Age'].asfactor()
>>> train, valid = insurance.split_frame(ratios=[.8], seed=1234)
>>> insurance_gbm = H2OGradientBoostingEstimator(distribution="tweedie",
...                                              tweedie_power=1.2,
...                                              seed=1234)
>>> insurance_gbm.train(x=predictors,
...                     y=response,
...                     training_frame=train,
...                     validation_frame=valid)
>>> insurance_gbm.mse(valid=True)
""",
    huber_alpha="""
>>> insurance = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/insurance.csv")
>>> predictors = insurance.columns[0:4]
>>> response = 'Claims'
>>> insurance['Group'] = insurance['Group'].asfactor()
>>> insurance['Age'] = insurance['Age'].asfactor()
>>> train, valid = insurance.split_frame(ratios=[.8], seed=1234)
>>> insurance_gbm = H2OGradientBoostingEstimator(distribution="huber",
...                                              huber_alpha=0.9,
...                                              seed=1234)
>>> insurance_gbm.train(x=predictors,
...                     y=response,
...                     training_frame=train,
...                     validation_frame=valid)
>>> insurance_gbm.mse(valid=True)
""",
    checkpoint="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_gbm = H2OGradientBoostingEstimator(ntrees=1,
...                                         seed=1234)
>>> cars_gbm.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> print(cars_gbm.auc(valid=True))
>>> print("Number of trees built for cars_gbm model:", cars_gbm.ntrees)
>>> cars_gbm_continued = H2OGradientBoostingEstimator(checkpoint=cars_gbm.model_id,
...                                                   ntrees=50,
...                                                   seed=1234)
>>> cars_gbm_continued.train(x=predictors,
...                          y=response,
...                          training_frame=train,
...                          validation_frame=valid)
>>> cars_gbm_continued.auc(valid=True)
>>> print("Number of trees built for cars_gbm model:",cars_gbm_continued.ntrees) 
""",
    sample_rate="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> airlines["Month"] = airlines["Month"].asfactor()                             >>> airlines["Year"]= airlines["Year"].asfactor()
>>> airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
>>> airlines["Cancelled"] = airlines["Cancelled"].asfactor()
>>> airlines['FlightNum'] = airlines['FlightNum'].asfactor()
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> response = "IsDepDelayed"
>>> train, valid = airlines.split_frame(ratios=[.8], seed=1234)
>>> airlines_gbm = H2OGradientBoostingEstimator(sample_rate=.7,
...                                             seed=1234)
>>> airlines_gbm.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> airlines_gbm.auc(valid=True)
""",
    sample_rate_per_class="""
>>> covtype = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/covtype/covtype.20k.data")
>>> covtype[54] = covtype[54].asfactor()
>>> predictors = covtype.columns[0:54]
>>> response = 'C55'
>>> train, valid = covtype.split_frame(ratios=[.8], seed=1234)
>>> rate_per_class_list = [1, .4, 1, 1, 1, 1, 1]
>>> cov_gbm = H2OGradientBoostingEstimator(sample_rate_per_class=rate_per_class_list,
...                                        seed=1234)
>>> cov_gbm.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> cov_gbm.logloss(valid=True)
""",
    col_sample_rate="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> airlines["Year"] = airlines["Year"].asfactor()
>>> airlines["Month"] = airlines["Month"].asfactor()
>>> airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
>>> airlines["Cancelled"] = airlines["Cancelled"].asfactor()
>>> airlines['FlightNum'] = airlines['FlightNum'].asfactor()
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> response = "IsDepDelayed"
>>> train, valid = airlines.split_frame(ratios=[.8], seed=1234)
>>> airlines_gbm = H2OGradientBoostingEstimator(col_sample_rate=.7,
...                                             seed=1234)
>>> airlines_gbm.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> airlines_gbm.auc(valid=True)
""",
    col_sample_rate_change_per_level="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> airlines["Year"] = airlines["Year"].asfactor()
>>> airlines["Month"] = airlines["Month"].asfactor()
>>> airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
>>> airlines["Cancelled"] = airlines["Cancelled"].asfactor()
>>> airlines['FlightNum'] = airlines['FlightNum'].asfactor()
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> response = "IsDepDelayed"
>>> train, valid = airlines.split_frame(ratios=[.8], seed=1234)
>>> airlines_gbm = H2OGradientBoostingEstimator(col_sample_rate_change_per_level=.9,
...                                             seed=1234)
>>> airlines_gbm.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> airlines_gbm.auc(valid=True)
""",
    col_sample_rate_per_tree="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> airlines["Year"] = airlines["Year"].asfactor()
>>> airlines["Month"] = airlines["Month"].asfactor()
>>> airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
>>> airlines["Cancelled"] = airlines["Cancelled"].asfactor()
>>> airlines['FlightNum'] = airlines['FlightNum'].asfactor()
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> response = "IsDepDelayed"
>>> train, valid = airlines.split_frame(ratios=[.8], seed=1234)
>>> airlines_gbm = H2OGradientBoostingEstimator(col_sample_rate_per_tree=.7,
...                                             seed=1234)
>>> airlines_gbm.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> airlines_gbm.auc(valid=True)
""",
    min_split_improvement="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_gbm = H2OGradientBoostingEstimator(min_split_improvement=1e-3,
...                                         seed=1234)
>>> cars_gbm.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> cars_gbm.auc(valid=True)
""",
    histogram_type="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> airlines["Year"] = airlines["Year"].asfactor()
>>> airlines["Month"] = airlines["Month"].asfactor()
>>> airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
>>> airlines["Cancelled"] = airlines["Cancelled"].asfactor()
>>> airlines['FlightNum'] = airlines['FlightNum'].asfactor()
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> response = "IsDepDelayed"
>>> train, valid = airlines.split_frame(ratios=[.8], seed=1234)
>>> airlines_gbm = H2OGradientBoostingEstimator(histogram_type="UniformAdaptive",
...                                             seed=1234)
>>> airlines_gbm.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> airlines_gbm.auc(valid=True)
""",
    max_abs_leafnode_pred="""
>>> covtype = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/covtype/covtype.20k.data")
>>> covtype[54] = covtype[54].asfactor()
>>> predictors = covtype.columns[0:54]
>>> response = 'C55'
>>> train, valid = covtype.split_frame(ratios=[.8], seed=1234)
>>> cov_gbm = H2OGradientBoostingEstimator(max_abs_leafnode_pred=2,
...                                        seed=1234)
>>> cov_gbm.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> cov_gbm.logloss(valid=True)
""",
    pred_noise_bandwidth="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> titanic['survived'] = titanic['survived'].asfactor()
>>> predictors = titanic.columns
>>> del predictors[1:3]
>>> response = 'survived'
>>> train, valid = titanic.split_frame(ratios=[.8], seed=1234)
>>> titanic_gbm = H2OGradientBoostingEstimator(pred_noise_bandwidth=0.1,
...                                            seed=1234)
>>> titanic_gbm.train(x=predictors,
...                   y=response,
...                   training_frame=train,
...                   validation_frame=valid)
>>> titanic_gbm.auc(valid = True)
""",
    categorical_encoding="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> airlines["Year"] = airlines["Year"].asfactor()
>>> airlines["Month"] = airlines["Month"].asfactor()
>>> airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
>>> airlines["Cancelled"] = airlines["Cancelled"].asfactor()
>>> airlines['FlightNum'] = airlines['FlightNum'].asfactor()
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> response = "IsDepDelayed"
>>> train, valid = airlines.split_frame(ratios=[.8], seed=1234)
>>> airlines_gbm = H2OGradientBoostingEstimator(categorical_encoding="labelencoder",
...                                             seed=1234)
>>> airlines_gbm.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> airlines_gbm.auc(valid=True)
""",
    calibrate_model="""
>>> ecology = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/ecology_model.csv")
>>> ecology['Angaus'] = ecology['Angaus'].asfactor()
>>> response = 'Angaus'
>>> train, calib = ecology.split_frame(seed = 12354)
>>> predictors = ecology.columns[3:13]
>>> w = h2o.create_frame(binary_fraction=1,
...                      binary_ones_fraction=0.5,
...                      missing_fraction=0,
...                      rows=744, cols=1)
>>> w.set_names(["weight"])
>>> train = train.cbind(w)
>>> ecology_gbm = H2OGradientBoostingEstimator(ntrees=10,
...                                            max_depth=5,
...                                            min_rows=10,
...                                            learn_rate=0.1,
...                                            distribution="multinomial",
...                                            weights_column="weight",
...                                            calibrate_model=True,
...                                            calibration_frame=calib)
>>> ecology_gbm.train(x=predictors,
...                   y="Angaus",
...                   training_frame=train)
>>> ecology_gbm.auc()
""",
    calibration_frame="""
>>> ecology = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/ecology_model.csv")
>>> ecology['Angaus'] = ecology['Angaus'].asfactor()
>>> response = 'Angaus'
>>> predictors = ecology.columns[3:13]
>>> train, calib = ecology.split_frame(seed=12354)
>>> w = h2o.create_frame(binary_fraction=1,
...                      binary_ones_fraction=0.5,
...                      missing_fraction=0,
...                      rows=744,cols=1)
>>> w.set_names(["weight"])
>>> train = train.cbind(w)
>>> ecology_gbm = H2OGradientBoostingEstimator(ntrees=10,
...                                            max_depth=5,
...                                            min_rows=10,
...                                            learn_rate=0.1,
...                                            distribution="multinomial",
...                                            calibrate_model=True,
...                                            calibration_frame=calib)
>>> ecology_gbm.train(x=predictors,
...                   y="Angaus",
...                   training_frame=train,
...                   weights_column="weight")
>>> ecology_gbm.auc()
""",
    custom_distribution_func="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> airlines["Year"] = airlines["Year"].asfactor()
>>> airlines["Month"] = airlines["Month"].asfactor()
>>> airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
>>> airlines["Cancelled"] = airlines["Cancelled"].asfactor()
>>> airlines['FlightNum'] = airlines['FlightNum'].asfactor()
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> response = "IsDepDelayed"
>>> train, valid = airlines.split_frame(ratios=[.8], seed=1234)
>>> airlines_gbm = H2OGradientBoostingEstimator(ntrees=3,
...                                             max_depth=5,
...                                             distribution="bernoulli",
...                                             seed=1234)
>>> airlines_gbm.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame valid)
>>> from h2o.utils.distributions import CustomDistributionBernoulli
>>> custom_distribution_bernoulli = h2o.upload_custom_distribution(CustomDistributionBernoulli,
...                                                                func_name="custom_bernoulli",
...                                                                func_file="custom_bernoulli.py")
>>> airlines_gbm_custom = H2OGradientBoostingEstimator(ntrees=3,
...                                                    max_depth=5,
...                                                    distribution="custom",
...                                                    custom_distribution_func=custom_distribution_bernoulli,
...                                                    seed=1235)
>>> airlines_gbm_custom.train(x=predictors,
...                           y=response,
...                           training_frame=train,
...                           validation_frame=valid)
>>> airlines_gbm.auc()
""",
    export_checkpoints_dir="""
>>> airlines = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip", destination_frame="air.hex")
>>> predictors = ["DayofMonth", "DayOfWeek"]
>>> response = "IsDepDelayed"
>>> hyper_parameters = {'ntrees': [5,10]}
>>> search_crit = {'strategy': "RandomDiscrete",
...                'max_models': 5,
...                'seed': 1234,
...                'stopping_rounds': 3,
...                'stopping_metric': "AUTO",
...                'stopping_tolerance': 1e-2}
>>> checkpoints_dir = tempfile.mkdtemp()
>>> air_grid = H2OGridSearch(H2OGradientBoostingEstimator,
...                          hyper_params=hyper_parameters,
...                          search_criteria=search_crit)
>>> air_grid.train(x=predictors,
...                y=response,
...                training_frame=airlines,
...                distribution="bernoulli",
...                learn_rate=0.1,
...                max_depth=3,
...                export_checkpoints_dir=checkpoints_dir)
>>> len(listdir(checkpoints_dir))
""",
    monotone_constraints="""
>>> prostate_hex = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")
>>> prostate_hex["CAPSULE"] = prostate_hex["CAPSULE"].asfactor()
>>> response = "CAPSULE"
>>> seed = 42
>>> monotone_constraints = {"AGE":1}
>>> gbm_model = H2OGradientBoostingEstimator(seed=seed,
...                                          monotone_constraints=monotone_constraints)
>>> gbm_model.train(y=response,
...                 ignored_columns=["ID"],
...                 training_frame=prostate_hex)
>>> gbm_model.scoring_history()
""",
    check_constant_response="""
>>> train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/iris/iris_train.csv")
>>> train["constantCol"] = 1
>>> my_gbm = H2OGradientBoostingEstimator(check_constant_response=False)
>>> my_gbm.train(x=list(range(1,5)),
...              y="constantCol",
...              training_frame=train)
""",
    gainslift_bins="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/testng/airlines_train.csv")
>>> model = H2OGradientBoostingEstimator(ntrees=1, gainslift_bins=20)
>>> model.train(x=["Origin", "Distance"],
...             y="IsDepDelayed",
...             training_frame=airlines)
>>> model.gains_lift()
"""
)
