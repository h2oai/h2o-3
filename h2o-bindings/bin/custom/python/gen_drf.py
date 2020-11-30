deprecated = ['offset_column', 'distribution']

doc = dict(
    __class__="""
Builds a Distributed Random Forest (DRF) on a parsed dataset, for regression or 
classification. 
"""
)

examples = dict(
    training_frame="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8],
...                                 seed=1234)
>>> cars_drf = H2ORandomForestEstimator(seed=1234)
>>> cars_drf.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> cars_drf.auc(valid=True)
""",
    validation_frame="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8],
...                                 seed=1234)
>>> cars_drf = H2ORandomForestEstimator(seed=1234)
>>> cars_drf.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> cars_drf.auc(valid=True)
""",
    nfolds="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> folds = 5
>>> cars_drf = H2ORandomForestEstimator(nfolds=folds,
...                                     seed=1234)
>>> cars_drf.train(x=predictors,
...                y=response,
...                training_frame=cars)
>>> cars_drf.auc(xval=True)
""",
    keep_cross_validation_models="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_drf = H2ORandomForestEstimator(keep_cross_validation_models=True,
...                                     nfolds=5,
...                                     seed=1234)
>>> cars_drf.train(x=predictors,
...                y=response,
...                training_frame=train)
>>> cars_drf.auc()
""",
    keep_cross_validation_predictions="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_drf = H2ORandomForestEstimator(keep_cross_validation_predictions=True,
...                                     nfolds=5,
...                                     seed=1234)
>>> cars_drf.train(x=predictors,
...                y=response,
...                training_frame=train)
>>> cars_drf.cross_validation_predictions()
""",
    keep_cross_validation_fold_assignment="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_drf = H2ORandomForestEstimator(keep_cross_validation_fold_assignment=True,
...                                     nfolds=5,
...                                     seed=1234)
>>> cars_drf.train(x=predictors,
...                y=response,
...                training_frame=train)
>>> cars_drf.cross_validation_fold_assignment()
""",
    score_each_iteration="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_drf = H2ORandomForestEstimator(score_each_iteration=True,
...                                     ntrees=55,
...                                     seed=1234)
>>> cars_drf.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame = valid)
>>> cars_drf.scoring_history()
""",
    score_tree_interval="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_drf = H2ORandomForestEstimator(score_tree_interval=5,
...                                     seed=1234)
>>> cars_drf.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> cars_drf.scoring_history()
""",
    fold_assignment="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> assignment_type = "Random"
>>> cars_drf = H2ORandomForestEstimator(fold_assignment=assignment_type,
...                                     nfolds=5,
...                                     seed=1234)
>>> cars_drf.train(x=predictors,
...                y=response,
...                training_frame=cars)
>>> cars_drf.auc(xval=True)
""",
    fold_column="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> fold_numbers = cars.kfold_column(n_folds=5, seed=1234)
>>> fold_numbers.set_names(["fold_numbers"])
>>> cars = cars.cbind(fold_numbers)
>>> print(cars['fold_numbers'])
>>> cars_drf = H2ORandomForestEstimator(seed=1234)
>>> cars_drf.train(x=predictors,
...                y=response,
...                training_frame=cars,
...                fold_column="fold_numbers")
>>> cars_drf.auc(xval=True)
""",
    ignore_const_cols="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> cars["const_1"] = 6
