def class_extensions():

    _deprecated_params_ = ['k', 'f', 'noise_level']
    k = deprecated_property('k', inflection_point)
    f = deprecated_property('f', smoothing)
    noise_level = deprecated_property('noise_level', noise)

    def transform(self, frame, blending=None, inflection_point=None, smoothing=None, noise=None, **kwargs):
        """
        Apply transformation to `te_columns` based on the encoding maps generated during `train()` method call.

        :param H2OFrame frame: the frame on which to apply the target encoding transformations.
        :param boolean blending: If provided, this overrides the `blending` parameter on the model.
        :param float inflection_point: If provided, this overrides the `inflection_point` parameter on the model.
        :param float smoothing: If provided, this overrides the `smoothing` parameter on the model.
        :param float noise: If provided, this overrides the amount of random noise added to the target encoding defined on the model, this helps prevent overfitting.

        :example:
        >>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
        >>> predictors = ["home.dest", "cabin", "embarked"]
        >>> response = "survived"
        >>> titanic[response] = titanic[response].asfactor()
        >>> fold_col = "kfold_column"
        >>> titanic[fold_col] = titanic.kfold_column(n_folds=5, seed=1234)
        >>> titanic_te = H2OTargetEncoderEstimator(data_leakage_handling="leave_one_out",
        ...                                        inflection_point=35,
        ...                                        smoothing=25,
        ...                                        blending=True,
        ...                                        seed=1234)
        >>> titanic_te.train(x=predictors,
        ...                  y=response,
        ...                  training_frame=titanic)
        >>> transformed = titanic_te.transform(frame=titanic)
        """
        for k in kwargs:
            if k in ['seed', 'data_leakage_handling']:
                warnings.warn("%s is deprecated in `transform` method and will be ignored. "
                              "Instead, please ensure that it was set before training on the H2OTargetEncoderEstimator model." % k, H2ODeprecationWarning)
            else:
                raise TypeError("transform() got an unexpected keyword argument '%s'" % k)
        
        params = dict(
            model=self.model_id,
            frame=frame.key,
            blending=blending if blending is not None else self.blending,  # always need to provide blending here as we can't represent unset value 
            inflection_point=inflection_point,
            smoothing=smoothing,
            noise=noise,
        )
        
        output = h2o.api("GET /3/TargetEncoderTransform", data=params)
        return h2o.get_frame(output["name"])


extensions = dict(
    __imports__="""
import h2o
import warnings
from h2o.exceptions import H2ODeprecationWarning
from h2o.utils.metaclass import deprecated_property
""",
    __init__setparams="""
elif pname in self._deprecated_params_:
    setattr(self, pname, pvalue)  # property handles the redefinition
""",
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
>>> titanic_te = H2OTargetEncoderEstimator(inflection_point=35,
...                                        smoothing=25,
...                                        blending=True)
>>> titanic_te.train(x=predictors,
...                  y=response,
...                  training_frame=titanic)
>>> titanic_te
""",
    data_leakage_handling="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> predictors = ["home.dest", "cabin", "embarked"]
>>> response = "survived"
>>> titanic["survived"] = titanic["survived"].asfactor()
>>> fold_col = "kfold_column"
>>> titanic[fold_col] = titanic.kfold_column(n_folds=5, seed=1234)
>>> titanic_te = H2OTargetEncoderEstimator(inflection_point=35,
...                                        smoothing=25,
...                                        data_leakage_handling="k_fold",
...                                        blending=True)
>>> titanic_te.train(x=predictors,
...                  y=response,
...                  training_frame=titanic)
>>> titanic_te
""",
    fold_column="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> predictors = ["home.dest", "cabin", "embarked"]
>>> response = "survived"
>>> titanic["survived"] = titanic["survived"].asfactor()
>>> fold_col = "kfold_column"
>>> titanic[fold_col] = titanic.kfold_column(n_folds=5, seed=1234)
>>> titanic_te = H2OTargetEncoderEstimator(inflection_point=35,
...                                        smoothing=25,
...                                        blending=True)
>>> titanic_te.train(x=predictors,
...                  y=response,
...                  training_frame=titanic)
>>> titanic_te
""",
    inflection_point="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> predictors = ["home.dest", "cabin", "embarked"]
>>> response = "survived"
>>> titanic["survived"] = titanic["survived"].asfactor()
>>> fold_col = "kfold_column"
>>> titanic[fold_col] = titanic.kfold_column(n_folds=5, seed=1234)
>>> titanic_te = H2OTargetEncoderEstimator(inflection_point=35,
...                                        smoothing=25,
...                                        blending=True)
>>> titanic_te.train(x=predictors,
...                  y=response,
...                  training_frame=titanic)
>>> titanic_te
""",
    smoothing="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> predictors = ["home.dest", "cabin", "embarked"]
>>> response = "survived"
>>> titanic["survived"] = titanic["survived"].asfactor()
>>> fold_col = "kfold_column"
>>> titanic[fold_col] = titanic.kfold_column(n_folds=5, seed=1234)
>>> titanic_te = H2OTargetEncoderEstimator(inflection_point=35,
...                                        smoothing=25,
...                                        blending=True)
>>> titanic_te.train(x=predictors,
...                  y=response,
...                  training_frame=titanic)
>>> titanic_te
""",
    training_frame="""
>>> titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
>>> predictors = ["home.dest", "cabin", "embarked"]
>>> response = "survived"
>>> titanic["survived"] = titanic["survived"].asfactor()
>>> fold_col = "kfold_column"
>>> titanic[fold_col] = titanic.kfold_column(n_folds=5, seed=1234)
>>> titanic_te = H2OTargetEncoderEstimator(inflection_point=35,
...                                        smoothing=25,
...                                        blending=True)
>>> titanic_te.train(x=predictors,
...                  y=response,
...                  training_frame=titanic)
>>> titanic_te
"""
)
