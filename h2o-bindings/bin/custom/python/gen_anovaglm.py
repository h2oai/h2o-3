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
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2OANOVAGLMEstimator 
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip", alpha=0.5)
>>> anova_model.train(x=x, y=y, training_frame=train)
>>> anova_model.summary()
""",
    balance_classes="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2OANOVAGLMEstimator 
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip", balance_classes=False)
>>> anova_model.train(x=x, y=y, training_frame=train)
>>> anova_model.summary()
""",
    class_sampling_factors="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2OANOVAGLMEstimator 
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",class_sampling_factors=None)
>>> anova_model.train(x=x, y=y, training_frame=train)
>>> anova_model.summary()
""",
    compute_p_values="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2OANOVAGLMEstimator 
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",compute_p_values=True)
>>> anova_model.train(x=x, y=y, training_frame=train)
>>> anova_model.summary()
""",
    early_stopping="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2OANOVAGLMEstimator 
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",early_stopping=False)
>>> anova_model.train(x=x, y=y, training_frame=train)
>>> anova_model.summary()
""",
    family="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2OANOVAGLMEstimator 
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family="auto",lambda_=0,missing_values_handling="skip")
>>> anova_model.train(x=x, y=y, training_frame=train)
>>> anova_model.summary()
""",
    highest_interaction_term="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2OANOVAGLMEstimator 
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",highest_interaction_term=2)
>>> anova_model.train(x=x, y=y, training_frame=train)
>>> anova_model.summary()
""",
    ignore_const_cols="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2OANOVAGLMEstimator 
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",ignore_const_cols=True)
>>> anova_model.train(x=x, y=y, training_frame=train)
>>> anova_model.summary()
""",
    lambda_="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2OANOVAGLMEstimator 
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial', lambda_=0, missing_values_handling="skip")
>>> anova_model.train(x=x, y=y, training_frame=train)
>>> anova_model.summary()
""",
    lambda_search="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2OANOVAGLMEstimator 
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",lambda_search=False)
>>> anova_model.train(x=x, y=y, training_frame=train)
>>> anova_model.summary()
""",
    link="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2OANOVAGLMEstimator 
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",link="family_default")
>>> anova_model.train(x=x, y=y, training_frame=train)
>>> anova_model.summary()
""",
    max_after_balance_size="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2OANOVAGLMEstimator 
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",max_after_balance_size=5.0)
>>> anova_model.train(x=x, y=y, training_frame=train)
>>> anova_model.summary()
""",
    max_iterations="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2OANOVAGLMEstimator 
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",max_iterations=0)
>>> anova_model.train(x=x, y=y, training_frame=train)
>>> anova_model.summary()    
""",
   max_runtime_secs="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2OANOVAGLMEstimator 
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",max_runtime_secs=0.0)    
>>> anova_model.train(x=x, y=y, training_frame=train)
>>> anova_model.summary()
""",
    missing_values_handling="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2OANOVAGLMEstimator 
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="mean_imputation")    
>>> anova_model.train(x=x, y=y, training_frame=train)
>>> anova_model.summary()
""",
    non_negative="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2OANOVAGLMEstimator 
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",non_negative=false)  
>>> anova_model.train(x=x, y=y, training_frame=train)
>>> anova_model.summary()
""",
    nparallelism="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2OANOVAGLMEstimator 
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",nparallelism=4)
>>> anova_model.train(x=x, y=y, training_frame=train)
>>> anova_model.summary()
""",
    plug_values="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2OANOVAGLMEstimator 
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="PlugValues",plug_values=means)
>>> anova_model.train(x=x, y=y, training_frame=train)
>>> anova_model.summary()
""",
    prior="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2OANOVAGLMEstimator 
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",prior=0.0)
>>> anova_model.train(x=x, y=y, training_frame=train)
>>> anova_model.summary()  
""",
    save_transformed_framekeys="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2OANOVAGLMEstimator 
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",save_transformed_framekeys=False)  
>>> anova_model.train(x=x, y=y, training_frame=train)
>>> anova_model.summary()
""",
    score_each_iteration="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2OANOVAGLMEstimator 
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",score_each_iteration=False) 
>>> anova_model.train(x=x, y=y, training_frame=train)
>>> anova_model.summary()  
""",
    seed="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2OANOVAGLMEstimator 
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",seed=-1)
>>> anova_model.train(x=x, y=y, training_frame=train)
>>> anova_model.summary() 
""",
    solver="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2OANOVAGLMEstimator 
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",solver="irlsm")  
>>> anova_model.train(x=x, y=y, training_frame=train)
>>> anova_model.summary() 
""",
    standardize="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2OANOVAGLMEstimator 
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",standardize=True")  
>>> anova_model.train(x=x, y=y, training_frame=train)
>>> anova_model.summary() 
""",
    stopping_metric="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2OANOVAGLMEstimator 
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",stopping_metric="auto") 
>>> anova_model.train(x=x, y=y, training_frame=train)
>>> anova_model.summary() 
""",
    stopping_rounds="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2OANOVAGLMEstimator 
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",stopping_rounds=0)
>>> anova_model.train(x=x, y=y, training_frame=train)
>>> anova_model.summary() 
""",
    stopping_tolerance="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2OANOVAGLMEstimator 
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",stopping_tolerance=0.001)
>>> anova_model.train(x=x, y=y, training_frame=train)
>>> anova_model.summary() 
""",
    theta="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2OANOVAGLMEstimator 
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",theta=0.0)
>>> anova_model.train(x=x, y=y, training_frame=train)
>>> anova_model.summary() 
""",
    training_frame="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2OANOVAGLMEstimator 
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",training_frame)
>>> anova_model.train(x=x, y=y, training_frame=train)
>>> anova_model.summary() 
""",
    tweedie_link_power="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2OANOVAGLMEstimator 
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",tweedie_link_power=1.0)
>>> anova_model.train(x=x, y=y, training_frame=train)
>>> anova_model.summary() 
""",
    tweedie_variance_power="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2OANOVAGLMEstimator 
>>> train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate_complete.csv.zip")
>>> x = ['AGE','VOL','DCAPS']
>>> y = 'CAPSULE'
>>> anova_model = H2OANOVAGLMEstimator(family='binomial',lambda_=0,missing_values_handling="skip",tweedie_variance_power=0.0)
>>> anova_model.train(x=x, y=y, training_frame=train)
>>> anova_model.summary() 
"""
)
