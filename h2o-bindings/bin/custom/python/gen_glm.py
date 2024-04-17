options = dict(
    model_extensions=[
        'h2o.model.extensions.ScoringHistoryGLM',
        'h2o.model.extensions.StandardCoef',
        'h2o.model.extensions.VariableImportance',
        'h2o.model.extensions.Fairness',
        'h2o.model.extensions.Contributions',
    ],
)
deprecated_params = dict(Lambda='lambda_')


def update_param(name, param):
    if name == 'distribution':
        param['values'].remove('custom')
        return param
    return None  # param untouched


def class_extensions():
    def get_regression_influence_diagnostics(self):
        """
        For GLM model, if influence is set to dfbetas, a frame containing the original predictors, response
        and DFBETA_ for each predictors that are used in building the model is returned.
                
        :return: H2OFrame containing predictors used in building the model, response and DFBETA_ for each predictor.
        
        :examples:
        >>> d = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv")
        >>> m = H2OGeneralizedLinearEstimator(family = 'binomial', 
        ...                                   lambda_=0.0, 
        ...                                   standardize=False, 
        ...                                   influence="dfbetas")
        >>> m.train(training_frame = d,
        ...         x = [2,3,4,5,6,7,8],
        ...         y = 1)
        >>> ridFrame = m.get_regression_influence_diagnostics()
        >>> print("column names of regression influence diagnostics frame is {0}".format(ridFrame.names))
        """

        if self.actual_params["influence"]=="dfbetas":
            return h2o.get_frame(self._model_json["output"]["regression_influence_diagnostics"]['name'])
        else:
            raise H2OValueError("get_regression_influence_diagnostics can only be called if influence='dfbetas'.")
    
    @staticmethod
    def getAlphaBest(model):
        """
        Extract best alpha value found from glm model.

        :param model: source lambda search model

        :examples:

        >>> d = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv")
        >>> m = H2OGeneralizedLinearEstimator(family = 'binomial',
        ...                                   lambda_search = True,
        ...                                   solver = 'COORDINATE_DESCENT')
        >>> m.train(training_frame = d,
        ...         x = [2,3,4,5,6,7,8],
        ...         y = 1)
        >>> bestAlpha = H2OGeneralizedLinearEstimator.getAlphaBest(m)
        >>> print("Best alpha found is {0}".format(bestAlpha))
        """
        return model._model_json["output"]["alpha_best"]
        
    @staticmethod
    def getLambdaBest(model):
        """
        Extract best lambda value found from glm model.

        :param model: source lambda search model

        :examples:

        >>> d = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv")
        >>> m = H2OGeneralizedLinearEstimator(family = 'binomial',
        ...                                   lambda_search = True,
        ...                                   solver = 'COORDINATE_DESCENT')
        >>> m.train(training_frame = d,
        ...         x = [2,3,4,5,6,7,8],
        ...         y = 1)
        >>> bestLambda = H2OGeneralizedLinearEstimator.getLambdaBest(m)
        >>> print("Best lambda found is {0}".format(bestLambda))
        """
        return model._model_json["output"]["lambda_best"]

    @staticmethod
    def getLambdaMax(model):
        """
        Extract the maximum lambda value used during lambda search.
    
        :param model: source lambda search model
    
        :examples:
    
        >>> d = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv")
        >>> m = H2OGeneralizedLinearEstimator(family = 'binomial',
        ...                                   lambda_search = True,
        ...                                   solver = 'COORDINATE_DESCENT')
        >>> m.train(training_frame = d,
        ...         x = [2,3,4,5,6,7,8],
        ...         y = 1)
        >>> maxLambda = H2OGeneralizedLinearEstimator.getLambdaMax(m)
        >>> print("Maximum lambda found is {0}".format(maxLambda))
        """
        lambdaMax = model._model_json["output"]["lambda_max"] # will be -1 if lambda_search is disabled
        if lambdaMax >= 0:
            return lambdaMax
        else:
            raise H2OValueError("getLambdaMax(model) can only be called when lambda_search=True.")

    
    @staticmethod
    def getLambdaMin(model):
        """
        Extract the minimum lambda value calculated during lambda search from glm model.  Note that due to early stop,
        this minimum lambda value may not be used in the actual lambda search.
    
        :param model: source lambda search model
    
        :examples:
    
        >>> d = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv")
        >>> m = H2OGeneralizedLinearEstimator(family = 'binomial',
        ...                                   lambda_search = True,
        ...                                   solver = 'COORDINATE_DESCENT')
        >>> m.train(training_frame = d,
        ...         x = [2,3,4,5,6,7,8],
        ...         y = 1)
        >>> minLambda = H2OGeneralizedLinearEstimator.getLambdaMin(m)
        >>> print("Minimum lambda found is {0}".format(minLambda))
        """
        lambdaMin = model._model_json["output"]["lambda_min"] # will be -1 if lambda_search is disabled
        if lambdaMin >= 0:
            return lambdaMin
        else:
            raise H2OValueError("getLambdaMin(model) can only be called when lambda_search=True.")

    @staticmethod
    def getGLMRegularizationPath(model):
        """
        Extract full regularization path explored during lambda search from glm model.

        :param model: source lambda search model

        :examples:

        >>> d = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv")
        >>> m = H2OGeneralizedLinearEstimator(family = 'binomial',
        ...                                   lambda_search = True,
        ...                                   solver = 'COORDINATE_DESCENT')
        >>> m.train(training_frame = d,
        ...         x = [2,3,4,5,6,7,8],
        ...         y = 1)
        >>> r = H2OGeneralizedLinearEstimator.getGLMRegularizationPath(m)
        >>> m2 = H2OGeneralizedLinearEstimator.makeGLMModel(model=m,
        ...                                                 coefs=r['coefficients'][10])
        >>> dev1 = r['explained_deviance_train'][10]
        >>> p = m2.model_performance(d)
        >>> dev2 = 1-p.residual_deviance()/p.null_deviance()
        >>> print(dev1, " =?= ", dev2)
        """
        x = h2o.api("GET /3/GetGLMRegPath", data={"model": model._model_json["model_id"]["name"]})
        ns = x.pop("coefficient_names")
        res = {
            "lambdas": x["lambdas"],
            "alphas": x["alphas"],
            "explained_deviance_train": x["explained_deviance_train"],
            "explained_deviance_valid": x["explained_deviance_valid"],
            "coefficients": [dict(zip(ns, y)) for y in x["coefficients"]],
            "z_values": None if (x["z_values"] is None) else [dict(zip(ns, z)) for z in x["z_values"]],
            "p_values": None if (x["p_values"] is None) else [dict(zip(ns, p)) for p in x["p_values"]],
            "std_errs": None if (x["std_errs"] is None) else [dict(zip(ns, s)) for s in x["std_errs"]],
            "names": ns
        }
        if "coefficients_std" in x and not(x["coefficients_std"] == None):
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

        :examples:

        >>> d = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv")
        >>> m = H2OGeneralizedLinearEstimator(family='binomial',
        ...                                   lambda_search=True,
        ...                                   solver='COORDINATE_DESCENT')
        >>> m.train(training_frame=d,
        ...         x=[2,3,4,5,6,7,8],
        ...         y=1)
        >>> r = H2OGeneralizedLinearEstimator.getGLMRegularizationPath(m)
        >>> m2 = H2OGeneralizedLinearEstimator.makeGLMModel(model=m,
        ...                                                 coefs=r['coefficients'][10])
        >>> dev1 = r['explained_deviance_train'][10]
        >>> p = m2.model_performance(d)
        >>> dev2 = 1-p.residual_deviance()/p.null_deviance()
        >>> print(dev1, " =?= ", dev2)
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

    @staticmethod
    def getConstraintsInfo(model):
        """
        
        Given a constrained GLM model, the constraints descriptions, constraints values, constraints conditions and 
        whether the constraints are satisfied (true) or not (false) are returned.
        
        :param model: GLM model with linear and beta (if applicable)  constraints
        :return: H2OTwoDimTable containing the above constraints information.
        
        :example:
        >>> train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/binomial_20_cols_10KRows.csv")
        >>> response = "C21"
        >>> predictors = list(range(0,20))
        >>> loose_init_const = [] # this constraint is satisfied by default coefficient initialization
        >>> # add loose constraints
        >>> name = "C19"
        >>> values = 0.5
        >>> types = "LessThanEqual"
        >>> contraint_numbers = 0
        >>> loose_init_const.append([name, values, types, contraint_numbers])
        >>> name = "C20"
        >>> values = -0.8
        >>> types = "LessThanEqual"
        >>> contraint_numbers = 0
        >>> loose_init_const.append([name, values, types, contraint_numbers])
        >>> name = "constant"
        >>> values = -1000
        >>> types = "LessThanEqual"
        >>> contraint_numbers = 0
        >>> loose_init_const.append([name, values, types, contraint_numbers])
        >>> linear_constraints2 = h2o.H2OFrame(loose_init_const)
        >>> linear_constraints2.set_names(["names", "values", "types", "constraint_numbers"])    
        >>> # GLM model with GLM coefficients with default initialization
        >>> h2o_glm = H2OGeneralizedLinearEstimator(family="binomial", compute_p_values=True, remove_collinear_columns=True, 
        ...                                         lambda_=0.0, solver="irlsm", linear_constraints=linear_constraints2,
        ...                                         init_optimal_glm = False, seed=12345)
        >>> h2o_glm.train(x=predictors, y=response, training_frame=train)
        >>> print(H2OGeneralizedLinearEstimator.getConstraintsInfo(h2o_glm))
        """
        if model.actual_params["linear_constraints"] is not None:
            return model._model_json["output"]["linear_constraints_table"]
        else:
            raise H2OValueError("getConstraintsInfo can only be called when there are linear constraints.")
        
    @staticmethod
    def allConstraintsPassed(model):
        """
        
        Given a constrainted GLM model, this will return true  if all beta (if exists) and linear constraints are
         satified.  It will return false even if one constraint is not satisfied.  To see which ones failed, use
         getConstraintsInfo function.
         
        :param model:  GLM model with linear and beta (if applicable)  constraints
        :return: boolean True or False
        
        :example:
        >>> train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/binomial_20_cols_10KRows.csv")
        >>> response = "C21"
        >>> predictors = list(range(0,20))
        >>> loose_init_const = [] # this constraint is satisfied by default coefficient initialization
        >>> # add loose constraints
        >>> name = "C19"
        >>> values = 0.5
        >>> types = "LessThanEqual"
        >>> contraint_numbers = 0
        >>> loose_init_const.append([name, values, types, contraint_numbers])
        >>> name = "C20"
        >>> values = -0.8
        >>> types = "LessThanEqual"
        >>> contraint_numbers = 0
        >>> loose_init_const.append([name, values, types, contraint_numbers])
        >>> name = "constant"
        >>> values = -1000
        >>> types = "LessThanEqual"
        >>> contraint_numbers = 0
        >>> loose_init_const.append([name, values, types, contraint_numbers])
        >>> linear_constraints2 = h2o.H2OFrame(loose_init_const)
        >>> linear_constraints2.set_names(["names", "values", "types", "constraint_numbers"])    
        >>> # GLM model with GLM coefficients with default initialization
        >>> h2o_glm = H2OGeneralizedLinearEstimator(family="binomial", compute_p_values=True, remove_collinear_columns=True, 
        ...                                         lambda_=0.0, solver="irlsm", linear_constraints=linear_constraints2,
        ...                                         init_optimal_glm = False, seed=12345)
        >>> h2o_glm.train(x=predictors, y=response, training_frame=train)
        >>> print(H2OGeneralizedLinearEstimator.allConstraintsPassed(h2o_glm))
        """
        if model.actual_params["linear_constraints"] is not None:
            return model._model_json["output"]["all_constraints_satisfied"]
        else:
            raise H2OValueError("allConstraintsPassed can only be called when there are linear constraints.")

