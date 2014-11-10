\name{quantile.H2OParsedData}
\alias{quantile.H2OParsedData}
\title{Obtain and display quantiles for H2O parsed data.}
\description{ \code{quantile.H2OParsedData}, a method for the \code{\link{quantile}} generic. Obtain and return quantiles for an \code{\linkS4class{H2OParsedData}} object.}
\usage{\method{quantile}{H2OParsedData}(x, probs = seq(0, 1, 0.25), na.rm = FALSE, names = TRUE, type = 7, ...)}
\arguments{
  \item{x}{An \code{\linkS4class{H2OParsedData}} object with a single numeric column.}
  \item{probs}{numeric vector of probabilities with values in [0,1].}
  \item{na.rm}{logical; if true, any NA and NaN's are removed from x before the quantiles are computed.}
  \item{names}{logical; if true, the result has a names attribute.}
  \item{type}{integer selecting the quantile algorithm to use. Currently, only type 7 (linear interpolation) is supported.}
  \item{...}{further arguments passed to or from other methods.}
}
\details{
Note that H2O parsed data objects can be quite large, and are therefore often distributed across multiple nodes in an H2O cluster. As a result, percentiles at the 1st, 5th, 10th, 25th, 33, 50, 66, 75, 90, 95, 99th, and other values cannot be returned. This range includes the 1st quantile at the 25th percentile, median at the 50th percentile, and 3rd quantile at the 75th percentile. 
}
\value{
A vector describing the percentiles at the given cutoffs for the \code{\linkS4class{H2OParsedData}} object.  
}

\examples{
# Request quantiles for an H2O parsed data set: 
library(h2o)
localH2O = h2o.init()
prosPath = system.file("extdata", "prostate.csv", package="h2o")
prostate.hex = h2o.importFile(localH2O, path = prosPath)

# Request quantiles for a subset of columns in an H2O parsed data set 
quantile(prostate.hex[,3])
for(i in 1:ncol(prostate.hex))
  quantile(prostate.hex[,i])
}
