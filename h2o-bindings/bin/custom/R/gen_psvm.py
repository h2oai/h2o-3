extensions = dict(
    set_required_params="""
parms$training_frame <- training_frame
args <- .verify_dataxy(training_frame, x, y)
parms$ignored_columns <- args$x_ignore
parms$response_column <- args$y
"""
)

doc = dict(
    preamble="""
Trains a Support Vector Machine model on an H2O dataset

Alpha version. Supports only binomial classification problems. 
""",
)
