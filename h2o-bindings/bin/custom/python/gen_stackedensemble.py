rest_api_version = 99


def update_param(name, param):
    if name == 'metalearner_params':
        param['type'] = 'KeyValue'
        param['default_value'] = None
        return param
    return None  # param untouched


def class_extensions():
    def metalearner(self):
        """Print the metalearner of an H2OStackedEnsembleEstimator.

        :examples:

        >>> from h2o.estimators.random_forest import H2ORandomForestEstimator
        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator
        >>> higgs = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/testng/higgs_train_5k.csv")
        >>> train, blend = higgs.split_frame(ratios = [.8], seed = 1234)
        >>> x = train.columns
        >>> y = "response"
        >>> x.remove(y)
        >>> train[y] = train[y].asfactor()
        >>> blend[y] = blend[y].asfactor()
        >>> nfolds = 3
        >>> my_gbm = H2OGradientBoostingEstimator(distribution="bernoulli",
        ...                                       ntrees=10,
        ...                                       nfolds=nfolds,
        ...                                       fold_assignment="Modulo",
        ...                                       keep_cross_validation_predictions=True,
        ...                                       seed=1)
        >>> my_gbm.train(x=x, y=y, training_frame=train)
        >>> my_rf = H2ORandomForestEstimator(ntrees=50,
        ...                                  nfolds=nfolds,
        ...                                  fold_assignment="Modulo",
        ...                                  keep_cross_validation_predictions=True,
        ...                                  seed=1)
        >>> my_rf.train(x=x, y=y, training_frame=train)
        >>> stack_blend = H2OStackedEnsembleEstimator(base_models=[my_gbm, my_rf],
        ...                                           seed=1,
        ...                                           keep_levelone_frame=True)
        >>> stack_blend.train(x=x, y=y, training_frame=train, blending_frame=blend)
        >>> stack_blend.metalearner
        """
        model = self._model_json["output"]
        if "metalearner" in model and model["metalearner"] is not None:
            return model["metalearner"]
        print("No metalearner for this model")

    def levelone_frame_id(self):
        """Fetch the levelone_frame_id for an H2OStackedEnsembleEstimator.

        :examples:
        
        >>> from h2o.estimators.random_forest import H2ORandomForestEstimator
        >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
        >>> from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator
        >>> higgs = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/testng/higgs_train_5k.csv")
        >>> train, blend = higgs.split_frame(ratios = [.8], seed = 1234)
        >>> x = train.columns
        >>> y = "response"
        >>> x.remove(y)
        >>> train[y] = train[y].asfactor()
        >>> blend[y] = blend[y].asfactor()
        >>> nfolds = 3
        >>> my_gbm = H2OGradientBoostingEstimator(distribution="bernoulli",
        ...                                       ntrees=10,
        ...                                       nfolds=nfolds,
        ...                                       fold_assignment="Modulo",
        ...                                       keep_cross_validation_predictions=True,
        ...                                       seed=1)
        >>> my_gbm.train(x=x, y=y, training_frame=train)
        >>> my_rf = H2ORandomForestEstimator(ntrees=50,
        ...                                  nfolds=nfolds,
        ...                                  fold_assignment="Modulo",
        ...                                  keep_cross_validation_predictions=True,
        ...                                  seed=1)
        >>> my_rf.train(x=x, y=y, training_frame=train)
        >>> stack_blend = H2OStackedEnsembleEstimator(base_models=[my_gbm, my_rf],
        ...                                           seed=1,
        ...                                           keep_levelone_frame=True)
        >>> stack_blend.train(x=x, y=y, training_frame=train, blending_frame=blend)
        >>> stack_blend.levelone_frame_id()
        """
        model = self._model_json["output"]
        if "levelone_frame_id" in model and model["levelone_frame_id"] is not None:
            return model["levelone_frame_id"]
        print("No levelone_frame_id for this model")

    def stacking_strategy(self):
        model = self._model_json["output"]
        if "stacking_strategy" in model and model["stacking_strategy"] is not None:
            return model["stacking_strategy"]
        print("No stacking strategy for this model")

    # Override train method to support blending
    def train(self, x=None, y=None, training_frame=None, blending_frame=None, **kwargs):
        has_training_frame = training_frame is not None or self.training_frame is not None
        blending_frame = H2OFrame._validate(blending_frame, 'blending_frame', required=not has_training_frame)

        if not has_training_frame:
            training_frame = blending_frame  # used to bypass default checks in super class and backend and to guarantee default metrics

        def extend_parms(parms):
            if blending_frame is not None:
                parms['blending_frame'] = blending_frame
            if self.metalearner_fold_column is not None:
                parms['ignored_columns'].remove(quoted(self.metalearner_fold_column))

        super(self.__class__, self)._train(x, y, training_frame,
                                           extend_parms_fn=extend_parms,
                                           **kwargs)


