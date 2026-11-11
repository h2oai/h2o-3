extensions = dict(
    extra_params=[('verbose', 'FALSE')],
    required_params=['x', 'y', 'training_frame', 'id_column', 'response_column'],
    skip_default_set_params_for=['training_frame', 'ignored_columns', 'response_column', 'offset_column'],
    set_required_params="""
parms$training_frame <- training_frame
args <- .verify_dataxy(training_frame, x, y)
if (!missing(id_column)) {
  parms$id_column <- id_column
} else {
  stop("ID column is required.")  
}
parms$ignored_columns <- args$x_ignore
parms$response_column <- args$y
"""
)


doc = dict(
    preamble="""
Build a KNN model

Builds a K-nearest neighbour model on an H2OFrame.
""",
    params=dict(
        verbose="""
\code{Logical}. Print scoring history to the console. Defaults to FALSE.
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
