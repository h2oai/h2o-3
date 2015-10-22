\name{as.matrix.H2OParsedData}
\alias{as.matrix.H2OParsedData}
\title{Converts a parsed H2O object to a matrix.}
\description{Convert an \code{\linkS4class{H2OParsedData}} object to a matrix, which allows subsequent data frame operations within the R environment.}
\usage{\method{as.matrix}{H2OParsedData}(x, \dots)}

\arguments{
  \item{x}{ An \code{\linkS4class{H2OParsedData}} object.}
  \item{\dots}{Additional arguments to be passed to or from methods.}
 }
\value{
Returns a matrix in the R environment. Note that this call establishes the data set in the R environment, and subsequent operations on the matrix take place within R, not H2O. When data are large, users may experience significant slowdown.
}

\examples{
library(h2o)
localH2O = h2o.init()
prosPath = system.file("extdata", "prostate.csv", package="h2o")
prostate.hex = h2o.importFile(localH2O, path = prosPath)
prostate.matrix <- as.matrix(prostate.hex)
summary(prostate.matrix)
head(prostate.matrix)
}
