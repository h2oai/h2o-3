extensions = dict(
    required_params=['training_frame', "x", "y"],
    set_required_params="""
args <- .verify_dataxy(training_frame, x, y)
if( !missing(fold_column) && !is.null(fold_column)) args$x_ignore <- args$x_ignore[!( fold_column == args$x_ignore )]
parms$ignored_columns <- args$x_ignore
parms$response_column <- args$y
parms$training_frame <- training_frame
    """
)


doc = dict(
    preamble="""
 Transformation of a categorical variable with a mean value of the target variable
""",
    examples="""
# library(h2o)
# h2o.init()
#
# Create a target encoder
# target_encoder <- h2o.targetencoder(training_frame = data, encoded_columns= encoded_columns,
# target_column = "survived", fold_column = "pclass", data_leakage_handling = "KFold")
#
# Apply the Target Encoder transformation
# encoded_data <- h2o.transform(target_encoder, data)
"""
)
