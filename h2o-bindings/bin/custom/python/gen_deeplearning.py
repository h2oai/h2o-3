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
>>> model.train(x=range(4), y=4, training_frame=fr)
>>> model.logloss()
""",
    activation="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "cylinders"
>>> cars_dl = H2ODeepLearningEstimator(activation="tanh")
>>> cars_dl.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> cars_dl.mse()
""",
    adaptive_rate="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "cylinders"
>>> cars_dl = H2ODeepLearningEstimator(adaptive_rate=True)
>>> cars_dl.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> cars_dl.mse()
""",
    autoencoder="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "cylinders"
>>> cars_dl = H2ODeepLearningEstimator(autoencoder=True)
>>> cars_dl.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> cars_dl.mse()
""",
    average_activation="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "cylinders"
>>> cars_dl = H2ODeepLearningEstimator(average_activation=1.5,
...                                    seed=1234)
>>> cars_dl.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> cars_dl.mse()
""",
    balance_classes="""
>>> covtype = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/covtype/covtype.20k.data")
>>> covtype[54] = covtype[54].asfactor()
>>> predictors = covtype.columns[0:54]
>>> response = 'C55'
>>> train, valid = covtype.split_frame(ratios=[.8], seed=1234)
>>> cov_dl = H2ODeepLearningEstimator(balance_classes=True,
...                                   seed=1234)
>>> cov_dl.train(x=predictors,
...              y=response,
...              training_frame=train,
...              validation_frame=valid)
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
>>> train, valid= airlines.split_frame(ratios=[.8], seed=1234)
>>> encoding = "one_hot_internal"
>>> airlines_dl = H2ODeepLearningEstimator(categorical_encoding=encoding,
...                                        seed=1234)
>>> airlines_dl.train(x=predictors,
...                   y=response,
...                   training_frame=train,
...                   validation_frame=valid)
>>> airlines_dl.mse()
""",
    checkpoint="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_dl = H2ODeepLearningEstimator(activation="tanh",
...                                    autoencoder=True,
...                                    seed=1234,
...                                    model_id="cars_dl")
>>> cars_dl.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> cars_dl.mse()
>>> cars_cont = H2ODeepLearningEstimator(checkpoint=cars_dl,
...                                      seed=1234)
>>> cars_cont.train(x=predictors,
...                 y=response,
...                 training_frame=train,
...                 validation_frame=valid)
>>> cars_cont.mse()
""",
    class_sampling_factors="""
>>> covtype = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/covtype/covtype.20k.data")
>>> covtype[54] = covtype[54].asfactor()
>>> predictors = covtype.columns[0:54]
>>> response = 'C55'
>>> train, valid = covtype.split_frame(ratios=[.8], seed=1234)
>>> sample_factors = [1., 0.5, 1., 1., 1., 1., 1.]
>>> cars_dl = H2ODeepLearningEstimator(balance_classes=True,
...                                    class_sampling_factors=sample_factors,
...                                    seed=1234)
>>> cov_dl.train(x=predictors,
...              y=response,
...              training_frame=train,
...              validation_frame=valid)
>>> cov_dl.mse()
""",
    classification_stop="""
>>> covtype = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/covtype/covtype.20k.data")
>>> covtype[54] = covtype[54].asfactor()
>>> predictors = covtype.columns[0:54]
>>> response = 'C55'
>>> train, valid = covtype.split_frame(ratios=[.8], seed=1234)
>>> cars_dl = H2ODeepLearningEstimator(classification_stop=1.5,
...                                    seed=1234)
>>> cov_dl.train(x=predictors,
...              y=response,
...              training_frame=train,
...              validation_frame=valid)
>>> cov_dl.mse()
""",
    diagnostics="""
>>> covtype = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/covtype/covtype.20k.data")
>>> covtype[54] = covtype[54].asfactor()
>>> predictors = covtype.columns[0:54]
>>> response = 'C55'
>>> train, valid = covtype.split_frame(ratios=[.8], seed=1234)
>>> cars_dl = H2ODeepLearningEstimator(diagnostics=True,
...                                    seed=1234)  
>>> cov_dl.train(x=predictors,
...              y=response,
...              training_frame=train,
...              validation_frame=valid)
>>> cov_dl.mse()
""",
    distribution="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "cylinders"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_dl = H2ODeepLearningEstimator(distribution="poisson",
...                                    seed=1234)
>>> cars_dl.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> cars_dl.mse()
""",
    elastic_averaging="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "cylinders"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_dl = H2ODeepLearningEstimator(elastic_averaging=True,
...                                    seed=1234)
>>> cars_dl.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> cars_dl.mse()
""",
    elastic_averaging_moving_rate="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "cylinders"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_dl = H2ODeepLearningEstimator(elastic_averaging_moving_rate=.8,
...                                    seed=1234)
>>> cars_dl.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> cars_dl.mse()
""",
    elastic_averaging_regularization="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "cylinders"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_dl = H2ODeepLearningEstimator(elastic_averaging_regularization=.008,
...                                    seed=1234)
>>> cars_dl.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> cars_dl.mse()
""",
    epochs="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "cylinders"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_dl = H2ODeepLearningEstimator(epochs=15,
