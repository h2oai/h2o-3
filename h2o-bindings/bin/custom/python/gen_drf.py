deprecated = ['offset_column', 'distribution']

examples = dict(
    training_frame="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios = [.8],
...                                 seed = 1234)
>>> cars_drf = H2ORandomForestEstimator(seed = 1234)
>>> cars_drf.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> cars_drf.auc(valid=True)
"""
)
