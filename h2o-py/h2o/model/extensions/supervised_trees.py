from warnings import warn


class SupervisedTrees:

    def _update_tree_weights(self, frame, weights_column):
        """
        Re-calculates tree-node weights based on provided dataset. Modifying node weights will affect how
        contribution predictions (Shapley values) are calculated. This can be used to explain the model
        on a curated sub-population of the training dataset.

        :param frame: frame that will be used to re-populate trees with new observations and to collect per-node weights 
        :param weights_column: name of the weight column (can be different from training weights) 
        """
        from h2o.expr import ExprNode
        result = ExprNode("tree.update.weights", self, frame, weights_column)._eval_driver('scalar')._cache._data
        if result != "OK":
            warn(result)

    def _calibrate(self, calibration_model):
        """
        Calibrate a trained model with a supplied calibration model.

        Only tree-based models can be calibrated.

        :param calibration_model: a GLM model (for Platt Scaling) or Isotonic Regression model trained with the purpose
            of calibrating output of this model.

        :examples:

        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> from h2o.estimators.isotonicregression import H2OIsotonicRegressionEstimator
        >>> df = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/ecology_model.csv")
        >>> df["Angaus"] = df["Angaus"].asfactor()
        >>> train, calib = df.split_frame(ratios=[.8], destination_frames=["eco_train", "eco_calib"], seed=42)
        >>> model = H2OGradientBoostingEstimator()
        >>> model.train(x=list(range(2, train.ncol)), y="Angaus", training_frame=train)
        >>> isotonic_train = calib[["Angaus"]]
        >>> isotonic_train = isotonic_train.cbind(model.predict(calib)["p1"])
        >>> h2o_iso_reg = H2OIsotonicRegressionEstimator(out_of_bounds="clip")
        >>> h2o_iso_reg.train(training_frame=isotonic_train, x="p1", y="Angaus")
        >>> model.calibrate(h2o_iso_reg)
        >>> model.predict(train)
        """
        from h2o.expr import ExprNode
        result = ExprNode("set.calibration.model", self, calibration_model)._eval_driver('scalar')._cache._data
        if result != "OK":
            warn(result)