...                                    seed=1234)
>>> cars_dl.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> cars_dl.mse()
""",
    epsilon="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "cylinders"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_dl = H2ODeepLearningEstimator(epsilon=1e-6,
...                                    seed=1234)
>>> cars_dl.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> cars_dl.mse()
""",
    export_checkpoints_dir="""
>>> import tempfile
>>> from os import listdir
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "cylinders"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> checkpoints_dir = tempfile.mkdtemp()
>>> cars_dl = H2ODeepLearningEstimator(export_checkpoints_dir=checkpoints_dir,
...                                    seed=1234)
>>> cars_dl.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> len(listdir(checkpoints_dir))
""",
    export_weights_and_biases="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "cylinders"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_dl = H2ODeepLearningEstimator(export_weights_and_biases=True,
...                                    seed=1234)
>>> cars_dl.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> cars_dl.mse()
""",
    fast_mode="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "cylinders"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_dl = H2ODeepLearningEstimator(fast_mode=False,
...                                    seed=1234)          
>>> cars_dl.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> cars_dl.mse()
""",
    fold_assignment="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "cylinders"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_dl = H2ODeepLearningEstimator(fold_assignment="Random",
...                                    nfolds=5,
...                                    seed=1234)
>>> cars_dl.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> cars_dl.mse()
""",
    fold_column="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "cylinders"
>>> fold_numbers = cars.kfold_column(n_folds=5, seed=1234)
>>> fold_numbers.set_names(["fold_numbers"])
>>> cars = cars.cbind(fold_numbers)
>>> print(cars['fold_numbers'])
>>> cars_dl = H2ODeepLearningEstimator(seed=1234)
>>> cars_dl.train(x=predictors,
...               y=response,
...               training_frame=cars,
...               fold_column="fold_numbers")
>>> cars_dl.mse()
""",
    force_load_balance="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "cylinders"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_dl = H2ODeepLearningEstimator(force_load_balance=False,
...                                    seed=1234)
>>> cars_dl.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> cars_dl.mse()
""",
    hidden="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "cylinders"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_dl = H2ODeepLearningEstimator(hidden=[100,100],
...                                    seed=1234) 
>>> cars_dl.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> cars_dl.mse()
""",
    hidden_dropout_ratios="""
>>> train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/mnist/train.csv.gz")
>>> valid = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/mnist/test.csv.gz")
>>> features = list(range(0,784))
>>> target = 784
>>> train[target] = train[target].asfactor()
>>> valid[target] = valid[target].asfactor()
>>> model = H2ODeepLearningEstimator(epochs=20,
...                                  hidden=[200,200],
...                                  hidden_dropout_ratios=[0.5,0.5],
...                                  seed=1234,
...                                  activation='tanhwithdropout')
>>> model.train(x=features,
...             y=target,
...             training_frame=train,
...             validation_frame=valid)
>>> model.mse()
""",
    huber_alpha="""
>>> insurance = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/insurance.csv")
>>> predictors = insurance.columns[0:4]
>>> response = 'Claims'
>>> insurance['Group'] = insurance['Group'].asfactor()
>>> insurance['Age'] = insurance['Age'].asfactor()
>>> train, valid = insurance.split_frame(ratios=[.8], seed=1234)
>>> insurance_dl = H2ODeepLearningEstimator(distribution="huber",
...                                         huber_alpha=0.9,
...                                         seed=1234)
>>> insurance_dl.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> insurance_dl.mse()
""",
    ignore_const_cols="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> cars["const_1"] = 6
>>> cars["const_2"] = 7
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_dl = H2ODeepLearningEstimator(seed=1234,
...                                    ignore_const_cols=True)
>>> cars_dl.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> cars_dl.auc()
""",
    initial_biases="""
>>> iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris.csv")
>>> dl1 = H2ODeepLearningEstimator(hidden=[10,10],
...                                export_weights_and_biases=True)
>>> dl1.train(x=list(range(4)), y=4, training_frame=iris)
>>> p1 = dl1.model_performance(iris).logloss()
>>> ll1 = dl1.predict(iris)
>>> print(p1)
>>> w1 = dl1.weights(0)
>>> w2 = dl1.weights(1)
>>> w3 = dl1.weights(2)
>>> b1 = dl1.biases(0)
>>> b2 = dl1.biases(1)
>>> b3 = dl1.biases(2)
>>> dl2 = H2ODeepLearningEstimator(hidden=[10,10],
...                                initial_weights=[w1, w2, w3],
...                                initial_biases=[b1, b2, b3],
...                                epochs=0)
>>> dl2.train(x=list(range(4)), y=4, training_frame=iris)
>>> dl2.initial_biases
""",
    initial_weights="""
