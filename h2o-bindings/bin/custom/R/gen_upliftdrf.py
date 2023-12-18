extensions = dict(
    extra_params=[('verbose', 'FALSE')],
    required_params=['x', 'y', 'training_frame', 'treatment_column'],
    skip_default_set_params_for=['training_frame', 'ignored_columns', 'response_column', 'max_confusion_matrix_size',
                                 'distribution', 'treatment_column'],
    set_params="""
if (!missing(distribution)) {
  warning("The only bernoulli distribution is supported for Uplift Random Forest.")
  parms$distribution <- 'bernoulli'
}
""",
    set_required_params="""
parms$training_frame <- training_frame
args <- .verify_dataxy(training_frame, x, y)
if (!missing(treatment_column)) {
  parms$treatment_column <- treatment_column
} else {
  stop("Treatment column is required.")  
}
parms$ignored_columns <- args$x_ignore
parms$response_column <- args$y
"""   
)



doc = dict(
    preamble="""
Build a Uplift Random Forest model

Builds a Uplift Random Forest model on an H2OFrame.
""",
    params=dict(
        verbose="""
\code{Logical}. Print scoring history to the console (Metrics per tree). Defaults to FALSE.
"""
    ),
    returns="""
Creates a \linkS4class{H2OModel} object of the right type.
""",
    seealso="""
\code{\link{predict.H2OModel}} for prediction
""",
    examples=""""""
)
