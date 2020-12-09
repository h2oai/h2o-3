def class_extensions():
    @staticmethod
    def available():
        """
        Ask the H2O server whether a XGBoost model can be built (depends on availability of native backends).
        :return: True if a XGBoost model can be built, or False otherwise.

        :examples:

        >>> boston = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/BostonHousing.csv")
        >>> predictors = boston.columns[:-1]
        >>> response = "medv"
        >>> boston['chas'] = boston['chas'].asfactor()
        >>> train, valid = boston.split_frame(ratios=[.8])
        >>> boston_xgb = H2OXGBoostEstimator(seed=1234)
        >>> boston_xgb.available()
        """
        if "XGBoost" not in h2o.cluster().list_core_extensions():
            print("Cannot build an XGBoost model - no backend found.")
            return False
        else:
            return True


extensions = dict(
    __imports__="""import h2o""",
    __class__=class_extensions,
)

overrides = dict(
    gpu_id=dict(
        setter="""
assert_is_type(gpu_id, None, int, [int])
self._parms["gpu_id"] = gpu_id
"""
    )
)

doc = dict(
    __class__="""
Builds an eXtreme Gradient Boosting model using the native XGBoost backend.
""",
)

examples = dict(
    training_frame="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> titanic['survived'] = titanic['survived'].asfactor()
>>> predictors = titanic.columns
>>> del predictors[1:3]
>>> response = 'survived'
>>> train, valid = titanic.split_frame(ratios=[.8],
...                                    seed=1234)
>>> titanic_xgb = H2OXGBoostEstimator(seed=1234)
>>> titanic_xgb.train(x=predictors,
...                   y=response,
...                   training_frame=train,
...                   validation_frame=valid)
>>> titanic_xgb.auc(valid=True)
""",
    validation_frame="""
>>> insurance = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/insurance.csv")
>>> insurance['Group'] = insurance['Group'].asfactor()
>>> insurance['Age'] = insurance['Age'].asfactor()
>>> predictors = insurance.columns[0:4]
>>> response = 'Claims'
>>> train, valid = insurance.split_frame(ratios=[.8],
...                                      seed=1234)
>>> insurance_xgb = H2OXGBoostEstimator(seed=1234)
>>> insurance_xgb.train(x=predictors,
...                     y=response,
...                     training_frame=train,
...                     validation_frame=valid)
>>> print(insurance_xgb.mse(valid=True))
""",
    nfolds="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> titanic['survived'] = titanic['survived'].asfactor()
>>> predictors = titanic.columns
>>> del predictors[1:3]
>>> response = 'survived'
>>> folds = 5
>>> titanic_xgb = H2OXGBoostEstimator(nfolds=folds,
...                                   seed=1234)
>>> titanic_xgb.train(x=predictors,
...                   y=response,
...                   training_frame=titanic)
>>> titanic_xgb.auc(xval=True)
""",
    keep_cross_validation_models="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> titanic['survived'] = titanic['survived'].asfactor()
>>> predictors = titanic.columns
>>> response = 'survived'
>>> train, valid = titanic.split_frame(ratios=[.8],
...                                    seed=1234)
>>> titanic_xgb = H2OXGBoostEstimator(keep_cross_validation_models=True,
...                                   nfolds=5 ,
...                                   seed=1234)
>>> titanic_xgb.train(x=predictors,
...                   y=response,
...                   training_frame=train)
>>> titanic_xgb.cross_validation_models()
""",
    keep_cross_validation_predictions="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> titanic['survived'] = titanic['survived'].asfactor()
>>> predictors = titanic.columns
>>> response = 'survived'
>>> train, valid = titanic.split_frame(ratios=[.8],
...                                    seed=1234)
>>> titanic_xgb = H2OXGBoostEstimator(keep_cross_validation_predictions=True,
...                                   nfolds=5,
...                                   seed=1234)
>>> titanic_xgb.train(x=predictors,
...                   y=response,
...                   training_frame=train)
>>> titanic_xgb.cross_validation_predictions()
""",
    keep_cross_validation_fold_assignment="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> titanic['survived'] = titanic['survived'].asfactor()
>>> predictors = titanic.columns
>>> response = 'survived'
>>> train, valid = titanic.split_frame(ratios=[.8],
...                                    seed=1234)
>>> titanic_xgb = H2OXGBoostEstimator(keep_cross_validation_fold_assignment=True,
...                                   nfolds=5,
...                                   seed=1234)
>>> titanic_xgb.train(x=predictors,
...                   y=response,
...                   training_frame=train)
>>> titanic_xgb.cross_validation_fold_assignment()
""",
    score_each_iteration="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> airlines["Year"] = airlines["Year"].asfactor()
>>> airlines["Month"] = airlines["Month"].asfactor()
>>> airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
>>> airlines["Cancelled"] = airlines["Cancelled"].asfactor()
>>> airlines['FlightNum'] = airlines['FlightNum'].asfactor()
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> response = "IsDepDelayed"
>>> train, valid= airlines.split_frame(ratios=[.8],
...                                    seed=1234)
>>> airlines_xgb = H2OXGBoostEstimator(score_each_iteration=True,
...                                    ntrees=55,
...                                    seed=1234)
>>> airlines_xgb.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> airlines_xgb.scoring_history()
""",
    fold_assignment="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> titanic['survived'] = titanic['survived'].asfactor()
>>> predictors = titanic.columns
>>> response = 'survived'
>>> assignment_type = "Random"
>>> titanic_xgb = H2OXGBoostEstimator(fold_assignment=assignment_type,
...                                   nfolds=5,
...                                   seed=1234)
>>> titanic_xgb.train(x=predictors,
...                   y=response,
...                   training_frame=titanic)
>>> titanic_xgb.auc(xval=True)
""",
    fold_column="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> titanic['survived'] = titanic['survived'].asfactor()
>>> predictors = titanic.columns
>>> response = 'survived'
>>> fold_numbers = titanic.kfold_column(n_folds=5,
...                                     seed=1234)
>>> fold_numbers.set_names(["fold_numbers"])
>>> titanic = titanic.cbind(fold_numbers)
>>> print(titanic['fold_numbers'])
>>> titanic_xgb = H2OXGBoostEstimator(seed=1234)
>>> titanic_xgb.train(x=predictors,
...                   y=response,
...                   training_frame=titanic,
...                   fold_column="fold_numbers")
>>> titanic_xgb.auc(xval=True)
""",
    ignore_const_cols="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> titanic['survived'] = titanic['survived'].asfactor()
>>> predictors = titanic.columns
>>> response = 'survived'
>>> titanic["const_1"] = 6
>>> titanic["const_2"] = 7
>>> train, valid = titanic.split_frame(ratios=[.8],
...                                    seed=1234)
>>> titanic_xgb = H2OXGBoostEstimator(seed=1234,
...                                   ignore_const_cols=True)
>>> titanic_xgb.train(x=predictors,
...                   y=response,
...                   training_frame=train,
...                   validation_frame=valid)
>>> titanic_xgb.auc(valid=True)
""",
    weights_column="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> titanic['survived'] = titanic['survived'].asfactor()
>>> predictors = titanic.columns
>>> del predictors[1:3]
>>> response = 'survived'
>>> train, valid = titanic.split_frame(ratios=[.8],
...                                    seed=1234)
>>> titanic_xgb = H2OXGBoostEstimator(seed=1234)
>>> titanic_xgb.train(x=predictors,
...                   y=response,
...                   training_frame=train,
...                   validation_frame=valid)
>>> titanic_xgb.auc(valid=True)
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
>>> train, valid= airlines.split_frame(ratios=[.8],
...                                    seed=1234)
>>> airlines_xgb = H2OXGBoostEstimator(stopping_metric="auc",
...                                    stopping_rounds=3,
...                                    stopping_tolerance=1e-2,
...                                    seed=1234)
>>> airlines_xgb.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> airlines_xgb.auc(valid=True)
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
>>> train, valid= airlines.split_frame(ratios=[.8], seed=1234)
>>> airlines_xgb = H2OXGBoostEstimator(stopping_metric="auc",
...                                    stopping_rounds=3,
...                                    stopping_tolerance=1e-2,
...                                    seed=1234)
>>> airlines_xgb.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> airlines_xgb.auc(valid=True)
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
>>> train, valid= airlines.split_frame(ratios=[.8],
...                                    seed=1234)
>>> airlines_xgb = H2OXGBoostEstimator(stopping_metric="auc",
...                                    stopping_rounds=3,
...                                    stopping_tolerance=1e-2,
...                                    seed=1234)
>>> airlines_xgb.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> airlines_xgb.auc(valid=True)
""",
    max_runtime_secs="""
>>> covtype = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/covtype/covtype.20k.data")
>>> covtype[54] = covtype[54].asfactor()
>>> predictors = covtype.columns[0:54]
>>> response = 'C55'
>>> train, valid = covtype.split_frame(ratios=[.8],
...                                    seed=1234)
>>> cov_xgb = H2OXGBoostEstimator(max_runtime_secs=10,
...                               ntrees=10000,
...                               max_depth=10,
...                               seed=1234)
>>> cov_xgb.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> print(cov_xgb.logloss(valid=True))
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
>>> xgb_w_seed_1 = H2OXGBoostEstimator(col_sample_rate=.7,
...                                    seed=1234)
>>> xgb_w_seed_1.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> xgb_w_seed_2 = H2OXGBoostEstimator(col_sample_rate = .7,
...                                    seed = 1234)
>>> xgb_w_seed_2.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> print('auc for the 1st model built with a seed:',
...        xgb_w_seed_1.auc(valid=True))
>>> print('auc for the 2nd model built with a seed:',
...        xgb_w_seed_2.auc(valid=True))
""",
    distribution="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "cylinders"
>>> train, valid = cars.split_frame(ratios=[.8],
...                                 seed=1234)
>>> cars_xgb = H2OXGBoostEstimator(distribution="poisson",
...                                seed=1234)
>>> cars_xgb.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> cars_xgb.mse(valid=True)
""",
    tweedie_power="""
>>> insurance = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/insurance.csv")
>>> predictors = insurance.columns[0:4]
>>> response = 'Claims'
>>> insurance['Group'] = insurance['Group'].asfactor()
>>> insurance['Age'] = insurance['Age'].asfactor()
>>> train, valid = insurance.split_frame(ratios=[.8],
...                                      seed=1234)
>>> insurance_xgb = H2OXGBoostEstimator(distribution="tweedie",
...                                     tweedie_power=1.2,
...                                     seed=1234)
>>> insurance_xgb.train(x=predictors,
...                     y=response,
...                     training_frame=train,
...                     validation_frame=valid)
>>> print(insurance_xgb.mse(valid=True))
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
>>> train, valid= airlines.split_frame(ratios=[.8],
...                                    seed=1234)
>>> encoding = "one_hot_explicit"
>>> airlines_xgb = H2OXGBoostEstimator(categorical_encoding=encoding,
...                                    seed=1234)
>>> airlines_xgb.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> airlines_xgb.auc(valid=True)
""",
    quiet_mode="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> titanic['survived'] = titanic['survived'].asfactor()
>>> predictors = titanic.columns
>>> del predictors[1:3]
>>> response = 'survived'
>>> train, valid = titanic.split_frame(ratios=[.8], seed=1234)
>>> titanic_xgb = H2OXGBoostEstimator(seed=1234, quiet_mode=True)
>>> titanic_xgb.train(x=predictors
...                   y=response,
...                   training_frame=train,
...                   validation_frame=valid)
>>> titanic_xgb.mse(valid=True)
""",
    checkpoint="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","year","economy_20mpg"]
