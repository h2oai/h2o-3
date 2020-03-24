extensions = dict(
    required_params=['x', 'y', 'training_frame', 'gam_X'],  # empty to override defaults in gen_defaults
    validate_required_params="""
    # If x is missing, then assume user wants to use all columns as features.
    if (missing(x)) {
       if (is.numeric(y)) {
           x <- setdiff(col(training_frame), y)
       } else {
           x <- setdiff(colnames(training_frame), y)
       }
    }

    # If gam_X is missing, then assume user wants to use all columns as features for GAM.
    if (missing(gam_X)) {
        gam_X <- x
    }
    """,
    set_required_params="""
    parms$training_frame <- training_frame
    args <- .verify_dataxy(training_frame, x, y)
    if( !missing(offset_column) && !is.null(offset_column))  args$x_ignore <- args$x_ignore[!( offset_column == args$x_ignore )]
    if( !missing(weights_column) && !is.null(weights_column)) args$x_ignore <- args$x_ignore[!( weights_column == args$x_ignore )]
    if( !missing(fold_column) && !is.null(fold_column)) args$x_ignore <- args$x_ignore[!( fold_column == args$x_ignore )]
    parms$ignored_columns <- args$x_ignore
    parms$response_column <- args$y
    parms$gam_X <- gam_X
    """,
)
