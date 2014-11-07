\name{h2o.mse}
\alias{h2o.mse}

\title{
Returns the mean squared error from H2O Classification Predictions 
}
\description{
Returns the mean squared error calculated froma column of predicted responses and a column of actual (reference) responses in H2O.
}
\usage{
h2o.mse(data, reference)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{data}{An \code{\linkS4class{H2OParsedData}} object that represents the predicted response values. (Must be a single column).}
  \item{reference}{An \code{\linkS4class{H2OParsedData}} object that represents the actual response values. Must have the same dimensions as \code{data}.}
}
\value{
Returns the mean squared error as a continuous real numeric.
}
\seealso{
\code{\linkS4class{H2OParsedData}}
}
\examples{
library(h2o)
localH2O = h2o.init()
prosPath = system.file("extdata", "prostate.csv", package="h2o")
prostate.hex = h2o.importFile(localH2O, path = prosPath)
prostate.glm = prostate.glm = h2o.glm(x = c("RACE","PSA","DCAPS"), y = "AGE", 
  data = prostate.hex, family = "gaussian", nfolds = 10, alpha = 0.5)
prostate.pred = h2o.predict(prostate.glm)
h2o.mse(prostate.pred[,1], prostate.hex[,2])
}
