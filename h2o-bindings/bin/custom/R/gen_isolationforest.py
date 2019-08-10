def update_param(name, param):
    if name == 'stopping_metric':
        param['values'] = ['AUTO', 'anomaly_score']
        return param
    return None  # param untouched


extensions = dict(
    required_params=dict(training_frame=None, x=None),
    validate_required_params="",
    set_required_params="""
parms$training_frame <- training_frame
if(!missing(x))
  parms$ignored_columns <- .verify_datacols(training_frame, x)$cols_ignore
""",
)

doc = dict(
    preamble="""
Trains an Isolation Forest model
""",
    params=dict(
        x="""A vector containing the \code{character} names of the predictors in the model."""
    ),
)
