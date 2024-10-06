options = dict(
)

doc = dict(
    __class__="""
Builds a Decision Tree (DT) on a preprocessed dataset.
"""
)
examples = dict(
    categorical_encoding="""
    >>> import h2o
    >>> from h2o.estimators import H2ODecisionTreeEstimator
    >>> h2o.init()
    >>> prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv")
    >>> target_variable = 'CAPSULE'
    >>> prostate["RACE"] = prostate["RACE"].asfactor()
    >>> prostate[target_variable] = prostate[target_variable].asfactor()
    >>> train, test = prostate.split_frame(ratios=[0.7])
    >>> sdt_h2o = H2ODecisionTreeEstimator(model_id="decision_tree.hex",
    ...                                    max_depth=5,
    ...                                    categorical_encoding="binary")
    >>> sdt_h2o.train(y=target_variable, training_frame=train)
    >>> pred_test = sdt_h2o.predict(test)
    """,
    ignore_const_cols="""
    >>> import h2o
    >>> from h2o.estimators import H2ODecisionTreeEstimator
    >>> h2o.init()
    >>> prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv")
    >>> target_variable = 'CAPSULE'
    >>> prostate[target_variable] = prostate[target_variable].asfactor()
    >>> prostate["const_1"] = 6
    >>> train, test = prostate.split_frame(ratios=[0.7])
    >>> sdt_h2o = H2ODecisionTreeEstimator(model_id="decision_tree.hex",
    ...                                    max_depth=5,
    ...                                    ignore_const_cols=True)
    >>> sdt_h2o.train(y=target_variable, training_frame=train)
    >>> pred_test = sdt_h2o.predict(test)
    """,
    max_depth="""
    >>> import h2o
    >>> from h2o.estimators import H2ODecisionTreeEstimator
    >>> h2o.init()
    >>> prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv")
    >>> target_variable = 'CAPSULE'
    >>> prostate[target_variable] = prostate[target_variable].asfactor()
    >>> train, test = prostate.split_frame(ratios=[0.7])
    >>> sdt_h2o = H2ODecisionTreeEstimator(model_id="decision_tree.hex",
    ...                                    max_depth=5)
    >>> sdt_h2o.train(y=target_variable, training_frame=train)
    >>> pred_test = sdt_h2o.predict(test)
    """,
    min_rows="""
    >>> import h2o
    >>> from h2o.estimators import H2ODecisionTreeEstimator
    >>> h2o.init()
    >>> prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv")
    >>> target_variable = 'CAPSULE'
    >>> prostate[target_variable] = prostate[target_variable].asfactor()
    >>> train, test = prostate.split_frame(ratios=[0.7])
    >>> sdt_h2o = H2ODecisionTreeEstimator(model_id="decision_tree.hex",
    ...                                    max_depth=5,
    ...                                    min_rows=20)
    >>> sdt_h2o.train(y=target_variable, training_frame=train)
    >>> pred_test = sdt_h2o.predict(test)
    """
)
