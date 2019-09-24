def update_param(name, param):
    if name == 'distribution':
        param['values'].remove('custom')
        return param
    return None  # param untouched


def class_extensions():
    @property
    def Lambda(self):
        """DEPRECATED. Use ``self.lambda_`` instead"""
        return self._parms["lambda"] if "lambda" in self._parms else None

    @Lambda.setter
    def Lambda(self, value):
        self._parms["lambda"] = value

    @staticmethod
    def getGLMRegularizationPath(model):
        """
        Extract full regularization path explored during lambda search from glm model.

        :param model: source lambda search model
        """
        x = h2o.api("GET /3/GetGLMRegPath", data={"model": model._model_json["model_id"]["name"]})
        ns = x.pop("coefficient_names")
        res = {
            "lambdas": x["lambdas"],
            "explained_deviance_train": x["explained_deviance_train"],
            "explained_deviance_valid": x["explained_deviance_valid"],
            "coefficients": [dict(zip(ns, y)) for y in x["coefficients"]],
        }
        if "coefficients_std" in x:
            res["coefficients_std"] = [dict(zip(ns, y)) for y in x["coefficients_std"]]
        return res

    @staticmethod
    def makeGLMModel(model, coefs, threshold=.5):
        """
        Create a custom GLM model using the given coefficients.

        Needs to be passed source model trained on the dataset to extract the dataset information from.

        :param model: source model, used for extracting dataset information
        :param coefs: dictionary containing model coefficients
        :param threshold: (optional, only for binomial) decision threshold used for classification
        """
        model_json = h2o.api(
            "POST /3/MakeGLMModel",
            data={"model": model._model_json["model_id"]["name"],
                  "names": list(coefs.keys()),
                  "beta": list(coefs.values()),
                  "threshold": threshold}
        )
        m = H2OGeneralizedLinearEstimator()
        m._resolve_model(model_json["model_id"]["name"], model_json)
        return m


extensions = dict(
    __imports__="""import h2o""",
    __class__=class_extensions,
    __init__validation="""
if "Lambda" in kwargs: kwargs["lambda_"] = kwargs.pop("Lambda")
"""
)

overrides = dict(
    alpha=dict(
        setter="""
# For `alpha` and `lambda` the server reports type float[], while in practice simple floats are also ok
assert_is_type({pname}, None, numeric, [numeric])
self._parms["{sname}"] = {pname}
"""
    ),
    lambda_=dict(
        setter="""
assert_is_type({pname}, None, numeric, [numeric])
self._parms["{sname}"] = {pname}
"""
    ),
)

doc = dict(
    __class__="""
Fits a generalized linear model, specified by a response variable, a set of predictors, and a
description of the error distribution.

A subclass of :class:`ModelBase` is returned. The specific subclass depends on the machine learning task
at hand (if it's binomial classification, then an H2OBinomialModel is returned, if it's regression then a
H2ORegressionModel is returned). The default print-out of the models is shown, but further GLM-specific
information can be queried out of the object. Upon completion of the GLM, the resulting object has
coefficients, normalized coefficients, residual/null deviance, aic, and a host of model metrics including
MSE, AUC (for logistic regression), degrees of freedom, and confusion matrices.
"""
)

examples = dict(
    training_frame="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios = [.8],
...                                 seed = 1234)
>>> cars_glm = H2OGeneralizedLinearEstimator(seed = 1234,
...                                          family = 'binomial')
>>> cars_glm.train(x = predictors,
...                y = response,
...                training_frame = train,
...                validation_frame = valid)
>>> cars_glm.auc(train=True)
""",
    validation_frame="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> cars_glm = H2OGeneralizedLinearEstimator(seed = 1234,
...                                          family = 'binomial')
>>> cars_glm.train(x = predictors,
...                y = response,
...                training_frame = train,
...                validation_frame = valid)
>>> cars_glm.auc(valid=True)
""",
    nfolds="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> folds = 5
>>> cars_glm = H2OGeneralizedLinearEstimator(nfolds = folds,
...                                          seed = 1234,
...                                          family='binomial')
>>> cars_glm.train(x=predictors,
...                y=response,
...                training_frame=cars)
>>> cars_glm.auc(xval=True)
""",
    seed="""
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
>>> glm_w_seed = H2OGeneralizedLinearEstimator(family = 'binomial',
...                                            seed = 1234)
>>> glm_w_seed.train(x = predictors,
...                  y = response,
...                  training_frame = train,
...                  validation_frame = valid)
>>> print(glm_w_seed_1.auc(valid=True))
""",
    keep_cross_validation_models="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> cars_glm = H2OGeneralizedLinearEstimator(keep_cross_validation_models = True,
...                                          nfolds = 5,
...                                          seed = 1234,
...                                          family = "binomial")
>>> cars_glm.train(x = predictors,
...                y = response,
...                training_frame = train)
>>> cars_glm_cv_models = cars_glm.cross_validation_models()
>>> print(cars_glm.cross_validation_models())
""",
    keep_cross_validation_predictions="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> cars_glm = H2OGeneralizedLinearEstimator(keep_cross_validation_predictions = True,
