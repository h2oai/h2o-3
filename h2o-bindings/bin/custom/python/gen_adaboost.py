def update_param(name, param):
    if name == 'weak_learner_params':
        param['type'] = 'KeyValue'
        param['default_value'] = None
        return param
    return None  # param untouched

extensions = dict(
    __imports__="""
import ast
import json
from h2o.estimators.estimator_base import H2OEstimator
from h2o.exceptions import H2OValueError
from h2o.frame import H2OFrame
from h2o.utils.typechecks import assert_is_type, Enum, numeric
""",
)

doc = dict(
    __class__="""
Builds an AdaBoost model
"""
)

overrides = dict(
    weak_learner_params=dict(
        getter="""
if self._parms.get("{sname}") != None:
    return json.loads(self._parms.get("{sname}"))
else:
    self._parms["{sname}"] = None
        """,
        setter="""
assert_is_type({pname}, None, {ptype})
if {pname} is not None and {pname} != "":
    for k in {pname}:
        weak_learner_params[k] = weak_learner_params[k]
    self._parms["{sname}"] = str(json.dumps({pname}))
else:
    self._parms["{sname}"] = None
"""
    )
)

examples = dict(
    weak_learner_params="""
>>> prostate_hex = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv")
>>> prostate_hex["CAPSULE"] = prostate_hex["CAPSULE"].asfactor()
>>> response = "CAPSULE"
>>> seed = 42
>>> adaboost_model = H2OAdaBoostEstimator(seed=seed,
...                                       weak_learner="DRF",
...                                       weak_learner_params={'ntrees':1,'max_depth':3})
>>> adaboost_model.train(y=response,
...                 ignored_columns=["ID"],
...                 training_frame=prostate_hex)
>>> print(adaboost_model)
""",
)