>>> cars["const_2"] = 7
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_drf = H2ORandomForestEstimator(seed=1234,
...                                     ignore_const_cols=True)
>>> cars_drf.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> cars_drf.auc(valid=True)
""",
    weights_column="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8],
...                                 seed=1234)
>>> cars_drf = H2ORandomForestEstimator(seed=1234)
>>> cars_drf.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid,
...                weights_column="weight")
>>> cars_drf.auc(valid=True)
""",
    balance_classes="""
>>> covtype = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/covtype/covtype.20k.data")
>>> covtype[54] = covtype[54].asfactor()
>>> predictors = covtype.columns[0:54]
>>> response = 'C55'
>>> train, valid = covtype.split_frame(ratios=[.8], seed=1234)
>>> cov_drf = H2ORandomForestEstimator(balance_classes=True,
...                                    seed=1234)
>>> cov_drf.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> print('logloss', cov_drf.logloss(valid=True))
""",
    class_sampling_factors="""
>>> covtype = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/covtype/covtype.20k.data")
>>> covtype[54] = covtype[54].asfactor()
>>> predictors = covtype.columns[0:54]
>>> response = 'C55'
>>> train, valid = covtype.split_frame(ratios=[.8], seed=1234)
>>> print(covtype[54].table())
>>> sample_factors = [1., 0.5, 1., 1., 1., 1., 1.]
>>> cov_drf = H2ORandomForestEstimator(balance_classes=True,
...                                    class_sampling_factors=sample_factors,
...                                    seed=1234)
>>> cov_drf.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> print('logloss', cov_drf.logloss(valid=True))
""",
    max_after_balance_size="""
>>> covtype = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/covtype/covtype.20k.data")
>>> covtype[54] = covtype[54].asfactor()
>>> predictors = covtype.columns[0:54]
>>> response = 'C55'
>>> train, valid = covtype.split_frame(ratios=[.8], seed=1234)
>>> print(covtype[54].table())
>>> max = .85
>>> cov_drf = H2ORandomForestEstimator(balance_classes=True,
...                                    max_after_balance_size=max,
...                                    seed=1234)
>>> cov_drf.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> print('logloss', cov_drf.logloss(valid=True))
""",
    ntrees="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> titanic['survived'] = titanic['survived'].asfactor()
>>> predictors = titanic.columns
>>> del predictors[1:3]
>>> response = 'survived'
>>> train, valid = titanic.split_frame(ratios=[.8],
...                                    seed=1234)
>>> tree_num = [20, 50, 80, 110,
...             140, 170, 200]
>>> label = ["20", "50", "80", "110",
...          "140", "170", "200"]
>>> for key, num in enumerate(tree_num):
#              Input an integer for 'num' and 'key'
>>> titanic_drf = H2ORandomForestEstimator(ntrees=num,
...                                        seed=1234)
>>> titanic_drf.train(x=predictors,
...                   y=response,
...                   training_frame=train,
...                   validation_frame=valid)
>>> print(label[key], 'training score',
...       titanic_drf.auc(train=True))
>>> print(label[key], 'validation score',
...       titanic_drf.auc(valid=True))
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
...                                     ['train.hex','valid.hex','test.hex'])
>>> drf = H2ORandomForestEstimator()
>>> drf.train(x=predictors,
...           y=response,
...           training_frame=train)
>>> perf = drf.model_performance(valid)
>>> print perf.auc()
""",
    min_rows="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_drf = H2ORandomForestEstimator(min_rows=16,
...                                     seed=1234)
>>> cars_drf.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> print(cars_drf.auc(valid=True))
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
#              Insert integer for 'num' and 'key'
>>> eeg_drf = H2ORandomForestEstimator(nbins=num, seed=1234)
>>> eeg_drf.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> print(label[key], 'training score',
...       eeg_drf.auc(train=True))
>>> print(label[key], 'validation score',
...       eeg_drf.auc(train=True))
""",
    nbins_top_level="""
>>> eeg = h2o.import_file("https://h2o-public-test-data.s3.amazonaws.com/smalldata/eeg/eeg_eyestate.csv")
>>> eeg['eyeDetection'] = eeg['eyeDetection'].asfactor()
>>> predictors = eeg.columns[:-1]
>>> response = 'eyeDetection'
>>> train, valid = eeg.split_frame(ratios=[.8],
...                                seed=1234)
>>> bin_num = [32, 64, 128, 256, 512,
...            1024, 2048, 4096]
>>> label = ["32", "64", "128", "256",
...          "512", "1024", "2048", "4096"]
>>> for key, num in enumerate(bin_num):
#              Insert integer for 'num' and 'key'
>>> eeg_drf = H2ORandomForestEstimator(nbins_top_level=32,
...                                    seed=1234)
>>> eeg_drf.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> print(label[key], 'training score',
...       eeg_gbm.auc(train=True))
>>> print(label[key], 'validation score',
...       eeg_gbm.auc(valid=True))
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
>>> train, valid= airlines.split_frame(ratios=[.8], seed=1234)
>>> bin_num = [8, 16, 32, 64, 128, 256,
...            512, 1024, 2048, 4096]
>>> label = ["8", "16", "32", "64", "128",
...          "256", "512", "1024", "2048", "4096"]
>>> for key, num in enumerate(bin_num):
#              Insert integer for 'num' and 'key'
>>> airlines_drf = H2ORandomForestEstimator(nbins_cats=num,
...                                         seed=1234)
>>> airlines_drf.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> print(label[key], 'training score',
...       airlines_gbm.auc(train=True))
>>> print(label[key], 'validation score',
...       airlines_gbm.auc(valid=True))
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
>>> airlines_drf = H2ORandomForestEstimator(stopping_metric="auc",
...                                         stopping_rounds=3,
...                                         stopping_tolerance=1e-2,
...                                         seed=1234)
>>> airlines_drf.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> airlines_drf.auc(valid=True)
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
>>> train, valid= airlines.split_frame(ratios=[.8],
...                                    seed=1234)
>>> airlines_drf = H2ORandomForestEstimator(stopping_metric="auc",
...                                         stopping_rounds=3,
...                                         stopping_tolerance=1e-2,
...                                         seed=1234)
>>> airlines_drf.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> airlines_drf.auc(valid=True)
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
>>> airlines_drf = H2ORandomForestEstimator(stopping_metric="auc",
...                                         stopping_rounds=3,
...                                         stopping_tolerance=1e-2,
...                                         seed=1234)
>>> airlines_drf.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> airlines_drf.auc(valid=True)
""",
    max_runtime_secs="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_drf = H2ORandomForestEstimator(max_runtime_secs=10,