>>> iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris.csv")
>>> dl1 = H2ODeepLearningEstimator(hidden=[10,10],
...                                export_weights_and_biases=True)
>>> dl1.train(x=list(range(4)), y=4, training_frame=iris)
>>> p1 = dl1.model_performance(iris).logloss()
>>> ll1 = dl1.predict(iris)
>>> print(p1)
>>> w1 = dl1.weights(0)
>>> w2 = dl1.weights(1)
>>> w3 = dl1.weights(2)
>>> b1 = dl1.biases(0)
>>> b2 = dl1.biases(1)
>>> b3 = dl1.biases(2)
>>> dl2 = H2ODeepLearningEstimator(hidden=[10,10],
...                                initial_weights=[w1, w2, w3],
...                                initial_biases=[b1, b2, b3],
...                                epochs=0)
>>> dl2.train(x=list(range(4)), y=4, training_frame=iris)
>>> dl2.initial_weights
""",
    initial_weight_distribution="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_dl = H2ODeepLearningEstimator(initial_weight_distribution="Uniform",
...                                    seed=1234)
>>> cars_dl.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> cars_dl.auc()
""",
    initial_weight_scale="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_dl = H2ODeepLearningEstimator(initial_weight_scale=1.5,
...                                    seed=1234) 
>>> cars_dl.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> cars_dl.auc()
""",
    input_dropout_ratio="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_dl = H2ODeepLearningEstimator(input_dropout_ratio=0.2,
...                                    seed=1234) 
>>> cars_dl.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> cars_dl.auc()
""",
    keep_cross_validation_fold_assignment="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_dl = H2ODeepLearningEstimator(keep_cross_validation_fold_assignment=True,
...                                    seed=1234)
>>> cars_dl.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> print(cars_dl.cross_validation_fold_assignment())
""",
    keep_cross_validation_models="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_dl = H2ODeepLearningEstimator(keep_cross_validation_models=True,
...                                   seed=1234)
>>> cars_dl.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> print(cars_dl.cross_validation_models())
""",
    keep_cross_validation_predictions="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_dl = H2ODeepLearningEstimator(keep_cross_validation_predictions=True,
...                                    seed=1234)
>>> cars_dl.train(x=predictors,
...               y=response,
...               training_frame=train)
>>> print(cars_dl.cross_validation_predictions())
""",
    l1="""
>>> covtype = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/covtype/covtype.20k.data")
>>> covtype[54] = covtype[54].asfactor()
>>> hh_imbalanced = H2ODeepLearningEstimator(l1=1e-5,
...                                          activation="Rectifier",
...                                          loss="CrossEntropy",
...                                          hidden=[200,200],
...                                          epochs=1,
...                                          balance_classes=False,
...                                          reproducible=True,
...                                          seed=1234)
>>> hh_imbalanced.train(x=list(range(54)),y=54, training_frame=covtype)
>>> hh_imbalanced.mse()
""",
    l2="""
>>> covtype = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/covtype/covtype.20k.data")
>>> covtype[54] = covtype[54].asfactor()
>>> hh_imbalanced = H2ODeepLearningEstimator(l2=1e-5,
...                                          activation="Rectifier",
...                                          loss="CrossEntropy",
...                                          hidden=[200,200],
...                                          epochs=1,
...                                          balance_classes=False,
...                                          reproducible=True,
...                                          seed=1234)
>>> hh_imbalanced.train(x=list(range(54)),y=54, training_frame=covtype)
>>> hh_imbalanced.mse()
""",
    loss="""
>>> covtype = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/covtype/covtype.20k.data")
>>> covtype[54] = covtype[54].asfactor()
>>> hh_imbalanced = H2ODeepLearningEstimator(l1=1e-5,
...                                          activation="Rectifier",
...                                          loss="CrossEntropy",
...                                          hidden=[200,200],
...                                          epochs=1,
...                                          balance_classes=False,
...                                          reproducible=True,
...                                          seed=1234)
>>> hh_imbalanced.train(x=list(range(54)),y=54, training_frame=covtype)
>>> hh_imbalanced.mse()
""",
    max_after_balance_size="""
>>> covtype = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/covtype/covtype.20k.data")
>>> covtype[54] = covtype[54].asfactor()
>>> predictors = covtype.columns[0:54]
>>> response = 'C55'
>>> train, valid = covtype.split_frame(ratios=[.8], seed=1234)
>>> max = .85
>>> cov_dl = H2ODeepLearningEstimator(balance_classes=True,
...                                   max_after_balance_size=max,
...                                   seed=1234)
>>> cov_dl.train(x=predictors,
...              y=response,
...              training_frame=train,
...              validation_frame=valid)
>>> cov_dl.logloss()
""",
    max_categorical_features="""
