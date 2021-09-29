extensions = dict(
    set_required_params="""
parms$training_frame <- training_frame
args <- .verify_dataxy(training_frame, x, y)
parms$ignored_columns <- args$x_ignore
parms$response_column <- args$y
""",
    module="""
#' Extract and return ANOVA Table as an H2OFrame
#' @param model an H2OANOVAGLM.
#' @export 
h2o.resultFrame <- function(model) {
  if (is(model, "H2OModel") && (model@algorithm=='anovaglm')) 
    return(h2o.getFrame(model@model$result_frame_key$name))
}
"""
)

doc = dict(
    preamble="""
    H2O ANOVAGLM is used to calculate Type III SS which is used to evaluate the contributions of individual predictors 
    and their interactions to a model.  Predictors or interactions with negligible contributions to the model will have 
    high p-values while those with more contributions will have low p-values. 
    """,
    examples="""
    h2o.init()

    # Run ANOVA GLM of VOL ~ CAPSULE + RACE
    prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
    prostate <- h2o.uploadFile(path = prostate_path)
    prostate$CAPSULE <- as.factor(prostate$CAPSULE)
    model <- h2o.anovaglm(y = "VOL", x = c("CAPSULE","RACE"), training_frame = prostate)
    """
)