...                                     ntrees=10000,
...                                     max_depth=10,
...                                     seed=1234)
>>> cars_drf.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> cars_drf.auc(valid = True)
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
>>> drf_w_seed_1 = H2ORandomForestEstimator(seed=1234)
>>> drf_w_seed_1.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> print('auc for the 1st model build with a seed:',
...        drf_w_seed_1.auc(valid=True))
""",
    build_tree_one_node="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_drf = H2ORandomForestEstimator(build_tree_one_node=True,
...                                     seed=1234)
>>> cars_drf.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> cars_drf.auc(valid=True)
""",
    mtries="""
>>> covtype = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/covtype/covtype.20k.data")
>>> covtype[54] = covtype[54].asfactor()
>>> predictors = covtype.columns[0:54]
>>> response = 'C55'
>>> train, valid = covtype.split_frame(ratios=[.8], seed=1234)
>>> cov_drf = H2ORandomForestEstimator(mtries=30, seed=1234)
>>> cov_drf.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> print('logloss', cov_drf.logloss(valid=True))
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
>>> airlines_drf = H2ORandomForestEstimator(sample_rate=.7,
...                                         seed=1234)
>>> airlines_drf.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> print(airlines_drf.auc(valid=True))
""",
    sample_rate_per_class="""
>>> covtype = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/covtype/covtype.20k.data")
>>> covtype[54] = covtype[54].asfactor()
>>> predictors = covtype.columns[0:54]
>>> response = 'C55'
>>> train, valid = covtype.split_frame(ratios=[.8],
...                                    seed=1234)
>>> print(train[response].table())
>>> rate_per_class_list = [1, .4, 1, 1, 1, 1, 1]
>>> cov_drf = H2ORandomForestEstimator(sample_rate_per_class=rate_per_class_list,
...                                    seed=1234)
>>> cov_drf.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> print('logloss', cov_drf.logloss(valid=True))
""",
    binomial_double_trees="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_drf = H2ORandomForestEstimator(binomial_double_trees=False,
...                                     seed=1234)
>>> cars_drf.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> print('without binomial_double_trees:',
...        cars_drf.auc(valid=True))
>>> cars_drf_2 = H2ORandomForestEstimator(binomial_double_trees=True,
...                                       seed=1234)
>>> cars_drf_2.train(x=predictors,
...                  y=response,
...                  training_frame=train,
...                  validation_frame=valid)
>>> print('with binomial_double_trees:', cars_drf_2.auc(valid=True))
""",
    checkpoint="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8],
...                                 seed=1234)
>>> cars_drf = H2ORandomForestEstimator(ntrees=1,
...                                     seed=1234)
>>> cars_drf.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> print(cars_drf.auc(valid=True))
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
>>> train, valid= airlines.split_frame(ratios=[.8], seed=1234)
>>> airlines_drf = H2ORandomForestEstimator(col_sample_rate_change_per_level=.9,
...                                         seed=1234)
>>> airlines_drf.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>>  print(airlines_drf.auc(valid=True))
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
>>> airlines_drf = H2ORandomForestEstimator(col_sample_rate_per_tree=.7,
...                                         seed=1234)
>>> airlines_drf.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> print(airlines_drf.auc(valid=True))
""",
    min_split_improvement="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_drf = H2ORandomForestEstimator(min_split_improvement=1e-3,
