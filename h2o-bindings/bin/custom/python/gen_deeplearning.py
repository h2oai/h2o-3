def module_extensions():
    class H2OAutoEncoderEstimator(H2ODeepLearningEstimator):
        """
        :examples:

        >>> import h2o as ml
        >>> from h2o.estimators.deeplearning import H2OAutoEncoderEstimator
        >>> ml.init()
        >>> rows = [[1,2,3,4,0]*50, [2,1,2,4,1]*50, [2,1,4,2,1]*50, [0,1,2,34,1]*50, [2,3,4,1,0]*50]
        >>> fr = ml.H2OFrame(rows)
        >>> fr[4] = fr[4].asfactor()
        >>> model = H2OAutoEncoderEstimator()
        >>> model.train(x=range(4), training_frame=fr)
        """
        def __init__(self, **kwargs):
            super(H2OAutoEncoderEstimator, self).__init__(**kwargs)
            self._parms['autoencoder'] = True


extensions = dict(
    __module__=module_extensions
)

overrides = dict(
    initial_biases=dict(
        setter="""
assert_is_type({pname}, None, [H2OFrame, None])
self._parms["{sname}"] = {pname}
"""
    ),

    initial_weights=dict(
        setter="""
assert_is_type({pname}, None, [H2OFrame, None])
self._parms["{sname}"] = {pname}
"""
    ),
)

doc = dict(
    __class__="""
Build a Deep Neural Network model using CPUs
Builds a feed-forward multilayer artificial neural network on an H2OFrame
"""
)