>>> response = "acceleration"
>>> from h2o.estimators import H2OXGBoostEstimator
>>> cars_xgb = H2OXGBoostEstimator(seed=1234)
>>> train, valid = cars.split_frame(ratios=[.8])
>>> cars_xgb.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> cars_xgb.mse()
>>> cars_xgb_continued = H2OXGBoostEstimator(checkpoint=cars_xgb.model_id,
...                                          ntrees=51,
...                                          seed=1234)
>>> cars_xgb_continued.train(x=predictors,
...                          y=response,
...                          training_frame=train,
...                          validation_frame=valid)
>>> cars_xgb_continued.mse()
""",
    export_checkpoints_dir="""
>>> import tempfile
>>> from h2o.grid.grid_search import H2OGridSearch
>>> from os import listdir
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
>>> air_grid = H2OGridSearch(H2OXGBoostEstimator,
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
    ntrees="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> titanic['survived'] = titanic['survived'].asfactor()
>>> predictors = titanic.columns
>>> del predictors[1:3]
>>> response = 'survived'
>>> train, valid = titanic.split_frame(ratios=[.8],
...                                    seed=1234)
>>> tree_num = [20, 50, 80, 110, 140, 170, 200]
>>> label = ["20", "50", "80", "110",
...          "140", "170", "200"]
>>> for key, num in enumerate(tree_num):
#              Input integer for 'num' and 'key'
>>> titanic_xgb = H2OXGBoostEstimator(ntrees=num,
...                                   seed=1234)
>>> titanic_xgb.train(x=predictors,
...                   y=response,
...                   training_frame=train,
...                   validation_frame=valid)
>>> print(label[key], 'training score',
...       titanic_xgb.auc(train=True))
>>> print(label[key], 'validation score',
...       titanic_xgb.auc(valid=True))
""",
    max_depth="""
>>> df = h2o.import_file(path = "http://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> response = "survived"
>>> df[response] = df[response].asfactor()
>>> predictors = df.columns
>>> del predictors[1:3]
>>> train, valid, test = df.split_frame(ratios=[0.6,0.2],
...                                     seed=1234,
...                                     destination_frames=
...                                     ['train.hex',
...                                     'valid.hex',
...                                     'test.hex'])
>>> xgb = H2OXGBoostEstimator()
>>> xgb.train(x=predictors,
...           y=response,
...           training_frame=train)
>>> perf = xgb.model_performance(valid)
>>> print perf.auc()
""",
    min_rows="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> titanic['survived'] = titanic['survived'].asfactor()
>>> predictors = titanic.columns
>>> del predictors[1:3]
>>> response = 'survived'
>>> train, valid = titanic.split_frame(ratios=[.8],
...                                    seed=1234)
>>> titanic_xgb = H2OXGBoostEstimator(min_rows=16,
...                                   seed=1234)
>>> titanic_xgb.train(x=predictors,
...                   y=response,
...                   training_frame=train,
...                   validation_frame=valid)
>>> print(titanic_xgb.auc(valid=True))
""",
    min_child_weight="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> titanic['survived'] = titanic['survived'].asfactor()
>>> predictors = titanic.columns
>>> del predictors[1:3]
>>> response = 'survived'
>>> train, valid = titanic.split_frame(ratios=[.8],
...                                    seed=1234)
>>> titanic_xgb = H2OXGBoostEstimator(min_child_weight=16,
...                                   seed=1234)
>>> titanic_xgb.train(x=predictors,
...                   y=response,
...                   training_frame=train,
...                   validation_frame=valid)
>>> print(titanic_xgb.auc(valid=True))
""",
    learn_rate="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> titanic['survived'] = titanic['survived'].asfactor()
>>> predictors = titanic.columns
>>> del predictors[1:3]
>>> response = 'survived'
>>> train, valid = titanic.split_frame(ratios=[.8], seed=1234)
>>> titanic_xgb = H2OXGBoostEstimator(ntrees=10000,
...                                   learn_rate=0.01,
...                                   stopping_rounds=5,
...                                   stopping_metric="AUC",
...                                   stopping_tolerance=1e-4,
...                                   seed=1234)
>>> titanic_xgb.train(x=predictors,
...                   y=response,
...                   training_frame=train,
...                   validation_frame=valid)
>>> print(titanic_xgb.auc(valid=True))
""",
    eta="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> titanic['survived'] = titanic['survived'].asfactor()
>>> predictors = titanic.columns
>>> del predictors[1:3]
>>> response = 'survived'
>>> train, valid = titanic.split_frame(ratios=[.8],
...                                    seed=1234)
>>> titanic_xgb = H2OXGBoostEstimator(ntrees=10000,
...                                   learn_rate=0.01,
...                                   stopping_rounds=5,
...                                   stopping_metric="AUC",
...                                   stopping_tolerance=1e-4,
...                                   seed=1234)
>>> titanic_xgb.train(x=predictors,
...                   y=response,
...                   training_frame=train,
...                   validation_frame=valid)
>>>  print(titanic_xgb.auc(valid=True))
""",
    sample_rate="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> airlines["Year"] = airlines["Year"].asfactor()
>>> airlines["Month"] = airlines["Month"].asfactor()
>>> airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
>>> airlines["Cancelled"] = airlines["Cancelled"].asfactor()
>>> airlines['FlightNum'] = airlines['FlightNum'].asfactor()
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> response = "IsDepDelayed"
>>> train, valid= airlines.split_frame(ratios=[.8],
...                                    seed=1234)
>>> airlines_xgb = H2OXGBoostEstimator(sample_rate=.7,
...                                    seed=1234)
>>> airlines_xgb.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> print(airlines_xgb.auc(valid=True))
""",
    subsample="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> airlines["Year"] = airlines["Year"].asfactor()
>>> airlines["Month"] = airlines["Month"].asfactor()
>>> airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
>>> airlines["Cancelled"] = airlines["Cancelled"].asfactor()
>>> airlines['FlightNum'] = airlines['FlightNum'].asfactor()
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> response = "IsDepDelayed"
>>> train, valid= airlines.split_frame(ratios=[.8],
...                                    seed=1234)
>>> airlines_xgb = H2OXGBoostEstimator(sample_rate=.7,
...                                    seed=1234)
>>> airlines_xgb.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> print(airlines_xgb.auc(valid=True))
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
>>> train, valid= airlines.split_frame(ratios=[.8],
...                                    seed=1234)
>>> airlines_xgb = H2OXGBoostEstimator(col_sample_rate=.7,
...                                    seed=1234)
>>> airlines_xgb.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> print(airlines_xgb.auc(valid=True))
""",
    colsample_bylevel="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> airlines["Year"] = airlines["Year"].asfactor()
>>> airlines["Month"] = airlines["Month"].asfactor()
>>> airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
>>> airlines["Cancelled"] = airlines["Cancelled"].asfactor()
>>> airlines['FlightNum'] = airlines['FlightNum'].asfactor()
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> response = "IsDepDelayed"
>>> train, valid= airlines.split_frame(ratios=[.8],
...                                    seed=1234)
>>> airlines_xgb = H2OXGBoostEstimator(col_sample_rate=.7,
...                                    seed=1234)
>>> airlines_xgb.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> print(airlines_xgb.auc(valid=True))
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
>>> train, valid= airlines.split_frame(ratios=[.8], seed=1234)
>>> airlines_xgb = H2OXGBoostEstimator(col_sample_rate_per_tree=.7,
...                                    seed=1234)
>>> airlines_xgb.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> print(airlines_xgb.auc(valid=True))
""",
    colsample_bytree="""
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
>>> airlines_xgb = H2OXGBoostEstimator(col_sample_rate_per_tree=.7,
...                                    seed=1234)
>>> airlines_xgb.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> print(airlines_xgb.auc(valid=True))
""",
    colsample_bynode="""
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
>>> airlines_xgb = H2OXGBoostEstimator(colsample_bynode=.5,
...                                    seed=1234)
>>> airlines_xgb.train(x=predictors, y=response,
...                    training_frame=train, validation_frame=valid)
>>> print(airlines_xgb.auc(valid=True))
""",
    max_abs_leafnode_pred="""
>>> covtype = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/covtype/covtype.20k.data")
>>> covtype[54] = covtype[54].asfactor()
>>> predictors = covtype.columns[0:54]
>>> response = 'C55'
>>> train, valid = covtype.split_frame(ratios=[.8],
...                                    seed=1234)
>>> cov_xgb = H2OXGBoostEstimator(max_abs_leafnode_pred=float(2),
...                               seed=1234)
>>> cov_xgb.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> print(cov_xgb.logloss(valid=True))
""",
    max_delta_step="""
>>> covtype = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/covtype/covtype.20k.data")
>>> covtype[54] = covtype[54].asfactor()
>>> predictors = covtype.columns[0:54]
>>> response = 'C55'
>>> train, valid = covtype.split_frame(ratios=[.8],
...                                    seed=1234)
>>> cov_xgb = H2OXGBoostEstimator(max_delta_step=float(2),
...                               seed=1234)
>>> cov_xgb.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> print(cov_xgb.logloss(valid=True))
""",
    monotone_constraints="""
>>> prostate_hex = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")
>>> prostate_hex["CAPSULE"] = prostate_hex["CAPSULE"].asfactor()
>>> response = "CAPSULE"
>>> seed=42
>>> monotone_constraints={"AGE":1}
>>> xgb_model = H2OXGBoostEstimator(seed=seed,
...                                 monotone_constraints=monotone_constraints)
>>> xgb_model.train(y=response,
...                 ignored_columns=["ID"],
...                 training_frame=prostate_hex)
>>> xgb_model.scoring_history()
""",
    score_tree_interval="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> airlines["Year"] = airlines["Year"].asfactor()
>>> airlines["Month"] = airlines["Month"].asfactor()
>>> airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
>>> airlines["Cancelled"] = airlines["Cancelled"].asfactor()
>>> airlines['FlightNum'] = airlines['FlightNum'].asfactor()
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> response = "IsDepDelayed"
>>> train, valid= airlines.split_frame(ratios=[.8],
...                                    seed=1234)
>>> airlines_xgb = H2OXGBoostEstimator(score_tree_interval=5,
...                                    seed=1234)
>>> airlines_xgb.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> airlines_xgb.scoring_history()
""",
    min_split_improvement="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> titanic['survived'] = titanic['survived'].asfactor()
>>> predictors = titanic.columns
>>> del predictors[1:3]
>>> response = 'survived'
>>> train, valid = titanic.split_frame(ratios=[.8],
...                                    seed=1234)
>>> titanic_xgb = H2OXGBoostEstimator(min_split_improvement=0.55,
...                                   seed=1234)
>>> titanic_xgb.train(x=predictors,
...                   y=response,
...                   training_frame=train,
...                   validation_frame=valid)
>>> print(titanic_xgb.auc(valid=True))
""",
    gamma="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> titanic['survived'] = titanic['survived'].asfactor()
>>> predictors = titanic.columns
>>> response = 'survived'
>>> train, valid = titanic.split_frame(ratios=[.8],
...                                    seed=1234)
>>> titanic_xgb = H2OXGBoostEstimator(min_split_improvement=1e-3,
...                                   seed=1234)
>>> titanic_xgb.train(x=predictors,
...                   y=response,
...                   training_frame=train,
...                   validation_frame=valid)
>>> print(titanic_xgb.auc(valid=True))
""",
    nthread="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> titanic['survived'] = titanic['survived'].asfactor()
>>> predictors = titanic.columns
>>> response = 'survived'
>>> train, valid = titanic.split_frame(ratios=[.8], seed=1234)
>>> thread = 4
>>> titanic_xgb = H2OXGBoostEstimator(nthread=thread,
...                                   seed=1234)
>>> titanic_xgb.train(x=predictors,
...                   y=response,
...                   training_frame=titanic)
>>> print(titanic_xgb.auc(train=True))
""",
    max_bins="""
>>> covtype = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/covtype/covtype.20k.data")
>>> covtype[54] = covtype[54].asfactor()
>>> predictors = covtype.columns[0:54]
>>> response = 'C55'
>>> train, valid = covtype.split_frame(ratios=[.8],
...                                    seed=1234)
>>> cov_xgb = H2OXGBoostEstimator(max_bins=200,
...                               seed=1234)
>>> cov_xgb.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> print(cov_xgb.logloss(valid=True))
""",
    max_leaves="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> titanic['survived'] = titanic['survived'].asfactor()
>>> predictors = titanic.columns
>>> del predictors[1:3]
>>> response = 'survived'
>>> train, valid = titanic.split_frame(ratios=[.8],
...                                    seed=1234)
>>> titanic_xgb = H2OXGBoostEstimator(max_leaves=0, seed=1234)
>>> titanic_xgb.train(x=predictors,
...                   y=response,
...                   training_frame=train,
...                   validation_frame=valid)
>>> print(titanic_xgb.auc(valid=True))
""",
    sample_type="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> airlines["Year"]= airlines["Year"].asfactor()
>>> airlines["Month"]= airlines["Month"].asfactor()
>>> airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
>>> airlines["Cancelled"] = airlines["Cancelled"].asfactor()
>>> airlines['FlightNum'] = airlines['FlightNum'].asfactor()
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> response = "IsDepDelayed"
>>> train, valid= airlines.split_frame(ratios=[.8],
...                                    seed=1234)
>>> airlines_xgb = H2OXGBoostEstimator(sample_type="weighted",
...                                    seed=1234)
>>> airlines_xgb.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> print(airlines_xgb.auc(valid=True))
""",
    normalize_type="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> titanic['survived'] = titanic['survived'].asfactor()
>>> predictors = titanic.columns
>>> response = 'survived'
>>> train, valid = titanic.split_frame(ratios=[.8],
...                                    seed=1234)
>>> titanic_xgb = H2OXGBoostEstimator(booster='dart',
...                                   normalize_type="tree",
...                                   seed=1234)
>>> titanic_xgb.train(x=predictors,
...                   y=response,
...                   training_frame=train,
...                   validation_frame=valid)
>>> print(titanic_xgb.auc(valid=True))
""",
    rate_drop="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> titanic['survived'] = titanic['survived'].asfactor()
>>> predictors = titanic.columns
>>> del predictors[1:3]
>>> response = 'survived'
>>> train, valid = titanic.split_frame(ratios=[.8],
...                                    seed=1234)
>>> titanic_xgb = H2OXGBoostEstimator(rate_drop=0.1, seed=1234)
>>> titanic_xgb.train(x=predictors,
...                   y=response,
...                   training_frame=train,
...                   validation_frame=valid)
>>> print(titanic_xgb.auc(valid=True))
""",
    one_drop="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> titanic['survived'] = titanic['survived'].asfactor()
>>> predictors = titanic.columns
>>> del predictors[1:3]
>>> response = 'survived'
>>> train, valid = titanic.split_frame(ratios=[.8],
...                                    seed=1234)
>>> titanic_xgb = H2OXGBoostEstimator(booster='dart',
...                                   one_drop=True,
...                                   seed=1234)
>>> titanic_xgb.train(x=predictors,
...                   y=response,
...                   training_frame=train,
...                   validation_frame=valid)
>>> print(titanic_xgb.auc(valid=True))
""",
    skip_drop="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> airlines["Year"] = airlines["Year"].asfactor()
>>> airlines["Month"] = airlines["Month"].asfactor()
>>> airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
>>> airlines["Cancelled"] = airlines["Cancelled"].asfactor()
>>> airlines['FlightNum'] = airlines['FlightNum'].asfactor()
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> response = "IsDepDelayed"
>>> train, valid= airlines.split_frame(ratios=[.8],
...                                    seed=1234)
>>> airlines_xgb = H2OXGBoostEstimator(skip_drop=0.5,
...                                    seed=1234)
>>> airlines_xgb.train(x=predictors,
...                    y=response,
...                    training_frame=train)
>>> airlines_xgb.auc(train=True)
""",
    tree_method="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> airlines["Year"] = airlines["Year"].asfactor()
>>> airlines["Month"] = airlines["Month"].asfactor()
>>> airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
>>> airlines["Cancelled"] = airlines["Cancelled"].asfactor()
>>> airlines['FlightNum'] = airlines['FlightNum'].asfactor()
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> response = "IsDepDelayed"
>>> train, valid= airlines.split_frame(ratios=[.8],
...                                    seed=1234)
>>> >>> airlines_xgb = H2OXGBoostEstimator(seed=1234,
...                                        tree_method="approx")
>>> airlines_xgb.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> print(airlines_xgb.auc(valid=True))
""",
    grow_policy="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> titanic['survived'] = titanic['survived'].asfactor()
>>> predictors = titanic.columns
>>> response = 'survived'
>>> titanic["const_1"] = 6
>>> titanic["const_2"] = 7
>>> train, valid = titanic.split_frame(ratios=[.8],
...                                    seed=1234)
>>> titanic_xgb = H2OXGBoostEstimator(seed=1234,
...                                   grow_policy="depthwise")
>>> titanic_xgb.train(x=predictors,
...                   y=response,
...                   training_frame=train,
...                   validation_frame=valid)
>>> titanic_xgb.auc(valid=True)
""",
    booster="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> titanic['survived'] = titanic['survived'].asfactor()
>>> predictors = titanic.columns
>>> response = 'survived'
>>> train, valid = titanic.split_frame(ratios=[.8],
...                                    seed=1234)
>>> titanic_xgb = H2OXGBoostEstimator(booster='dart',
...                                   normalize_type="tree",
...                                   seed=1234)
>>> titanic_xgb.train(x=predictors,
...                   y=response,
...                   training_frame=train,
...                   validation_frame=valid)
>>> print(titanic_xgb.auc(valid=True))
""",
    reg_lambda="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> airlines["Year"] = airlines["Year"].asfactor()
>>> airlines["Month"] = airlines["Month"].asfactor()
>>> airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
>>> airlines["Cancelled"] = airlines["Cancelled"].asfactor()
>>> airlines['FlightNum'] = airlines['FlightNum'].asfactor()
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> response = "IsDepDelayed"
>>> train, valid= airlines.split_frame(ratios=[.8])
>>> airlines_xgb = H2OXGBoostEstimator(reg_lambda=.0001,
...                                    seed=1234)
>>> airlines_xgb.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> print(airlines_xgb.auc(valid=True))
""",
    reg_alpha="""
>>> boston = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/BostonHousing.csv")
>>> predictors = boston.columns[:-1]
>>> response = "medv"
>>> boston['chas'] = boston['chas'].asfactor()
>>> train, valid = boston.split_frame(ratios=[.8])
>>> boston_xgb = H2OXGBoostEstimator(reg_alpha=.25)
>>> boston_xgb.train(x=predictors,
...                  y=response,
...                  training_frame=train,
...                  validation_frame=valid)
>>> print(boston_xgb.mse(valid=True))
""",
    dmatrix_type="""
>>> boston = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/BostonHousing.csv")
>>> predictors = boston.columns[:-1]
>>> response = "medv"
>>> boston['chas'] = boston['chas'].asfactor()
>>> train, valid = boston.split_frame(ratios=[.8])
>>> boston_xgb = H2OXGBoostEstimator(dmatrix_type="auto",
...                                  seed=1234)
>>> boston_xgb.train(x=predictors,
...                  y=response,
...                  training_frame=train,
...                  validation_frame=valid)
>>> boston_xgb.mse()
""",
    gpu_id="""
>>> boston = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/BostonHousing.csv")
>>> predictors = boston.columns[:-1]
>>> response = "medv"
>>> boston['chas'] = boston['chas'].asfactor()
>>> train, valid = boston.split_frame(ratios=[.8])
>>> boston_xgb = H2OXGBoostEstimator(gpu_id=0,
...                                  seed=1234)
>>> boston_xgb.train(x=predictors,
...                  y=response,
...                  training_frame=train,
...                  validation_frame=valid)
>>> boston_xgb.mse()
""",
    backend="""
>>> pros = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv")
>>> pros["CAPSULE"] = pros["CAPSULE"].asfactor()
>>> pros_xgb = H2OXGBoostEstimator(tree_method="exact",
...                                seed=123,
...                                backend="cpu")
>>> pros_xgb.train(y="CAPSULE",
...                ignored_columns=["ID"],
...                training_frame=pros)
>>> pros_xgb.auc()
""",
    gainslift_bins="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/testng/airlines_train.csv")
>>> model = H2OXGBoostEstimator(ntrees=1, gainslift_bins=20)
>>> model.train(x=["Origin", "Distance"],
...             y="IsDepDelayed",
...             training_frame=airlines)
>>> model.gains_lift()
"""
)