extensions = dict(
    __imports__="""import h2o""",
    __class__=class_extensions,
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
    beta_constraints=dict(
        setter="""
# beta_constraints can be specified as a H2OFrame or python dict
assert_is_type({pname}, None, dict, H2OFrame)
if type({pname}) is H2OFrame:
    self._parms["{sname}"]={pname}
if type({pname}) is dict:
    colnames = {pname}.keys()
    col_names = []
    upper_bounds = []
    lower_bounds = []
    for key in colnames:
        one_col_bounds = {pname}.get(key)
        col_names.append(key)
        upper_bounds.append(one_col_bounds.get('upper_bound'))
        lower_bounds.append(one_col_bounds.get('lower_bound'))
    constraints = h2o.H2OFrame(dict([("names",col_names), ("lower_bounds", lower_bounds), ("upper_bounds", upper_bounds)]))
    self._parms["{sname}"] = constraints[["names", "lower_bounds", "upper_bounds"]]
"""
    )
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
>>> train, valid = cars.split_frame(ratios=[.8],
...                                 seed=1234)
>>> cars_glm = H2OGeneralizedLinearEstimator(seed=1234,
...                                          family='binomial')
>>> cars_glm.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> cars_glm.auc(train=True)
""",
    validation_frame="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_glm = H2OGeneralizedLinearEstimator(seed=1234,
...                                          family='binomial')
>>> cars_glm.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> cars_glm.auc(valid=True)
""",
    nfolds="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> folds = 5
>>> cars_glm = H2OGeneralizedLinearEstimator(nfolds=folds,
...                                          seed=1234,
...                                          family='binomial')
>>> cars_glm.train(x=predictors,
...                y=response,
...                training_frame=cars)
>>> cars_glm.auc(xval=True)
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
>>> train, valid = airlines.split_frame(ratios=[.8], seed=1234)
>>> glm_w_seed = H2OGeneralizedLinearEstimator(family='binomial',
...                                            seed=1234)
>>> glm_w_seed.train(x=predictors,
...                  y=response,
...                  training_frame=train,
...                  validation_frame=valid)
>>> print(glm_w_seed_1.auc(valid=True))
""",
    keep_cross_validation_models="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_glm = H2OGeneralizedLinearEstimator(keep_cross_validation_models=True,
...                                          nfolds=5,
...                                          seed=1234,
...                                          family="binomial")
>>> cars_glm.train(x=predictors,
...                y=response,
...                training_frame=train)
>>> cars_glm_cv_models = cars_glm.cross_validation_models()
>>> print(cars_glm.cross_validation_models())
""",
    keep_cross_validation_predictions="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_glm = H2OGeneralizedLinearEstimator(keep_cross_validation_predictions=True,
...                                          nfolds=5,
...                                          seed=1234,
...                                          family="binomial")
>>> cars_glm.train(x=predictors,
...                y=response,
...                training_frame=train)
>>> cars_glm.cross_validation_predictions()
""",
    keep_cross_validation_fold_assignment="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_glm = H2OGeneralizedLinearEstimator(keep_cross_validation_fold_assignment=True,
...                                          nfolds=5,
...                                          seed=1234,
...                                          family="binomial")
>>> cars_glm.train(x=predictors,
...                y=response,
...                training_frame=train)
>>> cars_glm.cross_validation_fold_assignment()
""",
    fold_assignment="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> assignment_type = "Random"