>>> covtype = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/covtype/covtype.20k.data")
>>> covtype[54] = covtype[54].asfactor()
>>> predictors = covtype.columns[0:54]
>>> response = 'C55'
>>> train, valid = covtype.split_frame(ratios=[.8], seed=1234)
>>> cov_dl = H2ODeepLearningEstimator(balance_classes=True,
...                                   max_categorical_features=2147483647,
...                                   seed=1234)
>>> cov_dl.train(x=predictors,
...              y=response,
...              training_frame=train,
...              validation_frame=valid)
>>> cov_dl.logloss()
""",
    max_runtime_secs="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_dl = H2ODeepLearningEstimator(max_runtime_secs=10,
...                                    seed=1234) 
>>> cars_dl.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> cars_dl.auc()
""",
    max_w2="""
>>> covtype = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/covtype/covtype.20k.data")
>>> covtype[54] = covtype[54].asfactor()
>>> predictors = covtype.columns[0:54]
>>> response = 'C55'
>>> train, valid = covtype.split_frame(ratios=[.8], seed=1234)
>>> cov_dl = H2ODeepLearningEstimator(activation="RectifierWithDropout",
...                                   hidden=[10,10],
...                                   epochs=10,
...                                   input_dropout_ratio=0.2,
...                                   l1=1e-5,
...                                   max_w2=10.5,
...                                   stopping_rounds=0)
>>> cov_dl.train(x=predictors,
...              y=response,
...              training_frame=train,
...              validation_frame=valid)
>>> cov_dl.mse()
""",
    mini_batch_size="""
>>> covtype = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/covtype/covtype.20k.data")
>>> covtype[54] = covtype[54].asfactor()
>>> predictors = covtype.columns[0:54]
>>> response = 'C55'
>>> train, valid = covtype.split_frame(ratios=[.8], seed=1234)
>>> cov_dl = H2ODeepLearningEstimator(activation="RectifierWithDropout",
...                                   hidden=[10,10],
...                                   epochs=10,
...                                   input_dropout_ratio=0.2,
...                                   l1=1e-5,
...                                   max_w2=10.5,
...                                   stopping_rounds=0)
...                                   mini_batch_size=35
>>> cov_dl.train(x=predictors,
...              y=response,
...              training_frame=train,
...              validation_frame=valid)
>>> cov_dl.mse()
""",
    missing_values_handling="""
>>> boston = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/BostonHousing.csv")
>>> predictors = boston.columns[:-1]
>>> response = "medv"
>>> boston['chas'] = boston['chas'].asfactor()
>>> boston.insert_missing_values()
>>> train, valid = boston.split_frame(ratios=[.8])
>>> boston_dl = H2ODeepLearningEstimator(missing_values_handling="skip")
>>> boston_dl.train(x=predictors,
...                 y=response,
...                 training_frame=train,
...                 validation_frame=valid)
>>> boston_dl.mse()
""",
    momentum_ramp="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> predictors = ["Year","Month","DayofMonth","DayOfWeek","CRSDepTime",
...               "CRSArrTime","UniqueCarrier","FlightNum"]
>>> response_col = "IsDepDelayed"
>>> airlines_dl = H2ODeepLearningEstimator(hidden=[200,200],
...                                        activation="Rectifier",
...                                        input_dropout_ratio=0.0,
...                                        momentum_start=0.9,
...                                        momentum_stable=0.99,
...                                        momentum_ramp=1e7,
...                                        epochs=100,
...                                        stopping_rounds=4,
...                                        train_samples_per_iteration=30000,
...                                        mini_batch_size=32,
...                                        score_duty_cycle=0.25,
...                                        score_interval=1)
>>> airlines_dl.train(x=predictors,
...                   y=response_col,
...                   training_frame=airlines)
>>> airlines_dl.mse()
""",
    momentum_stable="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> predictors = ["Year","Month","DayofMonth","DayOfWeek","CRSDepTime",
...               "CRSArrTime","UniqueCarrier","FlightNum"]
>>> response_col = "IsDepDelayed"
>>> airlines_dl = H2ODeepLearningEstimator(hidden=[200,200],
...                                        activation="Rectifier",
...                                        input_dropout_ratio=0.0,
...                                        momentum_start=0.9,
...                                        momentum_stable=0.99,
...                                        momentum_ramp=1e7,
...                                        epochs=100,
...                                        stopping_rounds=4,
...                                        train_samples_per_iteration=30000,
...                                        mini_batch_size=32,
...                                        score_duty_cycle=0.25,
...                                        score_interval=1)
>>> airlines_dl.train(x=predictors,
...                   y=response_col,
...                   training_frame=airlines)
>>> airlines_dl.mse()
""",

    momentum_start="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> predictors = ["Year","Month","DayofMonth","DayOfWeek","CRSDepTime",
...               "CRSArrTime","UniqueCarrier","FlightNum"]
>>> response_col = "IsDepDelayed"
>>> airlines_dl = H2ODeepLearningEstimator(hidden=[200,200],
...                                        activation="Rectifier",
...                                        input_dropout_ratio=0.0,
...                                        momentum_start=0.9,
...                                        momentum_stable=0.99,
...                                        momentum_ramp=1e7,
...                                        epochs=100,
...                                        stopping_rounds=4,
...                                        train_samples_per_iteration=30000,
...                                        mini_batch_size=32,
...                                        score_duty_cycle=0.25,
...                                        score_interval=1)
>>> airlines_dl.train(x=predictors,
...                   y=response_col,
...                   training_frame=airlines)
>>> airlines_dl.mse()
""",
    nesterov_accelerated_gradient="""
