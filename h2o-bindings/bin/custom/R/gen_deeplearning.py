extensions = dict(
    extra_params=[('verbose', 'FALSE')],
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
#' Anomaly Detection via H2O Deep Learning Model
#'
#' Detect anomalies in an H2O dataset using an H2O deep learning model with
#' auto-encoding.
#'
#' @param object An \linkS4class{H2OAutoEncoderModel} object that represents the
#'        model to be used for anomaly detection.
#' @param data An H2OFrame object.
#' @param per_feature Whether to return the per-feature squared reconstruction error
#' @return Returns an H2OFrame object containing the
#'         reconstruction MSE or the per-feature squared error.
#' @seealso \code{\link{h2o.deeplearning}} for making an H2OAutoEncoderModel.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' prostate_path = system.file("extdata", "prostate.csv", package = "h2o")
#' prostate = h2o.importFile(path = prostate_path)
#' prostate_dl = h2o.deeplearning(x = 3:9, training_frame = prostate, autoencoder = TRUE,
#'                                hidden = c(10, 10), epochs = 5)
#' prostate_anon = h2o.anomaly(prostate_dl, prostate)
#' head(prostate_anon)
#' prostate_anon_per_feature = h2o.anomaly(prostate_dl, prostate, per_feature = TRUE)
#' head(prostate_anon_per_feature)
#' }
#' @export
h2o.anomaly <- function(object, data, per_feature=FALSE) {
  url <- paste0('Predictions/models/', object@model_id, '/frames/',h2o.getId(data))
  res <- .h2o.__remoteSend(url, method = "POST", reconstruction_error=TRUE, reconstruction_error_per_feature=per_feature)
  key <- res$model_metrics[[1L]]$predictions$frame_id$name
  h2o.getFrame(key)
}
""",
)

doc = dict(
    preamble="""
Build a Deep Neural Network model using CPUs

Builds a feed-forward multilayer artificial neural network on an H2OFrame.
""",
    params=dict(
        seed="""
Seed for random numbers (affects certain parts of the algo that are stochastic and those might or might not be enabled by default).
Note: only reproducible when running single threaded.
Defaults to -1 (time-based random number).
""",
        verbose="""
\code{Logical}. Print scoring history to the console (Metrics per epoch). Defaults to FALSE.
"""
    ),
    seealso="""
\code{\link{predict.H2OModel}} for prediction
""",
    examples="""
library(h2o)
h2o.init()
iris_hf <- as.h2o(iris)
iris_dl <- h2o.deeplearning(x = 1:4, y = 5, training_frame = iris_hf, seed=123456)

# now make a prediction
predictions <- h2o.predict(iris_dl, iris_hf)
"""
)