>>> cars_gml = H2OGeneralizedLinearEstimator(fold_assignment=assignment_type,
...                                          nfolds=5,
...                                          family='binomial',
...                                          seed=1234)
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
>>> fold_numbers = cars.kfold_column(n_folds=5, seed=1234)
>>> fold_numbers.set_names(["fold_numbers"])
>>> cars = cars.cbind(fold_numbers)
>>> print(cars['fold_numbers'])
>>>  cars_glm = H2OGeneralizedLinearEstimator(seed=1234,
...                                           family="binomial")
>>> cars_glm.train(x=predictors,
...                y=response,
...                training_frame=cars,
...                fold_column="fold_numbers")
>>> cars_glm.auc(xval=True)
""",
    ignore_const_cols="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> cars["const_1"] = 6
>>> cars["const_2"] = 7
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_glm = H2OGeneralizedLinearEstimator(seed=1234,
...                                          ignore_const_cols=True,
...                                          family="binomial")
>>> cars_glm.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> cars_glm.auc(valid=True)
""",
    score_each_iteration="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_glm = H2OGeneralizedLinearEstimator(score_each_iteration=True,
...                                          seed=1234,
...                                          family='binomial')
>>> cars_glm.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> cars_glm.scoring_history()
""",
    offset_column="""
>>> boston = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/BostonHousing.csv")
>>> predictors = boston.columns[:-1]
>>> response = "medv"
>>> boston['chas'] = boston['chas'].asfactor()
>>> boston["offset"] = boston["medv"].log()
>>> train, valid = boston.split_frame(ratios=[.8], seed=1234)
>>> boston_glm = H2OGeneralizedLinearEstimator(offset_column="offset",
...                                            seed=1234)
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
>>> train, valid = cars.split_frame(ratios=[.8], seed=1234)
>>> cars_glm = H2OGeneralizedLinearEstimator(seed=1234,
...                                          family='binomial')
>>> cars_glm.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid,
...                weights_column="weight")
>>> cars_glm.auc(valid=True)
""",
    family="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8])
>>> cars_glm = H2OGeneralizedLinearEstimator(family='binomial')
>>> cars_glm.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> cars_glm.auc(valid = True)
""",
    tweedie_variance_power="""
>>> auto = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/auto.csv")
>>> predictors = auto.names
>>> predictors.remove('y')
>>> response = "y"
>>> train, valid = auto.split_frame(ratios=[.8])
>>> auto_glm = H2OGeneralizedLinearEstimator(family='tweedie',
...                                          tweedie_variance_power=1)
>>> auto_glm.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> print(auto_glm.mse(valid=True))
""",
    tweedie_link_power="""
>>> auto = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/auto.csv")
>>> predictors = auto.names
>>> predictors.remove('y')
>>> response = "y"
>>> train, valid = auto.split_frame(ratios=[.8])
>>> auto_glm = H2OGeneralizedLinearEstimator(family='tweedie',
...                                          tweedie_link_power=1)
>>> auto_glm.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
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
>>> train, valid = boston.split_frame(ratios=[.8])
>>> boston_glm = H2OGeneralizedLinearEstimator(solver='irlsm')
>>> boston_glm.train(x=predictors,
...                  y=response,
...                  training_frame=train,
...                  validation_frame=valid)
>>> print(boston_glm.mse(valid=True))
""",
    alpha="""
>>> boston = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/BostonHousing.csv")
>>> predictors = boston.columns[:-1]
>>> response = "medv"
>>> boston['chas'] = boston['chas'].asfactor()
>>> train, valid = boston.split_frame(ratios=[.8])
>>> boston_glm = H2OGeneralizedLinearEstimator(alpha=.25)
>>> boston_glm.train(x=predictors,
...                  y=response,
...                  training_frame=train,
...                  validation_frame=valid)
>>> print(boston_glm.mse(valid=True))
""",
    lambda_="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> airlines["Year"] = airlines["Year"].asfactor()