...                                          nfolds = 5,
...                                          seed = 1234,
...                                          family = "binomial")
>>> cars_glm.train(x = predictors,
...                y = response,
...                training_frame = train)
>>> cars_glm.cross_validation_predictions()
""",
    keep_cross_validation_fold_assignment="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> cars_glm = H2OGeneralizedLinearEstimator(keep_cross_validation_fold_assignment = True,
...                                          nfolds = 5,
...                                          seed = 1234,
...                                          family = "binomial")
>>> cars_glm.train(x = predictors,
...                y = response,
...                training_frame = train)
>>> cars_glm.cross_validation_fold_assignment()
""",
    fold_assignment="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> assignment_type = "Random"
>>> cars_gml = H2OGeneralizedLinearEstimator(fold_assignment = assignment_type,
...                                          nfolds = 5,
...                                          family = 'binomial',
...                                          seed = 1234)
>>> cars_glm.train(x=predictors,
...                y=response,
...                training_frame=cars)
>>> cars_glm.auc(train=True)
""",
    fold_column="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> fold_numbers = cars.kfold_column(n_folds = 5, seed = 1234)
>>> fold_numbers.set_names(["fold_numbers"])
>>> cars = cars.cbind(fold_numbers)
>>> print(cars['fold_numbers'])
>>>  cars_glm = H2OGeneralizedLinearEstimator(seed = 1234,
...                                           family="binomial")
>>> cars_glm.train(x=predictors,
...                y=response,
...                training_frame=cars,
...                fold_column="fold_numbers")
>>> cars_glm.auc(xval=True)
""",
    response_column="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response_column = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> cars_glm = H2OGeneralizedLinearEstimator(seed = 1234,
...                                          family = 'binomial')
>>> cars_glm.train(x = predictors,
...                y = response_column,
...                training_frame = train,
...                validation_frame = valid)
>>> cars_glm.auc(valid=True)
""",
    ignored_columns="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> ignored_col = ["cylinders","power"]
>>> cars_glm = H2OGeneralizedLinearEstimator(seed = 1234,
...                                          family='binomial',
...                                          ignored_columns=ignored_col)
>>> cars_glm.train(y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> cars_glm.auc()
""",
    ignore_const_cols="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> cars["const_1"] = 6
>>> cars["const_2"] = 7
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> cars_glm = H2OGeneralizedLinearEstimator(seed = 1234,
...                                          ignore_const_cols = True,
...                                          family = "binomial")
>>> cars_glm.train(x = predictors,
...                y = response,
...                training_frame = train,
...                validation_frame = valid)
>>> cars_glm.auc(valid=True)
""",
    score_each_iteration="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> cars_glm = H2OGeneralizedLinearEstimator(score_each_iteration = True,
...                                          seed = 1234,
...                                          family = 'binomial')
>>> cars_glm.train(x = predictors,
...                y = response,
...                training_frame = train,
...                validation_frame = valid)
>>> cars_glm.scoring_history()
""",
    offset_column="""
>>> boston = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/BostonHousing.csv")
>>> predictors = boston.columns[:-1]
>>> response = "medv"
>>> boston['chas'] = boston['chas'].asfactor()
>>> boston["offset"] = boston["medv"].log()
>>> train, valid = boston.split_frame(ratios = [.8], seed = 1234)
>>> boston_glm = H2OGeneralizedLinearEstimator(offset_column = "offset",
...                                            seed = 1234)
>>> boston_glm.train(x=predictors,
...                  y=response,
...                  training_frame=train,
...                  validation_frame=valid)
>>> boston_glm.mse(valid=True)
""",
    weights_column="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios = [.8], seed = 1234)