>>> train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/mnist/train.csv.gz")
>>> test = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/mnist/test.csv.gz")
>>> predictors = list(range(0,784))
>>> resp = 784
>>> train[resp] = train[resp].asfactor()
>>> test[resp] = test[resp].asfactor()
>>> nclasses = train[resp].nlevels()[0]
>>> model = H2ODeepLearningEstimator(activation="RectifierWithDropout",
...                                  adaptive_rate=False,
...                                  rate=0.01,
...                                  rate_decay=0.9,
...                                  rate_annealing=1e-6,
...                                  momentum_start=0.95,
...                                  momentum_ramp=1e5,
...                                  momentum_stable=0.99,
...                                  nesterov_accelerated_gradient=False,
...                                  input_dropout_ratio=0.2,
...                                  train_samples_per_iteration=20000,
...                                  classification_stop=-1,
...                                  l1=1e-5) 
>>> model.train (x=predictors,
...              y=resp,
...              training_frame=train,
...              validation_frame=test)
>>> model.model_performance()
""",
    nfolds="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> cars_dl = H2ODeepLearningEstimator(nfolds=5, seed=1234)
>>> cars_dl.train(x=predictors,
...               y=response,
...               training_frame=cars)
>>> cars_dl.auc()
""",
    offset_column="""
>>> boston = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/BostonHousing.csv")
>>> predictors = boston.columns[:-1]
>>> response = "medv"
>>> boston['chas'] = boston['chas'].asfactor()
>>> boston["offset"] = boston["medv"].log()
>>> train, valid = boston.split_frame(ratios=[.8], seed=1234)
>>> boston_dl = H2ODeepLearningEstimator(offset_column="offset",
...                                      seed=1234)
>>> boston_dl.train(x=predictors,
...                 y=response,
...                 training_frame=train,
...                 validation_frame=valid)
>>> boston_dl.mse()
""",
    overwrite_with_best_model="""
>>> boston = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/BostonHousing.csv")
>>> predictors = boston.columns[:-1]
>>> response = "medv"
>>> boston['chas'] = boston['chas'].asfactor()
>>> boston["offset"] = boston["medv"].log()
>>> train, valid = boston.split_frame(ratios=[.8], seed=1234)
>>> boston_dl = H2ODeepLearningEstimator(overwrite_with_best_model=True,
...                                      seed=1234)
>>> boston_dl.train(x=predictors,
...                 y=response,
...                 training_frame=train,
...                 validation_frame=valid)
>>> boston_dl.mse()
""",
    pretrained_autoencoder="""
>>> from h2o.estimators.deeplearning import H2OAutoEncoderEstimator
>>> resp = 784
>>> nfeatures = 20
>>> train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/mnist/train.csv.gz")
>>> test = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/mnist/test.csv.gz")
>>> train[resp] = train[resp].asfactor()
>>> test[resp] = test[resp].asfactor()
>>> sid = train[0].runif(0)
>>> train_unsupervised = train[sid>=0.5]
>>> train_unsupervised.pop(resp)
>>> train_supervised = train[sid<0.5]
>>> ae_model = H2OAutoEncoderEstimator(activation="Tanh",
...                                    hidden=[nfeatures],
...                                    model_id="ae_model",
...                                    epochs=1,
...                                    ignore_const_cols=False,
...                                    reproducible=True,
...                                    seed=1234)
>>> ae_model.train(list(range(resp)), training_frame=train_unsupervised)
>>> ae_model.mse()
>>> pretrained_model = H2ODeepLearningEstimator(activation="Tanh",
...                                             hidden=[nfeatures],
...                                             epochs=1,
...                                             reproducible = True,
...                                             seed=1234,
...                                             ignore_const_cols=False,
...                                             pretrained_autoencoder="ae_model")
>>> pretrained_model.train(list(range(resp)), resp,
...                        training_frame=train_supervised,
...                        validation_frame=test)
>>> pretrained_model.mse()
""",
    quantile_alpha="""
>>> boston = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/BostonHousing.csv")
>>> predictors = boston.columns[:-1]
>>> response = "medv"
>>> boston['chas'] = boston['chas'].asfactor()
>>> train, valid = boston.split_frame(ratios=[.8], seed=1234)
>>> boston_dl = H2ODeepLearningEstimator(distribution="quantile",
...                                      quantile_alpha=.8,
...                                      seed=1234)
>>> boston_dl.train(x=predictors,
...                 y=response,
...                 training_frame=train,
...                 validation_frame=valid)
>>> boston_dl.mse()
""",
    quiet_mode="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> titanic['survived'] = titanic['survived'].asfactor()
>>> predictors = titanic.columns
>>> del predictors[1:3]
>>> response = 'survived'
>>> train, valid = titanic.split_frame(ratios=[.8], seed=1234)
>>> titanic_dl = H2ODeepLearningEstimator(quiet_mode=True,
...                                       seed=1234)
>>> titanic_dl.train(x=predictors,
...                  y=response,
...                  training_frame=train,
...                  validation_frame=valid)
>>> titanic_dl.mse()
""",
    rate="""
>>> train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/mnist/train.csv.gz")
>>> test = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/mnist/test.csv.gz")
>>> predictors = list(range(0,784))
>>> resp = 784
>>> train[resp] = train[resp].asfactor()
>>> test[resp] = test[resp].asfactor()
>>> nclasses = train[resp].nlevels()[0]
>>> model = H2ODeepLearningEstimator(activation="RectifierWithDropout",
...                                  adaptive_rate=False,
...                                  rate=0.01,
...                                  rate_decay=0.9,
...                                  rate_annealing=1e-6,
...                                  momentum_start=0.95,
...                                  momentum_ramp=1e5,
...                                  momentum_stable=0.99,
...                                  nesterov_accelerated_gradient=False,
...                                  input_dropout_ratio=0.2,
...                                  train_samples_per_iteration=20000,
...                                  classification_stop=-1,
...                                  l1=1e-5)
>>> model.train (x=predictors,y=resp, training_frame=train, validation_frame=test)
>>> model.model_performance(valid=True)
""",
    rate_annealing="""
