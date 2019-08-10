extensions = dict(
    required_params=dict(training_frame="NULL"),
    validate_frames="""
# training_frame is required if pre_trained frame is not specified
if (missing(pre_trained) && missing(training_frame)) stop(\"argument \'training_frame\' is missing, with no default\")
training_frame <- .validate.H2OFrame(training_frame)
pre_trained <- .validate.H2OFrame(pre_trained)
""",
    validate_required_params="",
    set_required_params="""
parms$training_frame <- training_frame
""",
)

doc = dict(
    preamble="""
Trains a word2vec model on a String column of an H2O data frame
""",
)
