options = dict(
    verbose=True,
    model_extensions=[
        'h2o.model.extensions.VariableImportance',
    ],
)


def update_param(name, param):
    if name == 'stopping_metric':
        param['values'] = ['AUTO', 'AUUC', 'ATE', 'ATT', 'ATC', 'qini']
        return param
    if name == 'distribution':
        param['values'] = ['AUTO', 'bernoulli']
        return param
    return None  # param untouched


doc = dict(
    __class__="""
Build a Uplift Random Forest model

Builds a Uplift Random Forest model on an H2OFrame.
"""
)

examples = dict(
    auuc_nbins="""
>>> import h2o
>>> from h2o.estimators import H2OUpliftRandomForestEstimator
>>> h2o.init()
>>> data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/uplift/criteo_uplift_13k.csv")
>>> predictors = ["f1", "f2", "f3", "f4", "f5", "f6","f7", "f8"]
>>> response = "conversion"
>>> data[response] = data[response].asfactor()
>>> treatment_column = "treatment"
>>> data[treatment_column] = data[treatment_column].asfactor()
>>> train, valid = data.split_frame(ratios=[.8], seed=1234)
>>> uplift_model = H2OUpliftRandomForestEstimator(ntrees=10,
...                                               max_depth=5,
...                                               treatment_column=treatment_column,
...                                               uplift_metric="KL",
...                                               min_rows=10,
...                                               seed=1234,
...                                               auuc_type="qini",
...                                               auuc_nbins=100)
>>> uplift_model.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> uplift_model.model_performance()
""",
    auuc_type="""
>>> import h2o
>>> from h2o.estimators import H2OUpliftRandomForestEstimator
>>> h2o.init()
>>> data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/uplift/criteo_uplift_13k.csv")
>>> predictors = ["f1", "f2", "f3", "f4", "f5", "f6","f7", "f8"]
>>> response = "conversion"
>>> data[response] = data[response].asfactor()
>>> treatment_column = "treatment"
>>> data[treatment_column] = data[treatment_column].asfactor()
>>> train, valid = data.split_frame(ratios=[.8], seed=1234)
>>> uplift_model = H2OUpliftRandomForestEstimator(ntrees=10,
...                                               max_depth=5,
...                                               treatment_column=treatment_column,
...                                               uplift_metric="KL",
...                                               min_rows=10,
...                                               seed=1234,
...                                               auuc_type="gain")
>>> uplift_model.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> uplift_model.model_performance()
""",
    treatment_column="""
>>> import h2o
>>> from h2o.estimators import H2OUpliftRandomForestEstimator
>>> h2o.init()
>>> data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/uplift/criteo_uplift_13k.csv")
>>> predictors = ["f1", "f2", "f3", "f4", "f5", "f6","f7", "f8"]
>>> response = "conversion"
>>> data[response] = data[response].asfactor()
>>> treatment_column = "treatment"
>>> data[treatment_column] = data[treatment_column].asfactor()
>>> train, valid = data.split_frame(ratios=[.8], seed=1234)
>>> uplift_model = H2OUpliftRandomForestEstimator(ntrees=10,
...                                               max_depth=5,
...                                               uplift_metric="KL",
...                                               min_rows=10,
...                                               seed=1234,
...                                               auuc_type="qini",
...                                               treatment_column=treatment_column)
>>> uplift_model.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> uplift_model.model_performance()
""",
    uplift_metric="""
>>> import h2o
>>> from h2o.estimators import H2OUpliftRandomForestEstimator
>>> h2o.init()
>>> data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/uplift/criteo_uplift_13k.csv")
>>> predictors = ["f1", "f2", "f3", "f4", "f5", "f6","f7", "f8"]
>>> response = "conversion"
>>> data[response] = data[response].asfactor()
>>> treatment_column = "treatment"
>>> data[treatment_column] = data[treatment_column].asfactor()
>>> train, valid = data.split_frame(ratios=[.8], seed=1234)
>>> uplift_model = H2OUpliftRandomForestEstimator(ntrees=10,
...                                               max_depth=5,
...                                               min_rows=10,
...                                               seed=1234,
...                                               auuc_type="qini",
...                                               treatment_column=treatment_column,
...                                               uplift_metric="euclidean")
>>> uplift_model.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> uplift_model.model_performance()
"""
)
