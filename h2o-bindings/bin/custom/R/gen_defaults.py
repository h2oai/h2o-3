def update_param(name, param):
    if name == 'distribution':
        values = param['values']
        param['values'] = [v for v in values if v not in ['custom', 'ordinal', 'quasibinomial']]
        return param
    elif name == 'stopping_metric':
        param['values'].remove('anomaly_score')
        return param
    elif name in ['ignored_columns', 'response_column', 'max_confusion_matrix_size']:
        return {}  # will skip this schema param
    return None  # means no change


extensions = dict(
    required_params=dict(x=None, y=None, training_frame=None),
    frame_params=dict(training_frame=True, validation_frame=False),
    validate_required_params="""
# If x is missing, then assume user wants to use all columns as features.
if (missing(x)) {
   if (is.numeric(y)) {
       x <- setdiff(col(training_frame), y)
   } else {
       x <- setdiff(colnames(training_frame), y)
   }
}
""",
    set_required_params="""
parms$training_frame <- training_frame
args <- .verify_dataxy(training_frame, x, y)
if( !missing(offset_column) && !is.null(offset_column))  args$x_ignore <- args$x_ignore[!( offset_column == args$x_ignore )]
if( !missing(weights_column) && !is.null(weights_column)) args$x_ignore <- args$x_ignore[!( weights_column == args$x_ignore )]
if( !missing(fold_column) && !is.null(fold_column)) args$x_ignore <- args$x_ignore[!( fold_column == args$x_ignore )]
parms$ignored_columns <- args$x_ignore
parms$response_column <- args$y
""",
    skip_default_set_params_for=['training_frame', 'ignored_columns', 'response_column', 'max_confusion_matrix_size'],
)

doc = dict(
    params=dict(
        x="""
(Optional) A vector containing the names or indices of the predictor variables to use in building the model.
If x is missing, then all columns except y are used.
""",
        y="""
The name or column index of the response variable in the data. 
The response must be either a numeric or a categorical/factor variable. 
If the response is numeric, then a regression model will be trained, otherwise it will train a classification model.
""",
        seed="""
Seed for random numbers (affects certain parts of the algo that are stochastic and those might or might not be enabled by default).
Defaults to -1 (time-based random number).
""",
        ignored_columns=None,
        response_column=None,
        max_confusion_matrix_size=None,
    )
)
