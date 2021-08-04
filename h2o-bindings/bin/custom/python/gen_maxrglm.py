def class_extensions():
    def get_best_R2_values(self):
        """
        Get list of best R2 values of models with 1 predictor, 2 predictors, ..., max_predictor_number of predictors
        :param self: 
        :return: a list of best r2 values
        """
        return self._model_json["output"]["best_r2_values"]

    def get_best_model_predictors(self):
        """
        Get list of best models with 1 predictor, 2 predictors, ..., max_predictor_number of predictors that have the
        highest r2 values
        :param self: 
        :return: a list of best r2 values
        """
        return self._model_json["output"]["best_model_predictors"]
    
    @property
    def Lambda(self):
        """DEPRECATED. Use ``self.lambda_`` instead"""
        return self._parms["lambda"] if "lambda" in self._parms else None

    @Lambda.setter
    def Lambda(self, value):
        self._parms["lambda"] = value


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
H2O MaxRGLM is used to build test best model with one predictor, two predictors, ... up to max_predictor_number 
specified in the algorithm parameters.  The best model is the one with the highest R2 value.
"""
)
