def class_extensions():
    def rule_importance(self):
        """
        Retrieve rule importances for a Rulefit model

        :return: H2OTwoDimTable
        """
        if self._model_json["algo"] != "rulefit":
            raise H2OValueError("This function is available for Rulefit models only")
        return self._model_json["output"]['rule_importance']

extensions = dict(
    __class__=class_extensions,
)

doc = dict(
    __class__="""
Builds a RuleFit on a parsed dataset, for regression or 
classification. 
"""
)