>>> airlines["Month"] = airlines["Month"].asfactor()
>>> airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
>>> airlines["Cancelled"] = airlines["Cancelled"].asfactor()
>>> airlines['FlightNum'] = airlines['FlightNum'].asfactor()
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> response = "IsDepDelayed"
>>> train, valid = airlines.split_frame(ratios=[.8])
>>> airlines_glm = H2OGeneralizedLinearEstimator(family='binomial',
...                                              lambda_=.0001)
>>> airlines_glm.train(x=predictors,
...                    y=response
...                    trainig_frame=train,
...                    validation_frame=valid)
>>> print(airlines_glm.auc(valid=True))
""",
    lambda_search="""
>>> boston = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/BostonHousing.csv")
>>> predictors = boston.columns[:-1]
>>> response = "medv"
>>> boston['chas'] = boston['chas'].asfactor()
>>> train, valid = boston.split_frame(ratios=[.8])
>>> boston_glm = H2OGeneralizedLinearEstimator(lambda_search=True)
>>> boston_glm.train(x=predictors,
...                  y=response,
...                  training_frame=train,
...                  validation_frame=valid)
>>> print(boston_glm.mse(valid=True))
""",
    early_stopping="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8])
>>> cars_glm = H2OGeneralizedLinearEstimator(family='binomial',
...                                          early_stopping=True)
>>> cars_glm.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> cars_glm.auc(valid=True)
""",
    nlambdas="""
>>> boston = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/BostonHousing.csv")
>>> predictors = boston.columns[:-1]
>>> response = "medv"
>>> boston['chas'] = boston['chas'].asfactor()
>>> train, valid = boston.split_frame(ratios=[.8])
>>> boston_glm = H2OGeneralizedLinearEstimator(lambda_search=True,
...                                            nlambdas=50)
>>> boston_glm.train(x=predictors,
...                  y=response,
...                  training_frame=train,
...                  validation_frame=valid)
>>> print(boston_glm.mse(valid=True))
""",
    balance_classes="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> predictors = ["displacement","power","weight","year"]