>>> cars_glm = H2OGeneralizedLinearEstimator(seed = 1234,
...                                          family = 'binomial')
>>> cars_glm.train(x = predictors,
...                y = response,
...                training_frame = train,
...                validation_frame = valid,
...                weights_column = "weight")
>>> cars_glm.auc(valid=True)
""",
    family="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios = [.8])
>>> cars_glm = H2OGeneralizedLinearEstimator(family = 'binomial')
>>> cars_glm.train(x = predictors,
...                y = response,
...                training_frame = train,
...                validation_frame = valid)
>>> cars_glm.auc(valid = True)
""",
    tweedie_variance_power="""
>>> auto = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/auto.csv")
>>> predictors = auto.names
>>> predictors.remove('y')
>>> response = "y"
>>> train, valid = auto.split_frame(ratios = [.8])
>>> auto_glm = H2OGeneralizedLinearEstimator(family = 'tweedie',
...                                          tweedie_variance_power = 1)
>>> auto_glm.train(x = predictors,
...                y = response,
...                training_frame = train,
...                validation_frame = valid)
>>> print(auto_glm.mse(valid=True))
""",
    tweedie_link_power="""
>>> auto = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/auto.csv")
>>> predictors = auto.names
>>> predictors.remove('y')
>>> response = "y"
>>> train, valid = auto.split_frame(ratios = [.8])
>>> auto_glm = H2OGeneralizedLinearEstimator(family = 'tweedie',
...                                          tweedie_link_power = 1)
>>> auto_glm.train(x = predictors,
...                y = response,
...                training_frame = train,
...                validation_frame = valid)
>>> print(auto_glm.mse(valid=True))
""",
    theta="""
>>> h2o_df = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/glm_test/Motor_insurance_sweden.txt")
>>> predictors = ["Payment", "Insured", "Kilometres", "Zone", "Bonus", "Make"]
>>> response = "Claims"
>>> negativebinomial_fit = H2OGeneralizedLinearEstimator(family="negativebinomial",
...                                                      link="identity",
...                                                      theta=0.5)
>>> negativebinomial_fit.train(x=predictors,
...                            y=response,
...                            training_frame=h2o_df)
>>> negativebinomial_fit.scoring_history()
""",
    solver="""
>>> boston = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/BostonHousing.csv")
>>> predictors = boston.columns[:-1]
>>> response = "medv"
>>> boston['chas'] = boston['chas'].asfactor()
>>> train, valid = boston.split_frame(ratios = [.8])
>>> boston_glm = H2OGeneralizedLinearEstimator(solver = 'irlsm')
>>> boston_glm.train(x = predictors,
...                  y = response,
...                  training_frame = train,
...                  validation_frame = valid)
>>> print(boston_glm.mse(valid=True))
""",
    alpha="""
>>> boston = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/BostonHousing.csv")
>>> predictors = boston.columns[:-1]
>>> response = "medv"
>>> boston['chas'] = boston['chas'].asfactor()
>>> train, valid = boston.split_frame(ratios = [.8])
>>> boston_glm = H2OGeneralizedLinearEstimator(alpha = .25)
>>> boston_glm.train(x = predictors,
...                  y = response,
...                  training_frame = train,
...                  validation_frame = valid)
>>> print(boston_glm.mse(valid=True))
""",
    lambda_="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> airlines["Year"]= airlines["Year"].asfactor()
>>> airlines["Month"]= airlines["Month"].asfactor()
>>> airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
>>> airlines["Cancelled"] = airlines["Cancelled"].asfactor()
>>> airlines['FlightNum'] = airlines['FlightNum'].asfactor()
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> response = "IsDepDelayed"
>>> train, valid= airlines.split_frame(ratios = [.8])
>>> airlines_glm = H2OGeneralizedLinearEstimator(family = 'binomial',
...                                              lambda_ = .0001)
>>> airlines_glm.train(x = predictors,
...                    y = response
...                    trainig_frame = train,
...                    validation_frame = valid)
>>> print(airlines_glm.auc(valid=True))
""",
    lambda_search="""
>>> boston = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/BostonHousing.csv")
>>> predictors = boston.columns[:-1]
>>> response = "medv"
>>> boston['chas'] = boston['chas'].asfactor()
>>> train, valid = boston.split_frame(ratios = [.8])
>>> boston_glm = H2OGeneralizedLinearEstimator(lambda_search = True)
>>> boston_glm.train(x = predictors,
...                  y = response,
...                  training_frame = train,
...                  validation_frame = valid)
>>> print(boston_glm.mse(valid=True))
""",
    early_stopping="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios = [.8])
>>> cars_glm = H2OGeneralizedLinearEstimator(family = 'binomial',
...                                          early_stopping = True)
>>> cars_glm.train(x = predictors,
...                y = response,
...                training_frame = train,
...                validation_frame = valid)
>>> cars_glm.auc(valid = True)
""",
    nlambdas="""
>>> boston = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/BostonHousing.csv")
>>> predictors = boston.columns[:-1]
>>> response = "medv"
>>> boston['chas'] = boston['chas'].asfactor()
>>> train, valid = boston.split_frame(ratios = [.8])
>>> boston_glm = H2OGeneralizedLinearEstimator(lambda_search = True,
...                                            nlambdas = 50)
>>> boston_glm.train(x = predictors,
...                  y = response,
...                  training_frame = train,
...                  validation_frame = valid)
>>> print(boston_glm.mse(valid=True))
""",
)
