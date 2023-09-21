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
    alpha="""
>>> from h2o.estimators import H2OANOVAGLMEstimator 
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
""",
    balance_classes="""
>>> from h2o.estimators import H2OANOVAGLMEstimator
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip", balance_classes=False)
""",
    class_sampling_factors"""
>>> from h2o.estimators import H2OANOVAGLMEstimator
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",class_sampling_factors=None)
""",
    compute_p_values="""
>>> from h2o.estimators import H2OANOVAGLMEstimator
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",compute_p_values=True)
""",
    early_stopping="""
>>> from h2o.estimators import H2OANOVAGLMEstimator
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",early_stopping=False)
""",
    family="""
>>> from h2o.estimators import H2OANOVAGLMEstimator
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family="auto",lambda_=0,missing_values_handling="skip")
""",
    highest_interaction_term="""
>>> from h2o.estimators import H2OANOVAGLMEstimator
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",highest_interaction_term=2)
""",
    ignore_const_cols="""
>>> from h2o.estimators import H2OANOVAGLMEstimator
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",ignore_const_cols=True)
""",
    lambda_="""
>>> from h2o.estimators import H2OANOVAGLMEstimator
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial', lambda_=0, missing_values_handling="skip")
""",
    lambda_search="""
>>> from h2o.estimators import H2OANOVAGLMEstimator
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",lambda_search=False)
""",
    link="""
>>> from h2o.estimators import H2OANOVAGLMEstimator
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",link="family_default")
""",
    max_after_balance_size="""
>>> from h2o.estimators import H2OANOVAGLMEstimator
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",max_after_balance_size=5.0)    
""",
    max_iterations="""
>>> from h2o.estimators import H2OANOVAGLMEstimator
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",max_iterations=0)    
""",
   max_runtime_secs="""
>>> from h2o.estimators import H2OANOVAGLMEstimator
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",max_runtime_secs=0.0)    
""",
    missing_values_handling="""
>>> from h2o.estimators import H2OANOVAGLMEstimator
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="mean_imputation")    
""",
    non_negative="""
>>> from h2o.estimators import H2OANOVAGLMEstimator
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",non_negative=false)  
""",
    nparallelism="""
>>> from h2o.estimators import H2OANOVAGLMEstimator
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",nparallelism=4)  
""",
    plug_values="""
>>> from h2o.estimators import H2OANOVAGLMEstimator
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="PlugValues",plug_values=means)  
""",
    prior="""
>>> from h2o.estimators import H2OANOVAGLMEstimator
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",prior=0.0)  
""",
    save_transformed_framekeys="""
>>> from h2o.estimators import H2OANOVAGLMEstimator
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",save_transformed_framekeys=False)  
""",
    score_each_iteration="""
>>> from h2o.estimators import H2OANOVAGLMEstimator
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",score_each_iteration=False)  
""",
    seed="""
>>> from h2o.estimators import H2OANOVAGLMEstimator
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",seed=-1)  
""",
    solver="""
>>> from h2o.estimators import H2OANOVAGLMEstimator
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",solver="irlsm")  
""",
    standardize="""
>>> from h2o.estimators import H2OANOVAGLMEstimator
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",solver="True")
""",
    stopping_metric="""
>>> from h2o.estimators import H2OANOVAGLMEstimator
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",stopping_metric="auto")
""",
    stopping_rounds="""
>>> from h2o.estimators import H2OANOVAGLMEstimator
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",stopping_rounds=0)
""",
    stopping_tolerance="""
>>> from h2o.estimators import H2OANOVAGLMEstimator
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",stopping_tolerance=0.001)
""",
    theta="""
>>> from h2o.estimators import H2OANOVAGLMEstimator
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",theta=0.0)
""",
    training_frame="""
>>> from h2o.estimators import H2OANOVAGLMEstimator
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",training_frame)
""",
    tweedie_link_power="""
>>> from h2o.estimators import H2OANOVAGLMEstimator
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",tweedie_link_power=1.0)
""",
    tweedie_variance_power="""
>>> from h2o.estimators import H2OANOVAGLMEstimator
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",tweedie_variance_power=0.0)
"""
)
