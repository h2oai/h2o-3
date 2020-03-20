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
    te_model_id=dict(
        setter="""
assert_is_type(te_model_id, None, H2OTargetEncoderEstimator)
self._parms["te_model_id"] = te_model_id.key if isinstance(te_model_id, H2OTargetEncoderEstimator) else te_model_id
""")
)

doc = dict(
    te_model_id="""    Type: ``str`` | ``H2OTargetEncoderEstimator``.
"""
)