>>> response = "acceleration"
>>> train, valid = cars.split_frame(ratios=[.8])
>>> cars_glm = H2OGeneralizedLinearEstimator(balance_classes=True,
...                                          seed=1234)
>>> cars_glm.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> cars_glm.mse()
""",
    beta_constraints="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> predictors = ["displacement","power","weight","year"]
>>> response = "acceleration"
>>> train, valid = cars.split_frame(ratios=[.8])
>>> n = len(predictors)
>>> constraints = h2o.H2OFrame({'names':predictors,
...                             'lower_bounds': [-1000]*n,
...                             'upper_bounds': [1000]*n,
...                             'beta_given': [1]*n,
...                             'rho': [0.2]*n})
>>> cars_glm = H2OGeneralizedLinearEstimator(standardize=True,
...                                          beta_constraints=constraints)
>>> cars_glm.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> cars_glm.mse()
""",
    beta_epsilon="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> predictors = ["displacement","power","weight","year"]
>>> response = "acceleration"
>>> train, valid = cars.split_frame(ratios=[.8])
>>> cars_glm = H2OGeneralizedLinearEstimator(beta_epsilon=1e-3)
>>> cars_glm.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> cars_glm.mse()
""",
    class_sampling_factors="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> predictors = ["displacement","power","weight","year"]
>>> response = "acceleration"
>>> train, valid = cars.split_frame(ratios=[.8])
>>> sample_factors = [1., 0.5, 1., 1., 1., 1., 1.]
>>> cars_glm = H2OGeneralizedLinearEstimator(balance_classes=True,
...                                          class_sampling_factors=sample_factors,
...                                          seed=1234)
>>> cars_glm.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> cars_glm.mse()
""",
    compute_p_values="""
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
>>> airlines_glm = H2OGeneralizedLinearEstimator(family='binomial',
...                                              lambda_=0,
...                                              remove_collinear_columns=True,
...                                              compute_p_values=True)
>>> airlines_glm.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> airlines_glm.mse()
""",
    export_checkpoints_dir="""
>>> import tempfile
>>> from os import listdir
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> predictors = ["displacement","power","weight","year"]
>>> response = "acceleration"
>>> train, valid = cars.split_frame(ratios=[.8])
>>> checkpoints = tempfile.mkdtemp()
>>> cars_glm = H2OGeneralizedLinearEstimator(export_checkpoints_dir=checkpoints,
...                                          seed=1234)
>>> cars_glm.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> cars_glm.mse()
>>> len(listdir(checkpoints_dir))
""",
    gradient_epsilon="""
>>> boston = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/BostonHousing.csv")
>>> predictors = boston.columns[:-1]
>>> response = "medv"
>>> boston['chas'] = boston['chas'].asfactor()
>>> train, valid = boston.split_frame(ratios=[.8])
>>> boston_glm = H2OGeneralizedLinearEstimator(gradient_epsilon=1e-3)
>>> boston_glm.train(x=predictors,
...                  y=response,
...                  training_frame=train,
...                  validation_frame=valid)
>>> boston_glm.mse()
""",
    interaction_pairs="""
>>> df = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> XY = [df.names[i-1] for i in [1,2,3,4,6,8,9,13,17,18,19,31]]
>>> interactions = [XY[i-1] for i in [5,7,9]]
>>> m = H2OGeneralizedLinearEstimator(lambda_search=True,
...                                   family="binomial",
...                                   interactions=interactions)
>>> m.train(x=XY[:len(XY)], y=XY[-1],training_frame=df)
>>> m._model_json['output']['coefficients_table']
>>> coef_m = m._model_json['output']['coefficients_table']
>>> interaction_pairs = [("CRSDepTime", "UniqueCarrier"),
...                      ("CRSDepTime", "Origin"),
...                      ("UniqueCarrier", "Origin")]
>>> mexp = H2OGeneralizedLinearEstimator(lambda_search=True,
...                                      family="binomial",
...                                      interaction_pairs=interaction_pairs)
>>> mexp.train(x=XY[:len(XY)], y=XY[-1],training_frame=df)
>>> mexp._model_json['output']['coefficients_table']
""",
    interactions="""
>>> boston = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/BostonHousing.csv")
>>> predictors = boston.columns[:-1]
>>> response = "medv"
>>> boston['chas'] = boston['chas'].asfactor()
>>> train, valid = boston.split_frame(ratios=[.8])
>>> interactions_list = ['crim', 'dis']
>>> boston_glm = H2OGeneralizedLinearEstimator(interactions=interactions_list) 
>>> boston_glm.train(x=predictors,
...                  y=response,
...                  training_frame=train,
...                  validation_frame=valid)
>>> boston_glm.mse()
""",
    intercept="""
>>> iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
>>> iris['class'] = iris['class'].asfactor()
>>> predictors = iris.columns[:-1]
>>> response = 'class'
>>> train, valid = iris.split_frame(ratios=[.8])
>>> iris_glm = H2OGeneralizedLinearEstimator(family='multinomial',
...                                          intercept=True)
>>> iris_glm.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> iris_glm.logloss(valid=True)
""",
    lambda_min_ratio="""
>>> boston = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/BostonHousing.csv")
>>> predictors = boston.columns[:-1]
>>> response = "medv"
>>> boston['chas'] = boston['chas'].asfactor()
>>> train, valid = boston.split_frame(ratios=[.8])
>>> boston_glm = H2OGeneralizedLinearEstimator(lambda_min_ratio=.0001)
>>> boston_glm.train(x=predictors,
...                  y=response,
...                  training_frame=train,
...                  validation_frame=valid)
>>> boston_glm.mse()
""",
    link="""
>>> iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
>>> iris['class'] = iris['class'].asfactor()
>>> predictors = iris.columns[:-1]
>>> response = 'class'
>>> train, valid = iris.split_frame(ratios=[.8])
>>> iris_glm = H2OGeneralizedLinearEstimator(family='multinomial',
...                                          link='family_default')
>>> iris_glm.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> iris_glm.logloss()
""",
    max_active_predictors="""
>>> higgs= h2o.import_file("https://h2o-public-test-data.s3.amazonaws.com/smalldata/testng/higgs_train_5k.csv")
>>> predictors = higgs.names
>>> predictors.remove('response')
>>> response = "response"
>>> train, valid = higgs.split_frame(ratios=[.8])
>>> higgs_glm = H2OGeneralizedLinearEstimator(family='binomial',
...                                           max_active_predictors=200)
>>> higgs_glm.train(x=predictors,
...                 y=response,
...                 training_frame=train,
...                 validation_frame=valid)
>>> higgs_glm.auc()
""",
    max_after_balance_size="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> predictors = ["displacement","power","weight","year"]
