extensions = dict(
    required_params=['x', 'y', 'training_frame', 'random_columns', 'group_column'],
    set_required_params="""
    parms$training_frame <- training_frame
    args <- .verify_dataxy(training_frame, x, y)
    if (!missing(random_columns)) {
        parms$random_columns <- random_columns
    } else {
    stop("random_columns is required.")
    }
    if (!missing(group_column) {
        parms$group_column <- group_column
    } else {
        stop("group_column is required.")
    }
    parms$ignored_columns <- args$x_ignore
    parms$response_column <- args$y
    """,
    module="""
#' Extracts the fixed coefficients of the HGLM model.
#'
#' @param model is a H2O HGLM model with algorithm name of hglm
#' @export
h2o.get_fixed_coefs <- function(model) {
    if (is(model, "H2OModel") && (model@algorithm=="hglm"))
        return(model@model$beta)
}
    """
)

doc = dict(
    preamble="""
    Fits a HGLM model with both the residual noise and random effect being modeled by Gaussian distribution.  The fixed
    effect coefficients are specified in parameter x, the random effect coefficients are specified in parameter 
    random_columns.  The column specified in group_column will contain the level 2 index value and must be an enum column.
    """,
    examples="""
    library(h2o)
    h2o.init()
    # build a HGLM model with prostate dataset
    prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
    prostate <- h2o.uploadFile(path = prostate_path)
    prostate$CAPSULE <- as.factor(prostate$CAPSULE)
    prostate$RACE <- as.factor(prostate$RACE)
    model <- h2o.hglm(y="VOL", x=c("AGE","RACE","DPROS"), random_columns = ["AGE"], group_column = "RACE", training_frame=prostate)
    """
)
