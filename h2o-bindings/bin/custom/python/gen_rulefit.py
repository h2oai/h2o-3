def class_extensions():
    def rule_importance(self):
        """
        Retrieve rule importances for a Rulefit model

        :return: H2OTwoDimTable
        """
        if self._model_json["algo"] != "rulefit":
            raise H2OValueError("This function is available for Rulefit models only")
        return self._model_json["output"]['rule_importance']

    def fit_rules(self, frame, rule_ids):
        """
        Evaluates validity of the given rules on the given data. 

        :param frame: H2OFrame on which rule validity is to be evaluated
        :param rule_ids: string array of rule ids to be evaluated against the frame
        :return: H2OFrame with a column per each input ruleId, representing a flag whether given rule is applied to the observation or not.
        """
        import h2o
        from h2o.frame import H2OFrame
        from h2o.utils.typechecks import assert_is_type
        assert_is_type(frame, H2OFrame)
        astFrame = h2o.rapids('(rulefit.fit.rules {} {} {})'.format(self.model_id, frame.frame_id, rule_ids))
        return h2o.get_frame(astFrame['key']['name'])
    

extensions = dict(
    __class__=class_extensions,
)

doc = dict(
    __class__="""
Builds a RuleFit on a parsed dataset, for regression or 
classification. 
"""
)
