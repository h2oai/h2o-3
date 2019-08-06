class_doc = """
Builds gradient boosted trees on a parsed data set, for regression or classification.
The default distribution function will guess the model type based on the response column type.
Otherwise, the response column must be an enum for "bernoulli" or "multinomial", and numeric
for all other distributions.
"""

property_training_frame_examples = """
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
>>> cars_gbm.train(x = predictors,
...                y = response,
...                training_frame = train,
...                validation_frame = valid)
>>> cars_gbm.auc(valid=True)
"""

property_validation_frame_examples = """
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
>>> cars_gbm.train(x = predictors,
...                y = response,
...                training_frame = train,
...                validation_frame = valid)
>>> cars_gbm.auc(valid=True)
"""

properties_examples = dict(
    training_frame="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
>>> cars_gbm.train(x = predictors,
...                y = response,
...                training_frame = train,
...                validation_frame = valid)
>>> cars_gbm.auc(valid=True)
""",
    validation_frame="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> cars_gbm = H2OGradientBoostingEstimator(seed = 1234)
>>> cars_gbm.train(x = predictors,
...                y = response,
...                training_frame = train,
...                validation_frame = valid)
>>> cars_gbm.auc(valid=True)
""",
)
