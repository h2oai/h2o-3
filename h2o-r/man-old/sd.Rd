\name{sd}
\alias{sd}
%- Also NEED an '\alias' for EACH other topic documented here.
\title{
Standard Deviation of a Numeric Column of H2O Data
}
\description{
Calculates the standard deviation of a \code{\linkS4class{H2OParsedData}} column of continuous real valued data. 
}
\usage{
sd(x, na.rm = FALSE)
}
\arguments{
  \item{x}{An \code{\linkS4class{H2OParsedData}} object containing numeric data. 
}
  \item{na.rm}{Logical value where FALSE does not remove NA's in the calculation and TRUE removes NA's in the calculation. 
}
}

\value{ Returns a vector of values of the standard deviations for the requested columns. 
}

\examples{
library(h2o)
localH2O = h2o.init()
irisPath = system.file("extdata", "iris.csv", package="h2o")
iris.hex = h2o.importFile(localH2O, path = irisPath, key = "iris.hex")
sd(iris.hex[,4])
}

