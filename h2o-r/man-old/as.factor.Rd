\name{as.factor}
\alias{as.factor}
\title{Converts a column from numeric to factor}
\description{ 
	Specify a column type to be factor (also called categorical or enumerative), rather than numeric.
	}
\usage{as.factor(x)}

\arguments{
  \item{x}{A column in an object of class \code{\linkS4class{H2OParsedData}}, or data frame.}
}
\value{
Returns the original object of class \code{\linkS4class{H2OParsedData}}, with the requested column specified as a factor, rather than numeric. 
}
\examples{
library(h2o)
localH2O = h2o.init()
prosPath = system.file("extdata", "prostate.csv", package="h2o")
prostate.hex = h2o.importFile(localH2O, path = prosPath)
prostate.hex[,4] = as.factor(prostate.hex[,4])
summary(prostate.hex)
}
