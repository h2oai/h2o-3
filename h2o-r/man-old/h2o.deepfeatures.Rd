\name{h2o.deepfeatures}
\alias{h2o.deepfeatures}

\title{
Feature Generation via H2O Deep Learning Model
}
\description{
Extract the non-linear features from a H2O dataset using a H2O deep learning model.
}
\usage{
h2o.deepfeatures(data, model, key = "", layer = -1)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{data}{An \code{\linkS4class{H2OParsedData}} object.}
  \item{model}{An \code{\linkS4class{H2ODeepLearningModel}} object that represents the deeplearning model to be used for feature extraction.}
  \item{key}{(Optional) The unique hex key assigned to the resulting dataset. If none is given, a key will automatically be generated.}
  \item{layer}{(Optional) Index of the hidden layer to extract. If none is given, the last hidden layer is chosen.)}
}
\value{
Returns an \code{\linkS4class{H2OParsedData}} object with as many features as the number of units in the hidden layer of specified index.
If the model is supervised, and the data object has a column of the same name as the
response with which the model was trained, then the response column will be prepended to the output frame.
}
\seealso{
\code{\linkS4class{H2OParsedData}, \linkS4class{H2ODeepLearningModel}, \link{h2o.deeplearning}}
}
\examples{
library(h2o)
localH2O = h2o.init()
prosPath = system.file("extdata", "prostate.csv", package = "h2o")
prostate.hex = h2o.importFile(localH2O, path = prosPath)
prostate.dl = h2o.deeplearning(x = 3:9, y = 2, data = prostate.hex, hidden = c(100, 200),
                               epochs = 5)
prostate.deepfeatures_layer1 = h2o.deepfeatures(prostate.hex, prostate.dl, layer = 1)
prostate.deepfeatures_layer2 = h2o.deepfeatures(prostate.hex, prostate.dl, layer = 2)
head(prostate.deepfeatures_layer1)
head(prostate.deepfeatures_layer2)
}
