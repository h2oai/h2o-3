rest_api_version = 3

def class_extensions():        
    @property
    def Lambda(self):
        """DEPRECATED. Use ``self.lambda_`` instead"""
        return self._parms["lambda"] if "lambda" in self._parms else None

    @Lambda.setter
    def Lambda(self, value):
        self._parms["lambda"] = value
        
    def anova_table_frame(self):
        keyString = self._model_json["output"]["anova_table_key"]
        if keyString is None:
            return None
        else:
            return h2o.get_frame(keyString)

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
H2O ANOVAGLM is used to calculate Type III SS which is used to evaluate the contributions of individual predictors
 and their interactions to a model.  Predictors or interactions with negligible contributions to the model will have 
high p-values while those with more contributions will have low p-values. 
"""
)
