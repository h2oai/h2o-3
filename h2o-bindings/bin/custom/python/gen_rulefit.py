deprecated_params = dict(Lambda='lambda_')

def class_extensions():
    def rule_importance(self):
        """
        Retrieve rule importances for a Rulefit model

        :return: H2OTwoDimTable
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
>>> train, test = df.split_frame(ratios=[0.8], seed=1)
>>> x = ["age", "sibsp", "parch", "fare", "sex", "pclass"]
>>> y = "survived"
>>> rfit = H2ORuleFitEstimator(max_rule_length=10,max_num_rules=100, algorithm="auto", seed=1)
>>> rfit.train(training_frame=train, x=x, y=y)
>>> print(rfit.rule_importance())
>>> rfit.predict(test)

""",
    max_categorical_levels="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2ORuleFitEstimator
>>> f = "https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv"
>>> df = h2o.import_file(path=f, col_types={'pclass': "enum", 'survived': "enum"})
>>> train, test = df.split_frame(ratios=[0.8], seed=1)
>>> x = ["age", "sibsp", "parch", "fare", "sex", "pclass"]
>>> y = "survived"
>>> rfit = H2ORuleFitEstimator(max_rule_length=10,max_num_rules=100, max_categorical_levels=10, seed=1)
>>> rfit.train(training_frame=train, x=x, y=y)
>>> print(rfit.rule_importance())
>>> rfit.predict(test)
""",
    max_num_rules="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators import H2ORuleFitEstimator
>>> f = "https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv"
>>> df = h2o.import_file(path=f, col_types={'pclass': "enum", 'survived': "enum"})
>>> train, test = df.split_frame(ratios=[0.8], seed=1)
>>> x = ["age", "sibsp", "parch", "fare", "sex", "pclass"]
>>> y = "survived"
>>> rfit = H2ORuleFitEstimator(max_rule_length=10,max_num_rules=100, max_num_rules=-1, seed=1)
>>> rfit.train(training_frame=train, x=x, y=y)
>>> print(rfit.rule_importance())
>>> rfit.predict(test)
""",
)