\name{colnames}
\alias{colnames}
\title{ Returns column names for a parsed H2O data object.}
\description{Returns column names for an \code{\linkS4class{H2OParsedData} object.}}
\usage{
colnames(x, do.NULL = TRUE, prefix = "col")
}
\arguments{
  \item{x}{ An\code{\linkS4class{H2OParsedData}} object.}
  \item{do.NULL}{ Logical value. If \code{FALSE} and names are \code{NULL}, names are created.}
  \item{prefix}{ Character string denoting prefix for created column names.}
}

\value{Returns a vector of column names. 
}
\examples{
library(h2o)
localH2O = h2o.init()
irisPath = system.file("extdata", "iris.csv", package="h2o")
iris.hex = h2o.importFile(localH2O, path = irisPath, key = "iris.hex")
summary(iris.hex)
colnames(iris.hex)
}

