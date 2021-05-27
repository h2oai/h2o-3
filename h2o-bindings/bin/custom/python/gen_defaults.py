def update_param(name, param):
    if name == 'distribution':
        values = param['values']
        param['values'] = [v for v in values if v not in ['custom', 'ordinal', 'quasibinomial']]
        return param
    elif name == 'stopping_metric':
        param['values'].remove('anomaly_score')
        return param
    return None  # means no change


# extra logic common to several algos
overrides = dict(
    checkpoint=dict(
        setter="""
assert_is_type(checkpoint, None, str, H2OEstimator)
key = checkpoint.key if isinstance(checkpoint, H2OEstimator) else checkpoint
self._parms["{sname}"] = key
"""
    ),
)

