rest_api_version = 3

def class_extensions():        
    @property
    def Lambda(self):
        """DEPRECATED. Use ``self.lambda_`` instead"""
        return self._parms["lambda"] if "lambda" in self._parms else None

    @Lambda.setter
    def Lambda(self, value):
        self._parms["lambda"] = value

    def result(self):
        """
        Get result frame that contains information about the model building process like for modelselection and anovaglm.

        :return: the H2OFrame that contains information about the model building process like for modelselection and anovaglm.
        """
        return H2OFrame._expr(expr=ExprNode("result", ASTId(self.key)))._frame(fill_cache=True)


extensions = dict(
    __imports__="""
    import h2o
    from h2o.base import Keyed
    from h2o.frame import H2OFrame
    from h2o.expr import ExprNode
    from h2o.expr import ASTId
    """,
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
examples = dict(
    highest_interaction_term="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2OANOVAGLMEstimator 
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE', 'VOL', 'DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',
...                                    lambda_=0,
...                                    missing_values_handling="skip",
...                                    highest_interaction_term=2)
>>> anova_model.train(x=x, y=y, training_frame=train)
>>> anova_model.summary()
""",
    link="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2OANOVAGLMEstimator 
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE', 'VOL', 'DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',
...                                    lambda_=0,
...                                    missing_values_handling="skip",
...                                    link="family_default")
>>> anova_model.train(x=x, y=y, training_frame=train)
>>> anova_model.summary()
""",
    save_transformed_framekeys="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2OANOVAGLMEstimator 
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE', 'VOL', 'DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',
...                                    lambda_=0,
...                                    missing_values_handling="skip",
...                                    save_transformed_framekeys=True)  
>>> anova_model.train(x=x, y=y, training_frame=train)
>>> transformFrame = h2o.get_frame(anova_model._model_json['output']['transformed_columns_key']['name'])
>>> print(transformFrame)
"""
)
