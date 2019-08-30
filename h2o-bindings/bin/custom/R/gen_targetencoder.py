extensions = dict(
    required_params=['training_frame', "target_column", "encoded_columns"],  # empty to override defaults in gen_defaults,
    validate_required_params="""
    if(missing(training_frame)) stop("Training frame must be specified.")
    if(missing(target_column)) stop("Target column must be specified.")
    if(missing(encoded_columns)) stop("Encoded columns must be specified.")
    """,
    set_required_params="""
parms$response_column <- target_column
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
