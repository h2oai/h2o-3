
extensions = dict(
    required_params=['x', 'y', 'training_frame', 'gam_columns'],  # empty to override defaults in gen_defaults
    validate_required_params="""
    # If x is missing, then assume user wants to use all columns as features.
    if (missing(x)) {
       if (is.numeric(y)) {
           x <- setdiff(col(training_frame), y)
       } else {
           x <- setdiff(colnames(training_frame), y)
       }
    }

    # If gam_columns is missing, then assume user wants to use all columns as features for GAM.
    if (missing(gam_columns)) {
        stop("Columns indices to apply to GAM must be specified. If there are none, please use GLM.")
    }
    """,
    set_required_params="""
    parms$training_frame <- training_frame
    args <- .verify_dataxy(training_frame, x, y)
    if( !missing(offset_column) && !is.null(offset_column))  args$x_ignore <- args$x_ignore[!( offset_column == args$x_ignore )]
    if( !missing(weights_column) && !is.null(weights_column)) args$x_ignore <- args$x_ignore[!( weights_column == args$x_ignore )]
    if( !missing(fold_column) && !is.null(fold_column)) args$x_ignore <- args$x_ignore[!( fold_column == args$x_ignore )]
    if( !missing(gam_columns) && !is.null(gam_columns)) args$x_ignore <- args$x_ignore[!( args$x_ignore %in% gam_columns )]
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
