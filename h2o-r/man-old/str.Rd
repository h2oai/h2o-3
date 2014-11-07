\name{str}
\alias{str.H2OParsedData}
\title{
Display the Structure of a H2O Dataset
}
\description{
A method for the \code{\link{str}} generic. Obtain information about H2O parsed data objects and their structure. 
}
\usage{
\method{str}{H2OParsedData}(object, ...)
}
\arguments{
  \item{object}{An \code{\linkS4class{H2OParsedData}} object.}
  \item{...}{Potential further arguments. (Currently unimplemented).}
}
\value{
A table listing summary information including variable names, types (for example, enum or numeric), count of observations and columns.
}

\examples{
library(h2o)
localH2O = h2o.init()
prosPath = system.file("extdata", "prostate.csv", package="h2o")
prostate.hex = h2o.importFile(localH2O, path = prosPath)
str(prostate.hex)
}
