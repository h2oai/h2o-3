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
    if (!missing(group_column)) {
        parms$group_column <- group_column
    } else {
        stop("group_column is required.")
    }
    parms$ignored_columns <- args$x_ignore
    parms$response_column <- args$y
    """,
    module="""
#' Extracts the random effects coefficients of an HGLM model.
#'
#' @param model is a H2O HGLM model.
#' @export
h2o.coef_random <- function(model) {
    if (is(model, "H2OModel") && (model@algorithm=="hglm"))
        return(model@model$ubeta)
}

#' Extracts the group_column levels of an HGLM model.  The group_column is usually referred to as level 2 predictor.
#'
#' @param model is a H2O HGLM model.
#' @export
h2o.level_2_names <- function(model) {
    if (is(model, "H2OModel") && (model@algorithm=="hglm"))
        return(model@model$group_column_names)
}

#' Extracts the coefficient names of random effect coefficients.
#'
#' @param model is a H2O HGLM model.
#' @export
h2o.coefs_random_names <- function(model) {
    if (is(model, "H2OModel") && (model@algorithm=="hglm"))
        return(model@model$random_coefficient_names)
}

#' Extracts scoring history of validation dataframe during training
#'
#' @param model is a H2O HGLM model.
#' @export
h2o.scoring_history_valid <- function(model) {
    if (is(model, "H2OModel") && (model@algorithm=="hglm"))
        return(model@model$scoring_history_valid)
}

#' Extracts scoring history of training dataframe during training
#'
#' @param model is a H2O HGLM model.
#' @export
h2o.scoring_history <- function(model) {
    if (is(model, "H2OModel") && (model@algorithm=="hglm"))
        return(model@model$scoring_history)
}

#' Extracts T matrix which is the covariance of random effect coefficients.
#'
#' @param model is a H2O HGLM model.
#' @export
h2o.matrix_T <- function(model) {
    if (is(model, "H2OModel") && (model@algorithm=="hglm"))
        return(model@model$tmat)
}

#' Extracts the variance of residuals of the HGLM model.
#'
#' @param model is a H2O HGLM model.
#' @export
h2o.residual_variance <- function(model) {
    if (is(model, "H2OModel") && (model@algorithm=="hglm"))
        return(model@model$residual_variance)
}

#' Extracts the ICC of the HGLM model.
#'
#' @param model is a H2O HGLM model.
#' @export
h2o.icc <- function(model) {
    if (is(model, "H2OModel") && (model@algorithm=="hglm"))
        return(model@model$icc)
}

#' Extracts the mean residual error taking into account only the fixed effect coefficients.
#'
#' @param model is a H2O HGLM model.
#' @param train is true for training and false for validation dataset
#' @export
h2o.mean_residual_fixed <- function(model, train=TRUE) {
    if (is(model, "H2OModel") && (model@algorithm=="hglm")) {
        if (train)
            return(model@model$mean_residual_fixed)
        else
           return(model@model$mean_residual_fixed_valid) 
    }
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
    prostate$RACE <- as.factor(prostate$RACE)
    model <- h2o.hglm(y="VOL", x=c("AGE","RACE","DPROS"), random_columns = ["AGE"], 
                      group_column = "RACE", training_frame=prostate)
    """
)