>>> train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/mnist/train.csv.gz")
>>> test = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/mnist/test.csv.gz")
>>> predictors = list(range(0,784))
>>> resp = 784
>>> train[resp] = train[resp].asfactor()
>>> test[resp] = test[resp].asfactor()
>>> nclasses = train[resp].nlevels()[0]
>>> model = H2ODeepLearningEstimator(activation="RectifierWithDropout",
...                                  adaptive_rate=False,
...                                  rate=0.01,
...                                  rate_decay=0.9,
...                                  rate_annealing=1e-6,
...                                  momentum_start=0.95,
...                                  momentum_ramp=1e5,
...                                  momentum_stable=0.99,
...                                  nesterov_accelerated_gradient=False,
...                                  input_dropout_ratio=0.2,
...                                  train_samples_per_iteration=20000,
...                                  classification_stop=-1,
...                                  l1=1e-5)
>>> model.train (x=predictors,
...              y=resp,
...              training_frame=train,
...              validation_frame=test)
>>> model.mse()
""",
    rate_decay="""
>>> train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/mnist/train.csv.gz")
>>> test = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/mnist/test.csv.gz")
>>> predictors = list(range(0,784))
>>> resp = 784
>>> train[resp] = train[resp].asfactor()
>>> test[resp] = test[resp].asfactor()
>>> nclasses = train[resp].nlevels()[0]
>>> model = H2ODeepLearningEstimator(activation="RectifierWithDropout",
...                                  adaptive_rate=False,
...                                  rate=0.01,
...                                  rate_decay=0.9,
...                                  rate_annealing=1e-6,
...                                  momentum_start=0.95,
...                                  momentum_ramp=1e5,
...                                  momentum_stable=0.99,
...                                  nesterov_accelerated_gradient=False,
...                                  input_dropout_ratio=0.2,
...                                  train_samples_per_iteration=20000,
...                                  classification_stop=-1,
...                                  l1=1e-5)
>>> model.train (x=predictors,
...              y=resp,
...              training_frame=train,
...              validation_frame=test)
>>> model.model_performance()
""",
    regression_stop="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> airlines["Year"]= airlines["Year"].asfactor()
>>> airlines["Month"]= airlines["Month"].asfactor()
>>> airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
>>> airlines["Cancelled"] = airlines["Cancelled"].asfactor()
>>> airlines['FlightNum'] = airlines['FlightNum'].asfactor()
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> response = "IsDepDelayed"
>>> train, valid= airlines.split_frame(ratios=[.8], seed=1234)
>>> airlines_dl = H2ODeepLearningEstimator(regression_stop=1e-6,
...                                        seed=1234)
>>> airlines_dl.train(x=predictors,
...                   y=response,
...                   training_frame=train,
...                   validation_frame=valid)
>>> airlines_dl.auc()
""",
    replicate_training_data="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> airlines["Year"]= airlines["Year"].asfactor()
>>> airlines["Month"]= airlines["Month"].asfactor()
>>> airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
>>> airlines["Cancelled"] = airlines["Cancelled"].asfactor()
>>> airlines['FlightNum'] = airlines['FlightNum'].asfactor()
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> response = "IsDepDelayed"
>>> airlines_dl = H2ODeepLearningEstimator(replicate_training_data=False)
>>> airlines_dl.train(x=predictors,
...                   y=response,
...                   training_frame=airlines) 
>>> airlines_dl.auc()
""",
    reproducible="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> airlines["Year"]= airlines["Year"].asfactor()
>>> airlines["Month"]= airlines["Month"].asfactor()
>>> airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
>>> airlines["Cancelled"] = airlines["Cancelled"].asfactor()
>>> airlines['FlightNum'] = airlines['FlightNum'].asfactor()
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> response = "IsDepDelayed"
>>> train, valid= airlines.split_frame(ratios=[.8], seed=1234)
>>> airlines_dl = H2ODeepLearningEstimator(reproducible=True)
>>> airlines_dl.train(x=predictors,
...                   y=response,
...                   training_frame=train,
...                   validation_frame=valid)
>>> airlines_dl.auc()
""",
    rho="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> cars_dl = H2ODeepLearningEstimator(rho=0.9,
...                                    seed=1234)
>>> cars_dl.train(x=predictors,
...               y=response,
...               training_frame=cars)
>>> cars_dl.auc()
""",
    score_duty_cycle="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> cars_dl = H2ODeepLearningEstimator(score_duty_cycle=0.2,
...                                    seed=1234)
>>> cars_dl.train(x=predictors,
...               y=response,
...               training_frame=cars)
>>> cars_dl.auc()
""",
    score_each_iteration="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> cars_dl = H2ODeepLearningEstimator(score_each_iteration=True,
