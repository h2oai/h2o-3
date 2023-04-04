extensions = dict(
    required_params=['x', 'y', 'training_frame', 'gam_columns'],  # empty to override defaults in gen_defaults
    validate_required_params="""
    # If x is missing, no predictors will be used.  Only the gam columns are present as predictors
    if (missing(x)) {
        x = NULL
    }
    # If gam_columns is missing, then assume user wants to use all columns as features for GAM.
    if (missing(gam_columns)) {
        stop("Columns indices to apply to GAM must be specified. If there are none, please use GLM.")
    }
    gam_columns <- lapply(gam_columns, function(x) if(is.character(x) & length(x) == 1) list(x) else x)
    """,
    set_required_params="""
    parms$training_frame <- training_frame
    args <- .verify_dataxy(training_frame, x, y)
    if( !missing(offset_column) && !is.null(offset_column))  args$x_ignore <- args$x_ignore[!( offset_column == args$x_ignore )]
    if( !missing(weights_column) && !is.null(weights_column)) args$x_ignore <- args$x_ignore[!( weights_column == args$x_ignore )]
    if( !missing(fold_column) && !is.null(fold_column)) args$x_ignore <- args$x_ignore[!( fold_column == args$x_ignore )]
    parms$ignored_columns <- args$x_ignore
    parms$response_column <- args$y
    parms$gam_columns <- gam_columns
    """,
    with_model="""
    model@model$coefficients <- model@model$coefficients_table[,2]
    names(model@model$coefficients) <- model@model$coefficients_table[,1]
    if (!(is.null(model@model$random_coefficients_table))) {
        model@model$random_coefficients <- model@model$random_coefficients_table[,2]
        names(model@model$random_coefficients) <- model@model$random_coefficients_table[,1]
    }
    """,
    validate_params="""
    # if (!is.null(beta_constraints)) {
    #     if (!inherits(beta_constraints, 'data.frame') && !is.H2OFrame(beta_constraints))
    #       stop(paste('`beta_constraints` must be an H2OH2OFrame or R data.frame. Got: ', class(beta_constraints)))
    #     if (inherits(beta_constraints, 'data.frame')) {
    #       beta_constraints <- as.h2o(beta_constraints)
    #     }
    # }
    if (inherits(beta_constraints, 'data.frame')) {
      beta_constraints <- as.h2o(beta_constraints)
    }
    """,
    skip_default_set_params_for=['training_frame', 'ignored_columns', 'response_column', 'max_confusion_matrix_size',
                                 "interactions", "nfolds", "beta_constraints", "missing_values_handling"],
    set_params="""
    if( !missing(interactions) ) {
      # interactions are column names => as-is
      if( is.character(interactions) )       parms$interactions <- interactions
      else if( is.numeric(interactions) )    parms$interactions <- names(training_frame)[interactions]
      else stop(\"Don't know what to do with interactions. Supply vector of indices or names\")
    }
    # For now, accept nfolds in the R interface if it is 0 or 1, since those values really mean do nothing.
    # For any other value, error out.
    # Expunge nfolds from the message sent to H2O, since H2O doesn't understand it.
    if (!missing(nfolds) && nfolds > 1)
      parms$nfolds <- nfolds
    if(!missing(beta_constraints))
      parms$beta_constraints <- beta_constraints
      if(!missing(missing_values_handling))
        parms$missing_values_handling <- missing_values_handling
    """,
    module="""
#' Extracts the knot locations from model output if it is enabled.
#'
#' @param model is a H2OModel with algorithm name of gam
#' @param gam_column will only extract the knot locations for the specific gam_columns.  Else, return all.
#' @export 
h2o.get_knot_locations <- function(model, gam_column=NULL) {
    if (!model@allparameters$store_knot_locations) {
        stop("knot locations are not available, please set store_knot_locations to TRUE")
    }
    if (is.null(gam_column)) {
        return(model@model$knot_locations)
    }
    gam_columns <- model@model$gam_knot_column_names
    if (gam_column %in% gam_columns) {
        return(model@model$knot_locations[which(gam_columns==gam_column)])
    } else {
        stop(paste(gam_column, "is not a valid gam column", sep=" "))
    }
}

#' Extracts the gam column names corresponding to the knot locations from model output if it is enabled.
#'
#' @param model is a H2OModel with algorithm name of gam
#' @export 
h2o.get_gam_knot_column_names <- function(model) {
    if (!model@allparameters$store_knot_locations) {
        stop("knot locations are not available, please set store_knot_locations to TRUE")
    }
    return(model@model$gam_knot_column_names)

}
    
    .h2o.fill_gam <- function(model, parameters, allparams) {
        if (is.null(model$scoring_history))
            model$scoring_history <- model$glm_scoring_history
        if (is.null(model$model_summary))
            model$model_summary <- model$glm_model_summary
        return(model)
    }

"""
)

doc = dict(
    preamble="""
    Fit a General Additive Model

    Creates a generalized additive model, specified by a response variable, a set of predictors, and a
    description of the error distribution.
    """,
    examples="""
    h2o.init()

    # Run GAM of CAPSULE ~ AGE + RACE + PSA + DCAPS
    prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
    prostate <- h2o.uploadFile(path = prostate_path)
    prostate$CAPSULE <- as.factor(prostate$CAPSULE)
    h2o.gam(y = "CAPSULE", x = c("RACE"), gam_columns = c("PSA"),
         training_frame = prostate, family = "binomial")
    """
)