...                                     seed=1234)
>>> cars_drf.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> print(cars_drf.auc(valid=True))
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
>>> train, valid= airlines.split_frame(ratios=[.8], seed=1234)
>>> airlines_drf = H2ORandomForestEstimator(histogram_type="UniformAdaptive",
...                                         seed=1234)
>>> airlines_drf.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> print(airlines_drf.auc(valid=True))
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
>>> train, valid= airlines.split_frame(ratios=[.8], seed=1234)
>>> encoding = "one_hot_explicit"
>>> airlines_drf = H2ORandomForestEstimator(categorical_encoding=encoding,
...                                         seed=1234)
>>> airlines_drf.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> airlines_drf.auc(valid=True)
""",
    calibrate_model="""
>>> ecology = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/ecology_model.csv")
>>> ecology['Angaus'] = ecology['Angaus'].asfactor()
>>> from h2o.estimators.random_forest import H2ORandomForestEstimator
>>> response = 'Angaus'
>>> predictors = ecology.columns[3:13]
>>> train, calib = ecology.split_frame(seed=12354)
>>> w = h2o.create_frame(binary_fraction=1,
...                      binary_ones_fraction=0.5,
...                      missing_fraction=0,
...                      rows=744, cols=1)
>>> w.set_names(["weight"])
>>> train = train.cbind(w)
>>> ecology_drf = H2ORandomForestEstimator(ntrees=10,
...                                        max_depth=5,
...                                        min_rows=10,
...                                        distribution="multinomial",
...                                        weights_column="weight",
...                                        calibrate_model=True,
...                                        calibration_frame=calib)
>>> ecology_drf.train(x=predictors,
...                   y="Angaus",
...                   training_frame=train)
>>> predicted = ecology_drf.predict(calib)
""",
    calibration_frame="""
>>> ecology = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/ecology_model.csv")
>>> ecology['Angaus'] = ecology['Angaus'].asfactor()
>>> response = 'Angaus'
>>> predictors = ecology.columns[3:13]
>>> train, calib = ecology.split_frame(seed = 12354)
>>> w = h2o.create_frame(binary_fraction=1,
...                      binary_ones_fraction=0.5,
...                      missing_fraction=0,
...                      rows=744, cols=1)
>>> w.set_names(["weight"])
>>> train = train.cbind(w)
>>> ecology_drf = H2ORandomForestEstimator(ntrees=10,
...                                        max_depth=5,
...                                        min_rows=10,
...                                        distribution="multinomial",
...                                        calibrate_model=True,
...                                        calibration_frame=calib)
>>> ecology_drf.train(x=predictors,
...                   y="Angaus,
...                   training_frame=train,
...                   weights_column="weight")
>>> predicted = ecology_drf.predict(train)
""",
    distribution="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "cylinders"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_drf = H2ORandomForestEstimator(distribution="poisson",
...                                     seed=1234)
>>> cars_drf.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> cars_drf.mse(valid=True)
""",
    export_checkpoints_dir="""
>>> import tempfile
>>> from os import listdir
>>> from h2o.grid.grid_search import H2OGridSearch
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
>>> air_grid = H2OGridSearch(H2ORandomForestEstimator,
...                          hyper_params=hyper_parameters,
...                          search_criteria=search_crit)
>>> air_grid.train(x=predictors,
...                y=response,
...                training_frame=airlines,
...                distribution="bernoulli",
...                max_depth=3,
...                export_checkpoints_dir=checkpoints_dir)
>>> num_files = len(listdir(checkpoints_dir))
>>> num_files
""",
    check_constant_response="""
>>> train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/iris/iris_train.csv")
>>> train["constantCol"] = 1
>>> my_drf = H2ORandomForestEstimator(check_constant_response=False)
>>> my_drf.train(x=list(range(1,5)),
...              y="constantCol",
...              training_frame=train)
""",
    gainslift_bins="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/testng/airlines_train.csv")
>>> model = H2ORandomForestEstimator(ntrees=1, gainslift_bins=20)
>>> model.train(x=["Origin", "Distance"],
...             y="IsDepDelayed",
...             training_frame=airlines)
>>> model.gains_lift()
"""
)