...                                    seed=1234) 
>>> cars_dl.train(x=predictors,
...               y=response,
...               training_frame=cars)
>>> cars_dl.auc()
""",
    score_interval="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> cars_dl = H2ODeepLearningEstimator(score_interval=3,
...                                    seed=1234) 
>>> cars_dl.train(x=predictors,
...               y=response,
...               training_frame=cars)
>>> cars_dl.auc()
""",
    score_training_samples="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> cars_dl = H2ODeepLearningEstimator(score_training_samples=10000,
...                                    seed=1234)
>>> cars_dl.train(x=predictors,
...               y=response,
...               training_frame=cars)
>>> cars_dl.auc()
""",
    score_validation_samples="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_dl = H2ODeepLearningEstimator(score_validation_samples=3,
...                                    seed=1234)
>>> cars_dl.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> cars_dl.auc()
""",
    score_validation_sampling="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_dl = H2ODeepLearningEstimator(score_validation_sampling="uniform",
...                                    seed=1234)
>>> cars_dl.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> cars_dl.auc()
""",
    seed="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_dl = H2ODeepLearningEstimator(seed=1234)
>>> cars_dl.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> cars_dl.auc()
""",
    shuffle_training_data="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_dl = H2ODeepLearningEstimator(shuffle_training_data=True,
...                                    seed=1234)
>>> cars_dl.train(x=predictors,
...               y=response,
...               training_frame=cars)
>>> cars_dl.auc()
""",
    single_node_mode="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_dl = H2ODeepLearningEstimator(single_node_mode=True,
...                                    seed=1234) 
>>> cars_dl.train(x=predictors,
...               y=response,
...               training_frame=cars)
>>> cars_dl.auc()
""",
    sparse="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_dl = H2ODeepLearningEstimator(sparse=True,
...                                    seed=1234)
>>> cars_dl.train(x=predictors,
...               y=response,
...               training_frame=cars)
>>> cars_dl.auc()
""",
    sparsity_beta="""
>>> from h2o.estimators import H2OAutoEncoderEstimator
>>> resp = 784
>>> nfeatures = 20
>>> train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/mnist/train.csv.gz")
>>> test = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/mnist/test.csv.gz")
>>> train[resp] = train[resp].asfactor()
>>> test[resp] = test[resp].asfactor()
>>> sid = train[0].runif(0)
>>> train_unsupervised = train[sid>=0.5]
>>> train_unsupervised.pop(resp)
>>> ae_model = H2OAutoEncoderEstimator(activation="Tanh",
...                                    hidden=[nfeatures],
...                                    epochs=1,
...                                    ignore_const_cols=False,
...                                    reproducible=True,
...                                    sparsity_beta=0.5,
...                                    seed=1234)
>>> ae_model.train(list(range(resp)),
...                training_frame=train_unsupervised)
>>> ae_model.mse()
""",
    standardize="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> cars_dl = H2ODeepLearningEstimator(standardize=True,
...                                    seed=1234)
>>> cars_dl.train(x=predictors,
...               y=response,
...               training_frame=cars)
>>> cars_dl.auc()
""",
    stopping_metric="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> airlines["Year"]= airlines["Year"].asfactor()
>>> airlines["Month"]= airlines["Month"].asfactor()
>>> airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
>>> airlines["Cancelled"] = airlines["Cancelled"].asfactor()
>>> airlines['FlightNum'] = airlines['FlightNum'].asfactor()
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> response = "IsDepDelayed"
>>> train, valid= airlines.split_frame(ratios=[.8], seed=1234)
>>> airlines_dl = H2ODeepLearningEstimator(stopping_metric="auc",
...                                        stopping_rounds=3,
...                                        stopping_tolerance=1e-2,
...                                        seed=1234)
>>> airlines_dl.train(x=predictors,
...                   y=response,
...                   training_frame=train,
...                   validation_frame=valid)
>>> airlines_dl.auc()
""",
    stopping_rounds="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> airlines["Year"]= airlines["Year"].asfactor()
>>> airlines["Month"]= airlines["Month"].asfactor()
>>> airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
>>> airlines["Cancelled"] = airlines["Cancelled"].asfactor()
>>> airlines['FlightNum'] = airlines['FlightNum'].asfactor()
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> response = "IsDepDelayed"
>>> train, valid= airlines.split_frame(ratios=[.8], seed=1234)
>>> airlines_dl = H2ODeepLearningEstimator(stopping_metric="auc",
...                                        stopping_rounds=3,
...                                        stopping_tolerance=1e-2,
...                                        seed=1234)
>>> airlines_dl.train(x=predictors,
...                   y=response,
...                   training_frame=train,
...                   validation_frame=valid)
>>> airlines_dl.auc()
""",
    stopping_tolerance="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> airlines["Year"]= airlines["Year"].asfactor()
