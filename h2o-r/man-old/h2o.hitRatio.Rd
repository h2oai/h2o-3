\name{h2o.hitRatio}
\alias{h2o.hitRatio}

\title{
Compute Hit Ratio from H2O Classification Predictions 
}
\description{
Compute the hit ratios from a prediction dataset and a column of actual (reference) responses in H2O. The hit ratio is the percentage of instances where the actual class of an observation is in the top k classes predicted by the model, where k is specified by the user. Note that the hit ratio can only be calculated for classification models.
}
\usage{
h2o.hitRatio(prediction, reference, k = 10, seed = 0)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{prediction}{An \code{\linkS4class{H2OParsedData}} object that represents the predicted response values. Must have the same number of rows as \code{reference}.}
  \item{reference}{An \code{\linkS4class{H2OParsedData}} object that represents the actual response values. (Must be a single column).}
  \item{k}{A positive integer indicating the maximum number of labels to use for hit ratio computation. Cannot be larger than the size of the response domain.}
  \item{seed}{(Optional) Random number seed for breaking ties between equal probabilities.}
}
\value{
Returns a numeric vector with the hit ratio for every level in the reference domain. 
}
\seealso{
\code{\linkS4class{H2OParsedData}}
}
\examples{
library(h2o)
localH2O = h2o.init()
irisPath = system.file("extdata", "iris.csv", package = "h2o")
iris.hex = h2o.importFile(localH2O, path = irisPath)
iris.gbm = h2o.gbm(x = 1:4, y = 5, data = iris.hex)
iris.pred = h2o.predict(iris.gbm)
h2o.hitRatio(iris.pred, iris.hex[,5], k = 3)
}