>>> response = "acceleration"
>>> train, valid = cars.split_frame(ratios=[.8])
>>> max = .85
>>> cars_glm = H2OGeneralizedLinearEstimator(balance_classes=True,
...                                          max_after_balance_size=max,
...                                          seed=1234)
>>> cars_glm.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> cars_glm.mse()
""",
    max_iterations="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8])
>>> cars_glm = H2OGeneralizedLinearEstimator(family='binomial',
...                                          max_iterations=50)
>>> cars_glm.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> cars_glm.mse()
""",
    max_runtime_secs="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8])
>>> cars_glm = H2OGeneralizedLinearEstimator(max_runtime_secs=10,
...                                          seed=1234) 
>>> cars_glm.train(x=predictors,
...                y=response,
...                training_frame=train,
...                validation_frame=valid)
>>> cars_glm.mse()
""",
    missing_values_handling="""
>>> boston = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/BostonHousing.csv")
>>> predictors = boston.columns[:-1]
>>> response = "medv"
>>> boston['chas'] = boston['chas'].asfactor()
>>> boston.insert_missing_values()
>>> train, valid = boston.split_frame(ratios=[.8])
>>> boston_glm = H2OGeneralizedLinearEstimator(missing_values_handling="skip")
>>> boston_glm.train(x=predictors,
...                  y=response,
...                  training_frame=train,
...                  validation_frame=valid)
>>> boston_glm.mse()
""",
    non_negative="""
