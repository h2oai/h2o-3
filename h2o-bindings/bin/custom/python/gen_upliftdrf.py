options = dict(
    verbose=True,
    model_extensions=[
        'h2o.model.extensions.VariableImportance',
    ],
)


def update_param(name, param):
    if name == 'stopping_metric':
        param['values'] = ['AUTO', 'AUUC', 'ATE', 'ATT', 'ATC', 'qini']
        return param
    if name == 'distribution':
        param['values'] = ['AUTO', 'bernoulli']
        return param
    return None  # param untouched


doc = dict(
    __class__="""
Build a Uplift Random Forest model

Builds a Uplift Random Forest model on an H2OFrame.
"""
)
