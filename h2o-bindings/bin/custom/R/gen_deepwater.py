
extensions = dict(
    set_required_params="""
parms$training_frame <- training_frame
args <- .verify_dataxy(training_frame, x, y, autoencoder)
if( !missing(offset_column) && !is.null(offset_column))  args$x_ignore <- args$x_ignore[!( offset_column == args$x_ignore )]
if( !missing(weights_column) && !is.null(weights_column)) args$x_ignore <- args$x_ignore[!( weights_column == args$x_ignore )]
if( !missing(fold_column) && !is.null(fold_column)) args$x_ignore <- args$x_ignore[!( fold_column == args$x_ignore )]
parms$ignored_columns <- args$x_ignore
parms$response_column <- args$y
""",
    module="""
#' Determines whether Deep Water is available
#'
#' Ask the H2O server whether a Deep Water model can be built. (Depends on availability of native backends.)
#' Returns TRUE if a Deep Water model can be built, or FALSE otherwise.
#' @param h2oRestApiVersion (Optional) Specific version of the REST API to use.
#' @export
h2o.deepwater.available <- function(h2oRestApiVersion = .h2o.__REST_API_VERSION) {
    res <- .h2o.__remoteSend(method = "GET",
                             h2oRestApiVersion = h2oRestApiVersion,
                             page = .h2o.__MODEL_BUILDERS("deepwater"))
    visibility <- res$model_builders[["deepwater"]][["visibility"]]
    if (visibility == "Experimental") {
        print("Cannot build a Deep Water model - no backend found.")
        available <- FALSE
    } else {
        available <- TRUE
    }
    return(available)
}
""",
)

doc = dict(
    preamble="""
Build a Deep Learning model using multiple native GPU backends

Builds a deep neural network on an H2OFrame containing various data sources.
""",
    params=dict(
        seed="""
Seed for random numbers (affects certain parts of the algo that are stochastic and those might or might not be enabled by default).
Note: only reproducible when running single threaded.
Defaults to -1 (time-based random number).
""",
    )
)
