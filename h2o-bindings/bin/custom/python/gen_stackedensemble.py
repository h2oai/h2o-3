rest_api_version = 99


def update_param(name, param):
    if name == 'metalearner_params':
        param['type'] = 'KeyValue'
        param['default_value'] = None
        return param
    return None  # param untouched


extensions = dict(
    __imports__="""
from h2o.utils.shared_utils import quoted
from h2o.utils.typechecks import is_type
import json
import ast
""",
    __class__="""
def metalearner(self):
    \"""Print the metalearner of an H2OStackedEnsembleEstimator.\"""
    model = self._model_json["output"]
    if "metalearner" in model and model["metalearner"] is not None:
        return model["metalearner"]
    print("No metalearner for this model")

def levelone_frame_id(self):
    \"""Fetch the levelone_frame_id for an H2OStackedEnsembleEstimator.\"""
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
    blending_frame = H2OFrame._validate(blending_frame, 'blending_frame', required=False)

    def extend_parms(parms):
        if blending_frame is not None:
            parms['blending_frame'] = blending_frame
        if self.metalearner_fold_column is not None:
            parms['ignored_columns'].remove(quoted(self.metalearner_fold_column))

    super(self.__class__, self)._train(x, y, training_frame,
                                       extend_parms_fn=extend_parms,
                                       **kwargs)
"""
)

overrides = dict(
    base_models=dict(
        setter="""
if is_type(base_models, [H2OEstimator]):
    {pname} = [b.model_id for b in {pname}]
    self._parms["{sname}"] = {pname}
else:
    assert_is_type({pname}, None, {ptype})
    self._parms["{sname}"] = {pname}
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
>>> col_types = ["numeric", "numeric", "numeric", "enum", "enum", "numeric", "numeric", "numeric", "numeric"]
>>> data = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/prostate/prostate.csv", col_types=col_types)
>>> train, test = data.split_frame(ratios=[.8], seed=1)
>>> x = ["CAPSULE","GLEASON","RACE","DPROS","DCAPS","PSA","VOL"]
>>> y = "AGE"
>>> nfolds = 5
>>> my_gbm = H2OGradientBoostingEstimator(nfolds=nfolds, fold_assignment="Modulo", keep_cross_validation_predictions=True)
>>> my_gbm.train(x=x, y=y, training_frame=train)
>>> my_rf = H2ORandomForestEstimator(nfolds=nfolds, fold_assignment="Modulo", keep_cross_validation_predictions=True)
>>> my_rf.train(x=x, y=y, training_frame=train)
>>> stack = H2OStackedEnsembleEstimator(model_id="my_ensemble", training_frame=train, validation_frame=test, base_models=[my_gbm.model_id, my_rf.model_id])
>>> stack.train(x=x, y=y, training_frame=train, validation_frame=test)
>>> stack.model_performance()
""",
    metalearner_params="""
    >>> metalearner_params = {'max_depth': 2, 'col_sample_rate': 0.3}
""",
)
