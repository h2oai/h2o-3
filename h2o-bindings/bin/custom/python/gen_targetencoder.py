def class_extensions():
    def transform(self, frame, data_leakage_handling="None", noise=-1, seed=-1):
        """

        Apply transformation to `te_columns` based on the encoding maps generated during `trains()` method call.

        :param H2OFrame frame: to which frame we are applying target encoding transformations.
        :param str data_leakage_handling: Supported options:

        1) "KFold" - encodings for a fold are generated based on out-of-fold data.
        2) "LeaveOneOut" - leave one out. Current row's response value is subtracted from the pre-calculated per-level frequencies.
        3) "None" - we do not holdout anything. Using whole frame for training
        
        :param float noise: the amount of random noise added to the target encoding.  This helps prevent overfitting. Defaults to 0.01 * range of y.
        :param int seed: a random seed used to generate draws from the uniform distribution for random noise. Defaults to -1.

        :example:
        >>> targetEncoder = TargetEncoder(encoded_columns=te_columns, target_column=responseColumnName, blended_avg=True, inflection_point=10, smoothing=20)
                            >>> encodedTrain = targetEncoder.transform(frame=trainFrame, data_leakage_handling="None", seed=1234, is_train_or_valid=True)
        """
        output = h2o.api("GET /3/TargetEncoderTransform", data={'model': self.model_id, 'frame': frame.key,
                                                                'data_leakage_handling': data_leakage_handling,
                                                                'noise': noise,
                                                                'seed': seed})
        return h2o.get_frame(output["name"])


extensions = dict(
    __imports__="""import h2o""",
    __class__=class_extensions,
)
