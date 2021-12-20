rest_api_version = 3

def update_param(name, param):
    if name == 'algorithm_params':
        param['default_value'] = None
        return param
    return None  # param untouched

extensions = dict(
    required_params=['x', 'y', 'training_frame'],  # empty to override defaults in gen_defaults
    set_required_params="""
    parms$training_frame <- training_frame
    args <- .verify_dataxy(training_frame, x, y)
    if (missing(protected_columns)) { # core infogram
      if (!missing(safety_index_threshold)) {
        warning("Should not set safety_index_threshold for core infogram runs.  Set net_information_threshold instead.
            Using default of 0.1 if not set")
      }
      if (!missing(relevance_index_threshold)) {
        warning("Should not set relevance_index_threshold for core infogram runs.  Set total_information_threshold 
        instead.   Using default of 0.1 if not set")
      }
    } else { # fair infogram
      if (!missing(net_information_threshold)) {
      warning("Should not set net_information_threshold for fair infogram runs, set safety_index_threshold instead.  
        Using default of 0.1 if not set")
      }
      if (!missing(total_information_threshold)) {
        warning("Should not set total_information_threshold for fair infogram runs, set relevance_index_threshold
         instead.    Using default of 0.1 if not set")
      }
    }
    
    if( !missing(offset_column) && !is.null(offset_column))  args$x_ignore <- args$x_ignore[!( offset_column == args$x_ignore )]
    if( !missing(weights_column) && !is.null(weights_column)) args$x_ignore <- args$x_ignore[!( weights_column == args$x_ignore )]
    if( !missing(fold_column) && !is.null(fold_column)) args$x_ignore <- args$x_ignore[!( fold_column == args$x_ignore )]
    parms$ignored_columns <- args$x_ignore
    parms$response_column <- args$y
    """,

    skip_default_set_params_for=['training_frame', 'ignored_columns', 'response_column', 'max_confusion_matrix_size',
                                 "algorithm_params"],
    set_params="""
if (!missing(algorithm_params))
    parms$algorithm_params <- as.character(toJSON(algorithm_params, pretty = TRUE))
h2o.show_progress() # enable progress bar explicitly
""",
    with_model="""
# Convert algorithm_params back to list if not NULL, added after obtaining model
if (!missing(algorithm_params)) {
    model@parameters$algorithm_params <- list(fromJSON(model@parameters$algorithm_params))[[1]] #Need the `[[ ]]` to avoid a nested list
}

infogram_model <- new("H2OInfogram", model_id=model@model_id)       
model <- infogram_model                
""",
    module="""
#' Extract the admissible attributes/predictors out of the H2O Infogram Model.
#'
#' @param model an H2OInfogram.
#' @export 
h2o.get_admissible_attributes<-function(model) {
  if ( is(model, "H2OInfogram") && (model@algorithm=='infogram'))
    return(model@admissible_features)
}
"""
)
# modify this for infogram.
doc = dict(
    preamble="""
Given a protected_columns list, Infogram will add all predictors that contains information on the 
 protected predictors to the protected_columns list.  It will return a set of predictors that
 do not contain information on the sensitive/unfair list and hence user can build a fair model.  If no 
 protected_columns list is given, Infogram will return a list of core predictors that should be used to build a final model.
 Infogram can significantly cut down the number of predictors needed to build a model and hence will build a simple
 model that is more interpretable, less susceptible to overfitting, runs faster while providing similar accuracy
 as models built using all attributes.
    """,
    examples="""
    h2o.init()

    # Run infogram of CAPSULE ~ AGE + RACE + PSA + DCAPS
    prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
    prostate <- h2o.uploadFile(path = prostate_path)
    prostate$CAPSULE <- as.factor(prostate$CAPSULE)
    h2o.infogram(y="CAPSULE", x=c("RACE", "AGE", "PSA", "DCAPS"), training_frame=prostate)
    """
)
