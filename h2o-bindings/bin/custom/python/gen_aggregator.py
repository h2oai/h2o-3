rest_api_version = 99


def class_extensions():
    @property
    def aggregated_frame(self):
        if (self._model_json is not None
                and self._model_json.get("output", {}).get("output_frame", {}).get("name") is not None):
            out_frame_name = self._model_json["output"]["output_frame"]["name"]
            return H2OFrame.get_frame(out_frame_name)


extensions = dict(
    __class__=class_extensions
)

examples = dict(
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
>>> encoding = "one_hot_explicit"
>>> airlines_ag = H2OAggregatorEstimator(categorical_encoding = encoding)
>>> airlines_ag.train(x = predictors,
...                   y = response,
...                   training_frame = train,
...                   validation_frame = valid)
>>> airlines_ag.aggregated_frame
""",
    export_checkpoints_dir="""
>>> import tempfile
>>> from os import listdir
>>> airlines = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip", destination_frame="air.hex")
>>> predictors = ["DayofMonth", "DayOfWeek"]
>>> response = "IsDepDelayed"
>>> checkpoints_dir = tempfile.mkdtemp()
>>> airlines_ag = H2OAggregatorEstimator()
>>> airlines_ag.train(x = predictors,
...                   y = response,
...                   training_frame = airlines)
>>> len(listdir(checkpoints_dir))
""",
    ignore_const_cols="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> cars["const_1"] = 6
>>> cars["const_2"] = 7
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> cars_ag = H2OAggregatorEstimator(ignore_const_cols = True)
>>> cars_ag.train(x = predictors,
...               y = response,
...               training_frame = train,
...               validation_frame = valid)
>>> cars_ag.aggregated_frame
""",
    ignored_columns="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> airlines["Year"]= airlines["Year"].asfactor()
>>> airlines["Month"]= airlines["Month"].asfactor()
>>> airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
>>> airlines["Cancelled"] = airlines["Cancelled"].asfactor()
>>> airlines['FlightNum'] = airlines['FlightNum'].asfactor()
>>> predictors = airlines.columns[:9]
>>> response = "IsDepDelayed"
>>> train, valid= airlines.split_frame(ratios = [.8], seed = 1234)
>>> col_list = ['DepTime','CRSDepTime','ArrTime','CRSArrTime']
>>> airlines_ag = H2OAggregatorEstimator(ignored_columns = col_list)
>>> airlines_ag.train(y = response,
...                   training_frame = train,
...                   validation_frame = valid)
>>> airlines_ag.aggregated_frame
""",
    num_iteration_without_new_exemplar="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> airlines["Year"]= airlines["Year"].asfactor()
>>> airlines["Month"]= airlines["Month"].asfactor()
>>> airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
>>> airlines["Cancelled"] = airlines["Cancelled"].asfactor()
>>> airlines['FlightNum'] = airlines['FlightNum'].asfactor()
>>> predictors = airlines.columns[:9]
>>> response = "IsDepDelayed"
>>> train, valid= airlines.split_frame(ratios = [.8], seed = 1234)
>>> col_list = ['DepTime','CRSDepTime','ArrTime','CRSArrTime']
>>> airlines_ag = H2OAggregatorEstimator(num_iteration_without_new_exemplar = 500)
>>> airlines_ag.train(y = response,
...                   training_frame = train,
...                   validation_frame = valid)
>>> airlines_ag.aggregated_frame
""",
    rel_tol_num_exemplars="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> cars_ag = H2OAggregatorEstimator(rel_tol_num_exemplars = .7)
>>> cars_ag.train(x = predictors,
...               y = response,
...               training_frame = train,
...               validation_frame = valid)
>>> cars_ag.aggregated_frame
""",
    response_column="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response_column = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> cars_ag = H2OAggregatorEstimator()
>>> cars_ag.train(x = predictors,
...               y = response_column,
...               training_frame = train,
...               validation_frame = valid)
>>> cars_ag.aggregated_frame
""",
    save_mapping_frame="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response_column = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> cars_ag = H2OAggregatorEstimator(save_mapping_frame = True)
>>> cars_ag.train(x = predictors,
...               y = response_column,
...               training_frame = train,
...               validation_frame = valid)
>>> cars_ag.aggregated_frame
>>> cars_ag.save_mapping_frame
""",
    target_num_exemplars="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response_column = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> cars_ag = H2OAggregatorEstimator(target_num_exemplars = 5000)
>>> cars_ag.train(x = predictors,
...               y = response_column,
...               training_frame = train,
...               validation_frame = valid)
>>> cars_ag.aggregated_frame
""",
    training_frame="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response_column = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> cars_ag = H2OAggregatorEstimator()
>>> cars_ag.train(x = predictors,
...               y = response_column,
...               training_frame = train,
...               validation_frame = valid)
>>> cars_ag.aggregated_frame
""",
    transform="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response_column = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> cars_ag = H2OAggregatorEstimator(transform = "demean")
>>> cars_ag.train(x = predictors,
...               y = response_column,
...               training_frame = train,
...               validation_frame = valid)
>>> cars_ag.aggregated_frame
"""
)
    