>>> airlines = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> airlines["Year"] = airlines["Year"].asfactor()
>>> airlines["Month"] = airlines["Month"].asfactor()
>>> airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
>>> airlines["Cancelled"] = airlines["Cancelled"].asfactor()
>>> airlines['FlightNum'] = airlines['FlightNum'].asfactor()
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> response = "IsDepDelayed"
>>> train, valid= airlines.split_frame(ratios=[.8])
>>> airlines_glm = H2OGeneralizedLinearEstimator(family='binomial',
...                                              non_negative=True)
>>> airlines_glm.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> airlines_glm.auc()
""",
    obj_reg="""
>>> df = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/glm_ordinal_logit/ordinal_multinomial_training_set.csv")
>>> df["C11"] = df["C11"].asfactor()
>>> ordinal_fit = H2OGeneralizedLinearEstimator(family="ordinal",
...                                             alpha=1.0,
...                                             lambda_=0.000000001,
...                                             obj_reg=0.00001,
...                                             max_iterations=1000,
...                                             beta_epsilon=1e-8,
...                                             objective_epsilon=1e-10)
>>> ordinal_fit.train(x=list(range(0,10)),
...                   y="C11",
...                   training_frame=df)
>>> ordinal_fit.mse()
""",
    objective_epsilon="""