extensions = dict(
    __imports__="""
from h2o.utils.shared_utils import quoted
from h2o.utils.typechecks import is_type
import json
import ast
""",
    __class__=class_extensions
)

overrides = dict(
    base_models=dict(
        setter="""
if is_type(base_models, {ptype}):
    {pname} = [b.model_id for b in {pname}]
    self._parms["{sname}"] = {pname}
else:
    assert_is_type({pname}, None, [str])
    self._parms["{sname}"] = {pname}
""",
        getter="""
base_models = self.actual_params.get("base_models", [])
base_models = [base_model["name"] for base_model in base_models]
if len(base_models) == 0:
    base_models = self._parms.get("base_models")
return base_models
"""
    ),

    metalearner_params=dict(
        getter="""
if self._parms.get("{sname}") != None:
    metalearner_params_dict =  ast.literal_eval(self._parms.get("{sname}"))
    for k in metalearner_params_dict:
        if len(metalearner_params_dict[k]) == 1: #single parameter
            metalearner_params_dict[k] = metalearner_params_dict[k][0]
    return metalearner_params_dict
else:
    return self._parms.get("{sname}")
""",
        setter="""
assert_is_type({pname}, None, {ptype})
if {pname} is not None and {pname} != "":
    for k in {pname}:
        if ("[" and "]") not in str(metalearner_params[k]):
            metalearner_params[k] = [metalearner_params[k]]
    self._parms["{sname}"] = str(json.dumps({pname}))
else:
    self._parms["{sname}"] = None
"""
    ),
)


doc = dict(
    __class__="""
Builds a stacked ensemble (aka "super learner") machine learning method that uses two
or more H2O learning algorithms to improve predictive performance. It is a loss-based
supervised learning method that finds the optimal combination of a collection of prediction
algorithms.This method supports regression and binary classification.
""",
)


