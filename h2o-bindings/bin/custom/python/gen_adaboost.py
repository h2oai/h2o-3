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
