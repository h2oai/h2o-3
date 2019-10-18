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
        >>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
        >>> predictors = ["home.dest", "cabin", "embarked"]
        >>> response = "survived"
        >>> titanic["survived"] = titanic["survived"].asfactor()
        >>> fold_col = "kfold_column"
        >>> titanic[fold_col] = titanic.kfold_column(n_folds=5, seed=1234)
        >>> titanic_te = H2OTargetEncoderEstimator(k = 0.7,
        ...                                        f = 0.3,
        ...                                        data_leakage_handling = "None",
        ...                                        blending = True)
        >>> titanic_te.train(x = predictors,
        ...                  y = response,
        ...                  training_frame = titanic)
        >>> transformed = titanic_te.transform(frame = titanic,
        ...                                    data_leakage_handling="None",
        ...                                    seed = 1234)
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

examples = dict(
    blending="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> predictors = ["home.dest", "cabin", "embarked"]
>>> response = "survived"
>>> titanic["survived"] = titanic["survived"].asfactor()
>>> fold_col = "kfold_column"
>>> titanic[fold_col] = titanic.kfold_column(n_folds=5, seed=1234)
>>> titanic_te = H2OTargetEncoderEstimator(k = 0.7,
...                                        f = 0.3,
...                                        data_leakage_handling= "None",
...                                        blending = True)
>>> titanic_te.train(x = predictors,
...                  y = response,
...                  training_frame = titanic)
>>> titanic_te
""",
    data_leakage_handling="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> predictors = ["home.dest", "cabin", "embarked"]
>>> response = "survived"
>>> titanic["survived"] = titanic["survived"].asfactor()
>>> fold_col = "kfold_column"
>>> titanic[fold_col] = titanic.kfold_column(n_folds=5, seed=1234)
>>> titanic_te = H2OTargetEncoderEstimator(k = 0.7,
...                                        f = 0.3,
...                                        data_leakage_handling= "k_fold",
...                                        blending = True)
>>> titanic_te.train(x = predictors,
...                  y = response,
...                  training_frame = titanic)
>>> titanic_te
""",
    f="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> predictors = ["home.dest", "cabin", "embarked"]
>>> response = "survived"
>>> titanic["survived"] = titanic["survived"].asfactor()
>>> fold_col = "kfold_column"
>>> titanic[fold_col] = titanic.kfold_column(n_folds=5, seed=1234)
>>> titanic_te = H2OTargetEncoderEstimator(k = 0.7,
...                                        f = 0.3,
...                                        data_leakage_handling = "None",
...                                        blending = True)
>>> titanic_te.train(x = predictors,
...                  y = response,
...                  training_frame = titanic)
>>> titanic_te
""",
    fold_column="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> predictors = ["home.dest", "cabin", "embarked"]
>>> response = "survived"
>>> titanic["survived"] = titanic["survived"].asfactor()
>>> fold_col = "kfold_column"
>>> titanic[fold_col] = titanic.kfold_column(n_folds=5, seed=1234)
>>> titanic_te = H2OTargetEncoderEstimator(k = 0.7,
...                                        f = 0.3,
...                                        data_leakage_handling = "None",
...                                        blending = True)
>>> titanic_te.train(x = predictors,
...                  y = response,
...                  training_frame = titanic)
>>> titanic_te
""",
    ignored_columns="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> predictors = ["home.dest", "cabin", "embarked"]
>>> response = "survived"
>>> ignored = ["fare","age"]
>>> titanic["survived"] = titanic["survived"].asfactor()
>>> fold_col = "kfold_column"
>>> titanic[fold_col] = titanic.kfold_column(n_folds=5, seed=1234)
>>> titanic_te = H2OTargetEncoderEstimator(k = 0.7,
...                                        f = 0.3,
...                                        data_leakage_handling = "None",
...                                        ignored_columns = ignored)
>>> titanic_te.train(y = response,
...                  training_frame = titanic)
>>> titanic_te
""",
    k="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> predictors = ["home.dest", "cabin", "embarked"]
>>> response = "survived"
>>> titanic["survived"] = titanic["survived"].asfactor()
>>> fold_col = "kfold_column"
>>> titanic[fold_col] = titanic.kfold_column(n_folds=5, seed=1234)
>>> titanic_te = H2OTargetEncoderEstimator(k = 0.7,
...                                        f = 0.3,
...                                        data_leakage_handling = "None",
...                                        blending = True)
>>> titanic_te.train(x = predictors,
...                  y = response,
...                  training_frame = titanic)
>>> titanic_te
""",
    response_column="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> predictors = ["home.dest", "cabin", "embarked"]
>>> response_column = "survived"
>>> titanic["survived"] = titanic["survived"].asfactor()
>>> fold_col = "kfold_column"
>>> titanic[fold_col] = titanic.kfold_column(n_folds=5, seed=1234)
>>> titanic_te = H2OTargetEncoderEstimator(k = 0.7,
...                                        f = 0.3,
...                                        data_leakage_handling = "None",
...                                        blending = True)
>>> titanic_te.train(x = predictors,
...                  y = response_column,
...                  training_frame = titanic)
>>> titanic_te
""",
    training_frame="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> predictors = ["home.dest", "cabin", "embarked"]
>>> response = "survived"
>>> titanic["survived"] = titanic["survived"].asfactor()
>>> fold_col = "kfold_column"
>>> titanic[fold_col] = titanic.kfold_column(n_folds=5, seed=1234)
>>> titanic_te = H2OTargetEncoderEstimator(k = 0.7,
...                                        f = 0.3,
...                                        data_leakage_handling = "None",
...                                        blending = True)
>>> titanic_te.train(x = predictors,
...                  y = response,
...                  training_frame = titanic)
>>> titanic_te
"""
)