examples = dict(
    __class__="""
>>> import h2o
>>> h2o.init()
>>> from h2o.estimators.random_forest import H2ORandomForestEstimator
>>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
>>> from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator
>>> col_types = ["numeric", "numeric", "numeric", "enum",
...              "enum", "numeric", "numeric", "numeric", "numeric"]
>>> data = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/prostate/prostate.csv", col_types=col_types)
>>> train, test = data.split_frame(ratios=[.8], seed=1)
>>> x = ["CAPSULE","GLEASON","RACE","DPROS","DCAPS","PSA","VOL"]
>>> y = "AGE"
>>> nfolds = 5
>>> gbm = H2OGradientBoostingEstimator(nfolds=nfolds,
...                                    fold_assignment="Modulo",
...                                    keep_cross_validation_predictions=True)
>>> gbm.train(x=x, y=y, training_frame=train)
>>> rf = H2ORandomForestEstimator(nfolds=nfolds,
...                               fold_assignment="Modulo",
...                               keep_cross_validation_predictions=True)
>>> rf.train(x=x, y=y, training_frame=train)
>>> stack = H2OStackedEnsembleEstimator(model_id="ensemble",
...                                     training_frame=train,
...                                     validation_frame=test,
...                                     base_models=[gbm.model_id, rf.model_id])
>>> stack.train(x=x, y=y, training_frame=train, validation_frame=test)
>>> stack.model_performance()
""",
    base_models="""
>>> from h2o.estimators.random_forest import H2ORandomForestEstimator
>>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
>>> from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator
>>> col_types = ["numeric", "numeric", "numeric", "enum",
...              "enum", "numeric", "numeric", "numeric", "numeric"]
>>> data = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/prostate/prostate.csv", col_types=col_types)
>>> train, test = data.split_frame(ratios=[.8], seed=1)
>>> x = ["CAPSULE","GLEASON","RACE","DPROS","DCAPS","PSA","VOL"]
>>> y = "AGE"
>>> nfolds = 5
>>> gbm = H2OGradientBoostingEstimator(nfolds=nfolds,
...                                    fold_assignment="Modulo",
...                                    keep_cross_validation_predictions=True)
>>> gbm.train(x=x, y=y, training_frame=train)
>>> rf = H2ORandomForestEstimator(nfolds=nfolds,
...                               fold_assignment="Modulo",
...                               keep_cross_validation_predictions=True)
>>> rf.train(x=x, y=y, training_frame=train)
>>> stack = H2OStackedEnsembleEstimator(model_id="ensemble",
...                                     training_frame=train,
...                                     validation_frame=test,
...                                     base_models=[gbm.model_id, rf.model_id])
>>> stack.train(x=x, y=y, training_frame=train, validation_frame=test)
>>> stack.model_performance()
""",
    blending_frame="""
>>> from h2o.estimators.random_forest import H2ORandomForestEstimator
>>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
>>> from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator
>>> higgs = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/testng/higgs_train_5k.csv")
>>> train, blend = higgs.split_frame(ratios = [.8], seed = 1234)
>>> x = train.columns
>>> y = "response"
>>> x.remove(y)
>>> train[y] = train[y].asfactor()
>>> blend[y] = blend[y].asfactor()
>>> nfolds = 3
>>> my_gbm = H2OGradientBoostingEstimator(distribution="bernoulli",
...                                       ntrees=10,
...                                       nfolds=nfolds,
...                                       fold_assignment="Modulo",
...                                       keep_cross_validation_predictions=True,
...                                       seed=1)
>>> my_gbm.train(x=x, y=y, training_frame=train)
>>> my_rf = H2ORandomForestEstimator(ntrees=50,
...                                  nfolds=nfolds,
...                                  fold_assignment="Modulo",
...                                  keep_cross_validation_predictions=True,
...                                  seed=1)
>>> my_rf.train(x=x, y=y, training_frame=train)
>>> stack_blend = H2OStackedEnsembleEstimator(base_models=[my_gbm, my_rf],
...                                           seed=1)
>>> stack_blend.train(x=x, y=y, training_frame=train, blending_frame=blend)
>>> stack_blend.model_performance(blend).auc()
""",
    export_checkpoints_dir="""
>>> from h2o.estimators.random_forest import H2ORandomForestEstimator
>>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
>>> from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator
>>> import tempfile
>>> from os import listdir
>>> higgs = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/testng/higgs_train_5k.csv")
>>> train, blend = higgs.split_frame(ratios = [.8], seed = 1234)
>>> x = train.columns
>>> y = "response"
>>> x.remove(y)
>>> train[y] = train[y].asfactor()
>>> blend[y] = blend[y].asfactor()
>>> nfolds = 3
>>> checkpoints_dir = tempfile.mkdtemp()
>>> my_gbm = H2OGradientBoostingEstimator(distribution="bernoulli",
...                                       ntrees=10,
...                                       nfolds=nfolds,
...                                       fold_assignment="Modulo",
...                                       keep_cross_validation_predictions=True,
...                                       seed=1)
>>> my_gbm.train(x=x, y=y, training_frame=train)
>>> my_rf = H2ORandomForestEstimator(ntrees=50,
...                                  nfolds=nfolds,
...                                  fold_assignment="Modulo",
...                                  keep_cross_validation_predictions=True,
...                                  seed=1)
>>> my_rf.train(x=x, y=y, training_frame=train)
>>> stack_blend = H2OStackedEnsembleEstimator(base_models=[my_gbm, my_rf],
...                                           seed=1,
...                                           export_checkpoints_dir=checkpoints_dir)
>>> stack_blend.train(x=x, y=y, training_frame=train, blending_frame=blend)
>>> len(listdir(checkpoints_dir))
""",
    keep_levelone_frame="""
>>> from h2o.estimators.random_forest import H2ORandomForestEstimator
>>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
>>> from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator
>>> higgs = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/testng/higgs_train_5k.csv")
>>> train, blend = higgs.split_frame(ratios = [.8], seed = 1234)
>>> x = train.columns
>>> y = "response"
>>> x.remove(y)
>>> train[y] = train[y].asfactor()
>>> blend[y] = blend[y].asfactor()
>>> nfolds = 3
>>> my_gbm = H2OGradientBoostingEstimator(distribution="bernoulli",
...                                       ntrees=1,
...                                       nfolds=nfolds,
...                                       fold_assignment="Modulo",
...                                       keep_cross_validation_predictions=True,
...                                       seed=1)
>>> my_gbm.train(x=x, y=y, training_frame=train)
>>> my_rf = H2ORandomForestEstimator(ntrees=50,
...                                  nfolds=nfolds,
...                                  fold_assignment="Modulo",
...                                  keep_cross_validation_predictions=True,
...                                  seed=1)
>>> my_rf.train(x=x, y=y, training_frame=train)
>>> stack_blend = H2OStackedEnsembleEstimator(base_models=[my_gbm, my_rf],
...                                           seed=1,
...                                           keep_levelone_frame=True)
>>> stack_blend.train(x=x, y=y, training_frame=train, blending_frame=blend)
>>> stack_blend.model_performance(blend).auc()
""",
    metalearner_algorithm="""
>>> from h2o.estimators.random_forest import H2ORandomForestEstimator
>>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
>>> from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator
>>> higgs = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/testng/higgs_train_5k.csv")
>>> train, blend = higgs.split_frame(ratios = [.8], seed = 1234)
>>> x = train.columns
>>> y = "response"
>>> x.remove(y)
>>> train[y] = train[y].asfactor()
>>> blend[y] = blend[y].asfactor()
>>> nfolds = 3
>>> my_gbm = H2OGradientBoostingEstimator(distribution="bernoulli",
...                                       ntrees=1,
...                                       nfolds=nfolds,
...                                       fold_assignment="Modulo",
...                                       keep_cross_validation_predictions=True,
...                                       seed=1)
>>> my_gbm.train(x=x, y=y, training_frame=train)
>>> my_rf = H2ORandomForestEstimator(ntrees=50,
...                                  nfolds=nfolds,
...                                  fold_assignment="Modulo",
...                                  keep_cross_validation_predictions=True,
...                                  seed=1)
>>> my_rf.train(x=x, y=y, training_frame=train)
>>> stack_blend = H2OStackedEnsembleEstimator(base_models=[my_gbm, my_rf],
...                                           seed=1,
...                                           metalearner_algorithm="gbm")
>>> stack_blend.train(x=x, y=y, training_frame=train, blending_frame=blend)
>>> stack_blend.model_performance(blend).auc()
""",
    metalearner_fold_assignment="""
>>> from h2o.estimators.random_forest import H2ORandomForestEstimator
>>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
>>> from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator
>>> higgs = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/testng/higgs_train_5k.csv")
>>> train, blend = higgs.split_frame(ratios = [.8], seed = 1234)
>>> x = train.columns
>>> y = "response"
>>> x.remove(y)
>>> train[y] = train[y].asfactor()
>>> blend[y] = blend[y].asfactor()
>>> nfolds = 3
>>> my_gbm = H2OGradientBoostingEstimator(distribution="bernoulli",
...                                       ntrees=1,
...                                       nfolds=nfolds,
...                                       fold_assignment="Modulo",
...                                       keep_cross_validation_predictions=True,
...                                       seed=1)
>>> my_gbm.train(x=x, y=y, training_frame=train)
>>> my_rf = H2ORandomForestEstimator(ntrees=50,
...                                  nfolds=nfolds,
...                                  fold_assignment="Modulo",
...                                  keep_cross_validation_predictions=True,
...                                  seed=1)
>>> my_rf.train(x=x, y=y, training_frame=train)
>>> stack_blend = H2OStackedEnsembleEstimator(base_models=[my_gbm, my_rf],
...                                           seed=1,
...                                           metalearner_fold_assignment="Random")
>>> stack_blend.train(x=x, y=y, training_frame=train, blending_frame=blend)
>>> stack_blend.model_performance(blend).auc()
""",
    metalearner_fold_column="""
>>> from h2o.estimators.random_forest import H2ORandomForestEstimator
>>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
>>> from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator
>>> train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/testng/higgs_train_5k.csv")
>>> test = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/testng/higgs_test_5k.csv")
>>> fold_column = "fold_id"
>>> train[fold_column] = train.kfold_column(n_folds=3, seed=1)
>>> x = train.columns
>>> y = "response"
>>> x.remove(y)
>>> x.remove(fold_column)
>>> train[y] = train[y].asfactor()
>>> test[y] = test[y].asfactor()
>>> nfolds = 3
>>> my_gbm = H2OGradientBoostingEstimator(distribution="bernoulli",
...                                       ntrees=10,
...                                       nfolds=nfolds,
...                                       fold_assignment="Modulo",
...                                       keep_cross_validation_predictions=True,
...                                       seed=1)
>>> my_gbm.train(x=x, y=y, training_frame=train)
>>> my_rf = H2ORandomForestEstimator(ntrees=50,
...                                  nfolds=nfolds,
...                                  fold_assignment="Modulo",
...                                  keep_cross_validation_predictions=True,
...                                  seed=1)
>>> my_rf.train(x=x, y=y, training_frame=train)
>>> stack = H2OStackedEnsembleEstimator(base_models=[my_gbm, my_rf],
...                                     metalearner_fold_column=fold_column,
...                                     metalearner_params=dict(keep_cross_validation_models=True))
>>> stack.train(x=x, y=y, training_frame=train)
>>> stack.model_performance().auc()
""",
    metalearner_nfolds="""
>>> from h2o.estimators.random_forest import H2ORandomForestEstimator
>>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
>>> from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator
>>> higgs = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/testng/higgs_train_5k.csv")
>>> train, blend = higgs.split_frame(ratios = [.8], seed = 1234)
>>> x = train.columns
>>> y = "response"
>>> x.remove(y)
>>> train[y] = train[y].asfactor()
>>> blend[y] = blend[y].asfactor()
>>> nfolds = 3
>>> my_gbm = H2OGradientBoostingEstimator(distribution="bernoulli",
...                                       ntrees=1,
...                                       nfolds=nfolds,
...                                       fold_assignment="Modulo",
...                                       keep_cross_validation_predictions=True,
...                                       seed=1)
>>> my_gbm.train(x=x, y=y, training_frame=train)
>>> my_rf = H2ORandomForestEstimator(ntrees=50,
...                                  nfolds=nfolds,
...                                  fold_assignment="Modulo",
...                                  keep_cross_validation_predictions=True,
...                                  seed=1)
>>> my_rf.train(x=x, y=y, training_frame=train)
>>> stack_blend = H2OStackedEnsembleEstimator(base_models=[my_gbm, my_rf],
...                                           seed=1,
...                                           metalearner_nfolds=3)
>>> stack_blend.train(x=x, y=y, training_frame=train, blending_frame=blend)
>>> stack_blend.model_performance(blend).auc()
""",
    metalearner_params="""
>>> from h2o.estimators.random_forest import H2ORandomForestEstimator
>>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
>>> from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator
>>> higgs = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/testng/higgs_train_5k.csv")
>>> train, blend = higgs.split_frame(ratios = [.8], seed = 1234)
>>> x = train.columns
>>> y = "response"
>>> x.remove(y)
>>> train[y] = train[y].asfactor()
>>> blend[y] = blend[y].asfactor()
>>> nfolds = 3
>>> gbm_params = {"ntrees" : 100, "max_depth" : 6}
>>> my_gbm = H2OGradientBoostingEstimator(distribution="bernoulli",
...                                       ntrees=1,
...                                       nfolds=nfolds,
...                                       fold_assignment="Modulo",
...                                       keep_cross_validation_predictions=True,
...                                       seed=1)
>>> my_gbm.train(x=x, y=y, training_frame=train)
>>> my_rf = H2ORandomForestEstimator(ntrees=50,
...                                  nfolds=nfolds,
...                                  fold_assignment="Modulo",
...                                  keep_cross_validation_predictions=True,
...                                  seed=1)
>>> my_rf.train(x=x, y=y, training_frame=train)
>>> stack_blend = H2OStackedEnsembleEstimator(base_models=[my_gbm, my_rf],
...                                           metalearner_algorithm="gbm",
...                                           metalearner_params=gbm_params)
>>> stack_blend.train(x=x, y=y, training_frame=train, blending_frame=blend)
>>> stack_blend.model_performance(blend).auc()
""",
    seed="""
>>> from h2o.estimators.random_forest import H2ORandomForestEstimator
>>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
>>> from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator
>>> higgs = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/testng/higgs_train_5k.csv")
>>> train, blend = higgs.split_frame(ratios = [.8], seed = 1234)
>>> x = train.columns
>>> y = "response"
>>> x.remove(y)
>>> train[y] = train[y].asfactor()
>>> blend[y] = blend[y].asfactor()
>>> nfolds = 3
>>> my_gbm = H2OGradientBoostingEstimator(distribution="bernoulli",
...                                       ntrees=1,
...                                       nfolds=nfolds,
...                                       fold_assignment="Modulo",
...                                       keep_cross_validation_predictions=True,
...                                       seed=1)
>>> my_gbm.train(x=x, y=y, training_frame=train)
>>> my_rf = H2ORandomForestEstimator(ntrees=50,
...                                  nfolds=nfolds,
...                                  fold_assignment="Modulo",
...                                  keep_cross_validation_predictions=True,
...                                  seed=1)
>>> my_rf.train(x=x, y=y, training_frame=train)
>>> stack_blend = H2OStackedEnsembleEstimator(base_models=[my_gbm, my_rf],
...                                           seed=1,
...                                           metalearner_fold_assignment="Random")
>>> stack_blend.train(x=x, y=y, training_frame=train, blending_frame=blend)
>>> stack_blend.model_performance(blend).auc()
""",
    training_frame="""
>>> from h2o.estimators.random_forest import H2ORandomForestEstimator
>>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
>>> from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator
>>> higgs = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/testng/higgs_train_5k.csv")
>>> train, valid = higgs.split_frame(ratios = [.8], seed = 1234)
>>> x = train.columns
>>> y = "response"
>>> x.remove(y)
>>> train[y] = train[y].asfactor()
>>> blend[y] = blend[y].asfactor()
>>> nfolds = 3
>>> my_gbm = H2OGradientBoostingEstimator(distribution="bernoulli",
...                                       ntrees=1,
...                                       nfolds=nfolds,
...                                       fold_assignment="Modulo",
...                                       keep_cross_validation_predictions=True,
...                                       seed=1)
>>> my_gbm.train(x=x, y=y, training_frame=train)
>>> my_rf = H2ORandomForestEstimator(ntrees=50,
...                                  nfolds=nfolds,
...                                  fold_assignment="Modulo",
...                                  keep_cross_validation_predictions=True,
...                                  seed=1)
>>> my_rf.train(x=x, y=y, training_frame=train)
>>> stack_blend = H2OStackedEnsembleEstimator(base_models=[my_gbm, my_rf],
...                                           seed=1,
...                                           metalearner_fold_assignment="Random")
>>> stack_blend.train(x=x, y=y, training_frame=train, validation_frame=valid)
>>> stack_blend.model_performance(blend).auc()
""",
    validation_frame="""
>>> from h2o.estimators.random_forest import H2ORandomForestEstimator
>>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
>>> from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator
>>> higgs = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/testng/higgs_train_5k.csv")
>>> train, valid = higgs.split_frame(ratios = [.8], seed = 1234)
>>> x = train.columns
>>> y = "response"
>>> x.remove(y)
>>> train[y] = train[y].asfactor()
>>> blend[y] = blend[y].asfactor()
>>> nfolds = 3 
>>> my_gbm = H2OGradientBoostingEstimator(distribution="bernoulli",
...                                       ntrees=1,
...                                       nfolds=nfolds,
...                                       fold_assignment="Modulo",
...                                       keep_cross_validation_predictions=True,
...                                       seed=1)
>>> my_gbm.train(x=x, y=y, training_frame=train)
>>> my_rf = H2ORandomForestEstimator(ntrees=50,
...                                  nfolds=nfolds,
...                                  fold_assignment="Modulo",
...                                  keep_cross_validation_predictions=True,
...                                  seed=1)
>>> my_rf.train(x=x, y=y, training_frame=train)
>>> stack_blend = H2OStackedEnsembleEstimator(base_models=[my_gbm, my_rf],
...                                           seed=1,
...                                           metalearner_fold_assignment="Random")
>>> stack_blend.train(x=x, y=y, training_frame=train, validation_frame=valid)
>>> stack_blend.model_performance(blend).auc()
"""
)