examples = dict(
    __class__="""
>>> from h2o.estimators.deeplearning import H2ODeepLearningEstimator
>>> rows = [[1,2,3,4,0], [2,1,2,4,1], [2,1,4,2,1],
...         [0,1,2,34,1], [2,3,4,1,0]] * 50
>>> fr = h2o.H2OFrame(rows)
>>> fr[4] = fr[4].asfactor()
>>> model = H2ODeepLearningEstimator()
>>> model.train(x = range(4), y = 4, training_frame = fr)
>>> model.logloss()
""",
    activation="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "cylinders"
>>> cars_dl = H2ODeepLearningEstimator(activation = "tanh")
>>> cars_dl.train(x = predictors,
...               y = response,
...               training_frame = train,
...               validation_frame = valid)
>>> cars_dl.mse()
""",
    adaptive_rate="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "cylinders"
>>> cars_dl = H2ODeepLearningEstimator(adaptive_rate = True)
>>> cars_dl.train(x = predictors,
...               y = response,
...               training_frame = train,
...               validation_frame = valid)
>>> cars_dl.mse()
""",
    autoencoder="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "cylinders"
>>> cars_dl = H2ODeepLearningEstimator(autoencoder = True)
>>> cars_dl.train(x = predictors,
...               y = response,
...               training_frame = train,
...               validation_frame = valid)
>>> cars_dl.mse()
""",
    average_activation="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "cylinders"
>>> cars_dl = H2ODeepLearningEstimator(average_activation = 1.5,
...                                    seed = 1234)
>>> cars_dl.train(x = predictors,
...               y = response,
...               training_frame = train,
...               validation_frame = valid)
>>> cars_dl.mse()
""",
    balance_classes="""
>>> covtype = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/covtype/covtype.20k.data")
>>> covtype[54] = covtype[54].asfactor()
>>> predictors = covtype.columns[0:54]
>>> response = 'C55'
>>> train, valid = covtype.split_frame(ratios = [.8], seed = 1234)
>>> cov_dl = H2ODeepLearningEstimator(balance_classes = True,
                                      seed = 1234)
>>> cov_dl.train(x = predictors,
                 y = response,
                 training_frame = train,
                 validation_frame = valid)
>>> cov_dl.mse()
""",
    categorical_encoding="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> airlines["Year"]= airlines["Year"].asfactor()
>>> airlines["Month"]= airlines["Month"].asfactor()
>>> airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
>>> airlines["Cancelled"] = airlines["Cancelled"].asfactor()
>>> airlines['FlightNum'] = airlines['FlightNum'].asfactor()
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> response = "IsDepDelayed"
>>> train, valid= airlines.split_frame(ratios = [.8], seed = 1234)
>>> encoding = "one_hot_internal"                                               >>> airlines_dl = H2ODeepLearningEstimator(categorical_encoding = encoding, seed = 1234)
>>> airlines_dl.train(x = predictors,
...                   y = response,
...                   training_frame = train,
...                   validation_frame = valid)
>>> airlines_dl.mse()
""",
    checkpoint="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> cars_dl = H2ODeepLearningEstimator(activation="tanh",
...                                    autoencoder=True,
...                                    seed = 1234,
...                                    model_id="cars_dl")
>>> cars_dl.train(x = predictors,
...               y = response,
...               training_frame = train,
...               validation_frame = valid)
>>> cars_dl.mse()
>>> cars_cont = H2ODeepLearningEstimator(checkpoint = cars_dl,
...                                      seed = 1234)
>>> cars_cont.train(x = predictors,
...                 y = response,
...                 training_frame = train,
...                 validation_frame = valid)
>>> cars_cont.mse()
""",
    class_sampling_factors="""
>>> covtype = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/covtype/covtype.20k.data")
>>> covtype[54] = covtype[54].asfactor()
>>> predictors = covtype.columns[0:54]
>>> response = 'C55'
>>> train, valid = covtype.split_frame(ratios = [.8], seed = 1234)
>>> sample_factors = [1., 0.5, 1., 1., 1., 1., 1.]
>>> cars_dl = H2ODeepLearningEstimator(balance_classes = True,
...                                    class_sampling_factors = sample_factors,
...                                    seed = 1234)
>>> cov_dl.train(x = predictors,
...              y = response,
...              training_frame = train,
...              validation_frame = valid)
>>> cov_dl.mse()
""",
    classification_stop="""
>>> covtype = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/covtype/covtype.20k.data")
>>> covtype[54] = covtype[54].asfactor()
>>> predictors = covtype.columns[0:54]
>>> response = 'C55'
>>> train, valid = covtype.split_frame(ratios = [.8], seed = 1234)
>>> cars_dl = H2ODeepLearningEstimator(classification_stop = 1.5,
...                                    seed = 1234)
>>> cov_dl.train(x = predictors,
...              y = response,
...              training_frame = train,
...              validation_frame = valid)
>>> cov_dl.mse()
""",
    diagnostics="""
>>> covtype = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/covtype/covtype.20k.data")
>>> covtype[54] = covtype[54].asfactor()
>>> predictors = covtype.columns[0:54]
>>> response = 'C55'
>>> train, valid = covtype.split_frame(ratios = [.8], seed = 1234)
>>> cars_dl = H2ODeepLearningEstimator(diagnostics = True,
...                                    seed = 1234)  
>>> cov_dl.train(x = predictors,
...              y = response,
...              training_frame = train,
...              validation_frame = valid)
>>> cov_dl.mse()
""",
    distribution="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "cylinders"
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> cars_dl = H2ODeepLearningEstimator(distribution = "poisson",
...                                    seed = 1234)
>>> cars_dl.train(x = predictors,
...               y = response,
...               training_frame = train,
...               validation_frame = valid)
>>> cars_dl.mse()
""",
    elastic_averaging="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "cylinders"
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> cars_dl = H2ODeepLearningEstimator(elastic_averaging = True,
...                                    seed = 1234)
>>> cars_dl.train(x = predictors,
...               y = response,
...               training_frame = train,
...               validation_frame = valid)
>>> cars_dl.mse()
""",
    elastic_averaging_moving_rate="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "cylinders"
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> cars_dl = H2ODeepLearningEstimator(elastic_averaging_moving_rate = .8,
...                                    seed = 1234)
>>> cars_dl.train(x = predictors,
...               y = response,
...               training_frame = train,
...               validation_frame = valid)
>>> cars_dl.mse()
""",
    elastic_averaging_regularization="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "cylinders"
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> cars_dl = H2ODeepLearningEstimator(elastic_averaging_regularization = .008,
...                                    seed = 1234)
>>> cars_dl.train(x = predictors,
...               y = response,
...               training_frame = train,
...               validation_frame = valid)
>>> cars_dl.mse()
""",
    epochs="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "cylinders"
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> cars_dl = H2ODeepLearningEstimator(epochs = 15,
...                                    seed = 1234)
>>> cars_dl.train(x = predictors,
...               y = response,
...               training_frame = train,
...               validation_frame = valid)
>>> cars_dl.mse()
""",
    epsilon="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "cylinders"
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> cars_dl = H2ODeepLearningEstimator(epsilon = 1e-6,
...                                    seed = 1234)
>>> cars_dl.train(x = predictors,
...               y = response,
...               training_frame = train,
...               validation_frame = valid)
>>> cars_dl.mse()
""",
    export_checkpoints_dir="""
>>> import tempfile
>>> from os import listdir
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "cylinders"
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> checkpoints_dir = tempfile.mkdtemp()
>>> cars_dl = H2ODeepLearningEstimator(export_checkpoints_dir=checkpoints_dir,
...                                    seed = 1234)
>>> cars_dl.train(x = predictors,
...               y = response,
...               training_frame = train,
...               validation_frame = valid)
>>> len(listdir(checkpoints_dir))
""",
    export_weights_and_biases="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "cylinders"
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> cars_dl = H2ODeepLearningEstimator(export_weights_and_biases = True,
...                                    seed = 1234)
>>> cars_dl.train(x = predictors,
...               y = response,
...               training_frame = train,
...               validation_frame = valid)
>>> cars_dl.mse()
""",
    fast_mode="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "cylinders"
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> cars_dl = H2ODeepLearningEstimator(fast_mode = False,
...                                    seed = 1234)          
>>> cars_dl.train(x = predictors,
...               y = response,
...               training_frame = train,
...               validation_frame = valid)
>>> cars_dl.mse()
""",
    fold_assignment="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "cylinders"
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> cars_dl = H2ODeepLearningEstimator(fold_assignment = "Random",
...                                    nfolds = 5,
...                                    seed = 1234)
>>> cars_dl.train(x = predictors,
...               y = response,
...               training_frame = train,
...               validation_frame = valid)
>>> cars_dl.mse()
""",
    fold_column="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "cylinders"
>>> fold_numbers = cars.kfold_column(n_folds = 5, seed = 1234)
>>> fold_numbers.set_names(["fold_numbers"])
>>> cars = cars.cbind(fold_numbers)
>>> print(cars['fold_numbers'])
>>> cars_dl = H2ODeepLearningEstimator(seed = 1234)
>>> cars_dl.train(x = predictors,
...               y = response,
...               training_frame = cars,
...               fold_column = "fold_numbers")
>>> cars_dl.mse()
""",
    force_load_balance="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "cylinders"
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> cars_dl = H2ODeepLearningEstimator(force_load_balance = False,
...                                    seed = 1234)
>>> cars_dl.train(x = predictors,
...               y = response,
...               training_frame = train,
...               validation_frame = valid)
>>> cars_dl.mse()
""",
    hidden="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "cylinders"
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> cars_dl = H2ODeepLearningEstimator(hidden = [100,100],
...                                    seed = 1234) 
>>> cars_dl.train(x = predictors,
...               y = response,
...               training_frame = train,
...               validation_frame = valid)
>>> cars_dl.mse()
""",
    hidden_dropout_ratios="""
"""
)
