deprecated_params = dict(Lambda='lambda_')

def class_extensions():
    def rule_importance(self):
        """
        Retrieve rule importances for a Rulefit model

        :return: H2OTwoDimTable
        
        :examples:
        >>> import h2o
        >>> h2o.init()
        >>> from h2o.estimators import H2ORuleFitEstimator
        >>> f = "https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv"
        >>> df = h2o.import_file(path=f, col_types={'pclass': "enum", 'survived': "enum"})
        >>> x = ["age", "sibsp", "parch", "fare", "sex", "pclass"]
        >>> y = "survived"
        >>> rfit = H2ORuleFitEstimator(max_rule_length=10,
        ...                            max_num_rules=100,
        ...                            seed=1)
        >>> rfit.train(training_frame=df, x=x, y=y)
        >>> rule_importance = rfit.rule_importance()
        >>> print(rfit.rule_importance())
        """
        if self._model_json["algo"] != "rulefit":
            raise H2OValueError("This function is available for Rulefit models only")

        kwargs = {}
        kwargs["model_id"] = self.model_id

        json = h2o.api("POST /3/SignificantRules", data=kwargs)
        return json['significant_rules_table']

    def predict_rules(self, frame, rule_ids):
        """
        Evaluates validity of the given rules on the given data.

        :param frame: H2OFrame on which rule validity is to be evaluated
        :param rule_ids: string array of rule ids to be evaluated against the frame
        :return: H2OFrame with a column per each input ruleId, representing a flag whether given rule is applied to the observation or not.
        
        :examples:
        >>> import h2o
        >>> h2o.init()
        >>> from h2o.estimators import H2ORuleFitEstimator
        >>> f = "https://s3.amazonaws.com/h2o-public-test-data/smalldata/iris/iris_train.csv"
        >>> df = h2o.import_file(path=f, col_types={'species': "enum"})
        >>> x = df.columns
        >>> y = "species"
        >>> x.remove(y)
        >>> train, test = df.split_frame(ratios=[.8], seed=1234)
        >>> rfit = H2ORuleFitEstimator(min_rule_length=4,
        ...                            max_rule_length=5,
        ...                            max_num_rules=3,
        ...                            seed=1234,
        ...                            model_type="rules")
        >>> rfit.train(training_frame=train, x=x, y=y, validation_frame=test)
        >>> print(rfit.predict_rules(train, ['M0T38N5_Iris-virginica']))
        """
        from h2o.frame import H2OFrame
        from h2o.utils.typechecks import assert_is_type
        from h2o.expr import ExprNode
        assert_is_type(frame, H2OFrame)
        return H2OFrame._expr(expr=ExprNode("rulefit.predict.rules", self, frame, rule_ids))
    

extensions = dict(
    __imports__="""import h2o""",
    __class__=class_extensions,
)

doc = dict(
    __class__="""
Builds a RuleFit on a parsed dataset, for regression or 
classification. 
"""
)


overrides = dict(
    lambda_=dict(
        setter="""
 assert_is_type({pname}, None, numeric, [numeric])
 self._parms["{sname}"] = {pname}
 """
    ),
)

examples = dict(
    algorithm="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2ORuleFitEstimator
>>> f = "https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv"
>>> df = h2o.import_file(path=f, col_types={'pclass': "enum", 'survived': "enum"})
>>> x = ["age", "sibsp", "parch", "fare", "sex", "pclass"]
>>> y = "survived"
>>> rfit = H2ORuleFitEstimator(max_rule_length=10,
...                            max_num_rules=100,
...                            algorithm="gbm",
...                            seed=1)
>>> rfit.train(training_frame=df, x=x, y=y)
>>> print(rfit.rule_importance())

""",
    max_categorical_levels="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2ORuleFitEstimator
>>> f = "https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv"
>>> df = h2o.import_file(path=f, col_types={'pclass': "enum", 'survived': "enum"})
>>> x = ["age", "sibsp", "parch", "fare", "sex", "pclass"]
>>> y = "survived"
>>> rfit = H2ORuleFitEstimator(max_rule_length=10,
...                            max_num_rules=100,
...                            max_categorical_levels=11,
...                            seed=1)
>>> rfit.train(training_frame=df, x=x, y=y)
>>> print(rfit.rule_importance())
""",
    max_num_rules="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2ORuleFitEstimator
>>> f = "https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv"
>>> df = h2o.import_file(path=f, col_types={'pclass': "enum", 'survived': "enum"})
>>> x = ["age", "sibsp", "parch", "fare", "sex", "pclass"]
>>> y = "survived"
>>> rfit = H2ORuleFitEstimator(max_rule_length=10,
...                            max_num_rules=3,
...                            seed=1)
>>> rfit.train(training_frame=df, x=x, y=y)
>>> print(rfit.rule_importance())
""",
    min_rule_length="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2ORuleFitEstimator
>>> f = "https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv"
>>> df = h2o.import_file(path=f, col_types={'pclass': "enum", 'survived': "enum"})
>>> x = ["age", "sibsp", "parch", "fare", "sex", "pclass"]
>>> y = "survived"
>>> rfit = H2ORuleFitEstimator(max_rule_length=10,
...                            max_num_rules=100,
...                            min_rule_length=4,
...                            seed=1)
>>> rfit.train(training_frame=df, x=x, y=y)
>>> print(rfit.rule_importance())
""",
    max_rule_length="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2ORuleFitEstimator
>>> f = "https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv"
>>> df = h2o.import_file(path=f, col_types={'pclass': "enum", 'survived': "enum"})
>>> x = ["age", "sibsp", "parch", "fare", "sex", "pclass"]
>>> y = "survived"
>>> rfit = H2ORuleFitEstimator(max_rule_length=10,
...                            max_num_rules=100,
...                            min_rule_length=3,
...                            seed=1)
>>> rfit.train(training_frame=df, x=x, y=y)
>>> print(rfit.rule_importance())
""",
    model_type="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2ORuleFitEstimator
>>> f = "https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv"
>>> df = h2o.import_file(path=f, col_types={'pclass': "enum", 'survived': "enum"})
>>> x = ["age", "sibsp", "parch", "fare", "sex", "pclass"]
>>> y = "survived"
>>> rfit = H2ORuleFitEstimator(max_rule_length=10,
...                            max_num_rules=100,
...                            model_type="rules",
...                            seed=1)
>>> rfit.train(training_frame=df, x=x, y=y)
>>> print(rfit.rule_importance())
""",
    distribution="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2ORuleFitEstimator
>>> f = "https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv"
>>> df = h2o.import_file(path=f, col_types={'pclass': "enum", 'survived': "enum"})
>>> x = ["age", "sibsp", "parch", "fare", "sex", "pclass"]
>>> y = "survived"
>>> rfit = H2ORuleFitEstimator(max_rule_length=10,
...                            max_num_rules=100,
...                            distribution="bernoulli",
...                            seed=1)
>>> rfit.train(training_frame=df, x=x, y=y)
>>> print(rfit.rule_importance())
""",
    rule_generation_ntrees="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2ORuleFitEstimator
>>> f = "https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv"
>>> df = h2o.import_file(path=f, col_types={'pclass': "enum", 'survived': "enum"})
>>> x = ["age", "sibsp", "parch", "fare", "sex", "pclass"]
>>> y = "survived"
>>> rfit = H2ORuleFitEstimator(max_rule_length=10,
...                            max_num_rules=100,
...                            rule_generation_ntrees=60,
...                            seed=1)
>>> rfit.train(training_frame=df, x=x, y=y)
>>> print(rfit.rule_importance())
"""
)
