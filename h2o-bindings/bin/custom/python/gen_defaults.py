def update_param(name, param):
    if name == 'distribution':
        values = param['values']
        param['values'] = [v for v in values if v not in ['custom', 'ordinal', 'quasibinomial']]
        return param
    elif name == 'stopping_metric':
        param['values'].remove('anomaly_score')
        return param
    return None  # means no change


overrides = dict(
    te_model=dict(
        setter="""
assert_is_type(te_model, None, str, H2OTargetEncoderEstimator)
self._parms["te_model"] = te_model.key if isinstance(te_model, H2OTargetEncoderEstimator) else te_model
""")
)

doc = dict(
    te_model="""    Type: ``str`` | ``H2OTargetEncoderEstimator``.
"""
)