>>> airlines["Month"]= airlines["Month"].asfactor()
>>> airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
>>> airlines["Cancelled"] = airlines["Cancelled"].asfactor()
>>> airlines['FlightNum'] = airlines['FlightNum'].asfactor()
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> response = "IsDepDelayed"
>>> train, valid= airlines.split_frame(ratios=[.8], seed=1234)
>>> airlines_dl = H2ODeepLearningEstimator(stopping_metric="auc",
...                                        stopping_rounds=3,
...                                        stopping_tolerance=1e-2,
...                                        seed=1234)
>>> airlines_dl.train(x=predictors,
...                   y=response,
...                   training_frame=train,
...                   validation_frame=valid)
>>> airlines_dl.auc()
""",
    target_ratio_comm_to_comp="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> airlines["Year"]= airlines["Year"].asfactor()
>>> airlines["Month"]= airlines["Month"].asfactor()
>>> airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
>>> airlines["Cancelled"] = airlines["Cancelled"].asfactor()
>>> airlines['FlightNum'] = airlines['FlightNum'].asfactor()
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> response = "IsDepDelayed"
>>> train, valid= airlines.split_frame(ratios=[.8], seed=1234)
>>> airlines_dl = H2ODeepLearningEstimator(target_ratio_comm_to_comp=0.05,
...                                        seed=1234)
>>> airlines_dl.train(x=predictors,
...                   y=response,
...                   training_frame=train,
...                   validation_frame=valid)
>>> airlines_dl.auc()
""",
    train_samples_per_iteration="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> airlines["Year"]= airlines["Year"].asfactor()
>>> airlines["Month"]= airlines["Month"].asfactor()
>>> airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
>>> airlines["Cancelled"] = airlines["Cancelled"].asfactor()
>>> airlines['FlightNum'] = airlines['FlightNum'].asfactor()
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> response = "IsDepDelayed"
>>> train, valid= airlines.split_frame(ratios=[.8], seed=1234)
>>> airlines_dl = H2ODeepLearningEstimator(train_samples_per_iteration=-1,
...                                        epochs=1,
...                                        seed=1234)
>>> airlines_dl.train(x=predictors,
...                   y=response,
...                   training_frame=train,
...                   validation_frame=valid)
>>> airlines_dl.auc()
""",
    training_frame="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> airlines["Year"]= airlines["Year"].asfactor()
>>> airlines["Month"]= airlines["Month"].asfactor()
>>> airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
>>> airlines["Cancelled"] = airlines["Cancelled"].asfactor()
>>> airlines['FlightNum'] = airlines['FlightNum'].asfactor()
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> response = "IsDepDelayed"
>>> train, valid= airlines.split_frame(ratios=[.8], seed=1234)
>>> airlines_dl = H2ODeepLearningEstimator()
>>> airlines_dl.train(x=predictors,
...                   y=response,
...                   training_frame=train,
...                   validation_frame=valid)
>>> airlines_dl.auc()
""",
    tweedie_power="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> airlines["Year"]= airlines["Year"].asfactor()
>>> airlines["Month"]= airlines["Month"].asfactor()
>>> airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
>>> airlines["Cancelled"] = airlines["Cancelled"].asfactor()
>>> airlines['FlightNum'] = airlines['FlightNum'].asfactor()
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> response = "IsDepDelayed"
>>> train, valid= airlines.split_frame(ratios=[.8], seed=1234)
>>> airlines_dl = H2ODeepLearningEstimator(tweedie_power=1.5,
...                                        seed=1234) 
>>> airlines_dl.train(x=predictors,
...                   y=response,
...                   training_frame=train,
...                   validation_frame=valid)
>>> airlines_dl.auc()
""",
    use_all_factor_levels="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> airlines["Year"]= airlines["Year"].asfactor()
>>> airlines["Month"]= airlines["Month"].asfactor()
>>> airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
>>> airlines["Cancelled"] = airlines["Cancelled"].asfactor()
>>> airlines['FlightNum'] = airlines['FlightNum'].asfactor()
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> response = "IsDepDelayed"
>>> train, valid= airlines.split_frame(ratios=[.8], seed=1234)
>>> airlines_dl = H2ODeepLearningEstimator(use_all_factor_levels=True,
...                                        seed=1234)
>>> airlines_dl.train(x=predictors,
...                   y=response,
...                   training_frame=train,
...                   validation_frame=valid)
>>> airlines_dl.mse()
""",
    validation_frame="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_dl = H2ODeepLearningEstimator(standardize=True,
...                                    seed=1234)
>>> cars_dl.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> cars_dl.auc()
""",
    variable_importances="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> airlines["Year"]= airlines["Year"].asfactor()
>>> airlines["Month"]= airlines["Month"].asfactor()
>>> airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
>>> airlines["Cancelled"] = airlines["Cancelled"].asfactor()
>>> airlines['FlightNum'] = airlines['FlightNum'].asfactor()
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> response = "IsDepDelayed"
>>> train, valid= airlines.split_frame(ratios=[.8], seed=1234)
>>> airlines_dl = H2ODeepLearningEstimator(variable_importances=True,
...                                        seed=1234)
>>> airlines_dl.train(x=predictors,
...                   y=response,
...                   training_frame=train,
...                   validation_frame=valid)
>>> airlines_dl.mse()
""",
    weights_column="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_dl = H2ODeepLearningEstimator(seed=1234)
>>> cars_dl.train(x=predictors,
...               y=response,
...               training_frame=train,
...               validation_frame=valid)
>>> cars_dl.auc()
"""
)
