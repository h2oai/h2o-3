\name{h2o.confusionMatrix}
\alias{h2o.confusionMatrix}

\title{
Build a Confusion Matrix from H2O Classification Predictions 
}
\description{
Constructs a confusion matrix from a column of predicted responses and a column of actual (reference) responses in H2O. Note that confusion matrices describe prediciton errors for classification data only. 
}
\usage{
h2o.confusionMatrix(data, reference)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{data}{An \code{\linkS4class{H2OParsedData}} object that represents the predicted response values. (Must be a single column).}
  \item{reference}{An \code{\linkS4class{H2OParsedData}} object that represents the actual response values. Must have the same dimensions as \code{data}.}
}
\value{
Returns a confusion matrix with the actual value counts along the rows and the predicted value counts along the columns.
}
\seealso{
\code{\linkS4class{H2OParsedData}}
}
\examples{
library(h2o)
localH2O = h2o.init()
prosPath = system.file("extdata", "prostate.csv", package="h2o")
prostate.hex = h2o.importFile(localH2O, path = prosPath)
prostate.gbm = h2o.gbm(x = 3:9, y = 2, data = prostate.hex)
prostate.pred = h2o.predict(prostate.gbm)
h2o.confusionMatrix(prostate.pred[,1], prostate.hex[,2])
}
