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
>>> uplift_model = H2OUpliftRandomForestEstimator(ntrees=10, max_depth=5, treatment_column=treatment_column, uplift_metric="KL", min_rows=10, seed=1234, auuc_type="qini", auuc_nbins=-1)
>>> uplift_model.train(x=predictors, y=response, training_frame=train, validation_frame=valid)
>>> perf = uplift_model.model_performance()
>>> pred = uplift_model.predict(valid)
>>> perf.plot_uplift(metric="gain", plot=True)
>>> perf.plot_uplift(metric="gain", plot=True, normalize=True)
>>> print(perf.auuc())
>>> print(perf.auuc(metric="lift"))
>>> print(perf.auuc_normalized(metric="lift"))
>>> print(perf.auuc_table())
>>> print(perf.thresholds_and_metric_scores())
>>> print(perf.qini())
>>> print(perf.aecu())
>>> print(perf.aecu_table())
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
>>> uplift_model = H2OUpliftRandomForestEstimator(ntrees=10, max_depth=5, treatment_column=treatment_column, uplift_metric="KL", min_rows=10, seed=1234, auuc_type="qini")
>>> uplift_model.train(x=predictors, y=response, training_frame=train, validation_frame=valid)
>>> perf = uplift_model.model_performance()
>>> pred = uplift_model.predict(valid)
>>> perf.plot_uplift(metric="gain", plot=True)
>>> perf.plot_uplift(metric="gain", plot=True, normalize=True)
>>> print(perf.auuc())
>>> print(perf.auuc(metric="lift"))
>>> print(perf.auuc_normalized(metric="lift"))
>>> print(perf.auuc_table())
>>> print(perf.thresholds_and_metric_scores())
>>> print(perf.qini())
>>> print(perf.aecu())
>>> print(perf.aecu_table())
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
>>> uplift_model = H2OUpliftRandomForestEstimator(ntrees=10, max_depth=5, uplift_metric="KL", min_rows=10, seed=1234, auuc_type="qini", treatment_column="treatment")
>>> uplift_model.train(x=predictors, y=response, training_frame=train, validation_frame=valid)
>>> perf = uplift_model.model_performance()
>>> pred = uplift_model.predict(valid)
>>> perf.plot_uplift(metric="gain", plot=True)
>>> perf.plot_uplift(metric="gain", plot=True, normalize=True)
>>> print(perf.auuc())
>>> print(perf.auuc(metric="lift"))
>>> print(perf.auuc_normalized(metric="lift"))
>>> print(perf.auuc_table())
>>> print(perf.thresholds_and_metric_scores())
>>> print(perf.qini())
>>> print(perf.aecu())
>>> print(perf.aecu_table())
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
>>> uplift_model = H2OUpliftRandomForestEstimator(ntrees=10, max_depth=5, min_rows=10, seed=1234, auuc_type="qini", treatment_column="treatment", uplift_metric="auto")
>>> uplift_model.train(x=predictors, y=response, training_frame=train, validation_frame=valid)
>>> perf = uplift_model.model_performance()
>>> pred = uplift_model.predict(valid)
>>> perf.plot_uplift(metric="gain", plot=True)
>>> perf.plot_uplift(metric="gain", plot=True, normalize=True)
>>> print(perf.auuc())
>>> print(perf.auuc(metric="lift"))
>>> print(perf.auuc_normalized(metric="lift"))
>>> print(perf.auuc_table())
>>> print(perf.thresholds_and_metric_scores())
>>> print(perf.qini())
>>> print(perf.aecu())
>>> print(perf.aecu_table())
"""
)