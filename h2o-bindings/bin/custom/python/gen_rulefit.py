def class_extensions():
    def rule_importance(self):
        """
        Retrieve rule importances for a Rulefit model

        :return: H2OTwoDimTable
        """
        if self._model_json["algo"] != "rulefit":
            raise H2OValueError("This function is available for Rulefit models only")
        return self._model_json["output"]['rule_importance']

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
        return H2OFrame._expr(expr=ExprNode("rulefit.fit.rules", self, frame, rule_ids))
    

extensions = dict(
    __class__=class_extensions,
)

doc = dict(
    __class__="""
Builds a RuleFit on a parsed dataset, for regression or 
classification. 
"""
)
