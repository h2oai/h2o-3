\name{h2o.anomaly}
\alias{h2o.anomaly}

\title{
Anomaly Detection via H2O Deep Learning Model
}
\description{
Detect anomalies in a H2O dataset using a H2O deep learning model with auto-encoding.
}
\usage{
h2o.anomaly(data, model, key = "", threshold = -1.0)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{data}{An \code{\linkS4class{H2OParsedData}} object.}
  \item{model}{An \code{\linkS4class{H2ODeepLearningModel}} object that represents the model to be used for anomaly detection. Must have been built with the argument \code{autoencoder = TRUE} in \code{\link{h2o.deeplearning}}.}
  \item{key}{(Optional) The unique hex key assigned to the resulting dataset. If none is given, a key will automatically be generated.}
  \item{threshold}{(Optional) Threshold of reconstruction error for rows to be displayed in logs. If set to -1.0, this defaults to 10 times the MSE.}
}
\value{
Returns an \code{\linkS4class{H2OParsedData}} object with a single column containing the reconstruction MSE. 
}
\seealso{
\code{\linkS4class{H2OParsedData}, \linkS4class{H2ODeepLearningModel}, \link{h2o.deeplearning}}
}
\examples{
library(h2o)
localH2O = h2o.init()
prosPath = system.file("extdata", "prostate.csv", package = "h2o")
prostate.hex = h2o.importFile(localH2O, path = prosPath)
prostate.dl = h2o.deeplearning(x = 3:9, y = 2, data = prostate.hex, autoencoder = TRUE,
                               hidden = c(10, 10), epochs = 5)
prostate.anon = h2o.anomaly(prostate.hex, prostate.dl)
head(prostate.anon)
}