>>> boston = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/BostonHousing.csv")
>>> predictors = boston.columns[:-1]
>>> response = "medv"
>>> boston['chas'] = boston['chas'].asfactor()
>>> train, valid = boston.split_frame(ratios=[.8])
>>> boston_glm = H2OGeneralizedLinearEstimator(objective_epsilon=1e-3)
>>> boston_glm.train(x=predictors,
...                  y=response,
...                  training_frame=train,
...                  validation_frame=valid)
>>> boston_glm.mse()
""",
    plug_values="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars = cars.drop(0)
>>> means = cars.mean()
>>> means = H2OFrame._expr(ExprNode("mean", cars, True, 0))
>>> glm_means = H2OGeneralizedLinearEstimator(seed=42)
>>> glm_means.train(training_frame=cars, y="cylinders")
>>> glm_plugs1 = H2OGeneralizedLinearEstimator(seed=42,
...                                            missing_values_handling="PlugValues",
...                                            plug_values=means)
>>> glm_plugs1.train(training_frame=cars, y="cylinders")
>>> glm_means.coef() == glm_plugs1.coef()
>>> not_means = 0.1 + (means * 0.5)
>>> glm_plugs2 = H2OGeneralizedLinearEstimator(seed=42,
...                                            missing_values_handling="PlugValues",
...                                            plug_values=not_means)
>>> glm_plugs2.train(training_frame=cars, y="cylinders")
>>> glm_means.coef() != glm_plugs2.coef()
""",
    prior="""
>>> cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
>>> cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
>>> predictors = ["displacement","power","weight","acceleration","year"]
>>> response = "economy_20mpg"
>>> train, valid = cars.split_frame(ratios=[.8])
>>> cars_glm1 = H2OGeneralizedLinearEstimator(family='binomial', prior=0.5)
>>> cars_glm1.train(x=predictors,
...                 y=response,
...                 training_frame=train,
...                 validation_frame=valid)
>>> cars_glm1.mse()
""",
    remove_collinear_columns="""
>>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
>>> airlines["Year"] = airlines["Year"].asfactor()
>>> airlines["Month"] = airlines["Month"].asfactor()
>>> airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
>>> airlines["Cancelled"] = airlines["Cancelled"].asfactor()
>>> airlines['FlightNum'] = airlines['FlightNum'].asfactor()
>>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
...               "DayOfWeek", "Month", "Distance", "FlightNum"]
>>> response = "IsDepDelayed"
>>> train, valid = airlines.split_frame(ratios=[.8])
>>> airlines_glm = H2OGeneralizedLinearEstimator(family='binomial',
...                                              lambda_=0,
...                                              remove_collinear_columns=True)
>>> airlines_glm.train(x=predictors,
...                    y=response,
...                    training_frame=train,
...                    validation_frame=valid)
>>> airlines_glm.auc()
""",
    standardize="""
>>> boston = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/BostonHousing.csv")
>>> predictors = boston.columns[:-1]
>>> response = "medv"
>>> boston['chas'] = boston['chas'].asfactor()
>>> train, valid = boston.split_frame(ratios=[.8])
>>> boston_glm = H2OGeneralizedLinearEstimator(standardize=True)
>>> boston_glm.train(x=predictors,
...                  y=response,
...                  training_frame=train,
...                  validation_frame=valid)
>>> boston_glm.mse()
""",
    generate_variable_inflation_factors="""
>>> training_data = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/glm_test/gamma_dispersion_factor_9_10kRows.csv")
>>> predictors = ['abs.C1.','abs.C2.','abs.C3.','abs.C4.','abs.C5.']
>>> response = 'resp'
>>> vif_glm = H2OGeneralizedLinearEstimator(family="gamma",
...                                         lambda_=0,
...                                         generate_variable_inflation_factors=True,
...                                         fold_assignment="modulo",
...                                         nfolds=3,
...                                         keep_cross_validation_models=True)
>>> vif_glm.train(x=predictors, y=response, training_frame=training_data)
>>> vif_glm.get_variable_inflation_factors()
"""
)
