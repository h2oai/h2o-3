\name{unique.H2OParsedData}
\alias{unique.H2OParsedData}
\alias{h2o.unique}
\title{
Extract Unique Elements from H2O Dataset
}
\description{
\code{unique.H2OParsedData}, a method for the \code{\link{unique}} generic. Returns a H2O dataset like \code{x} but with duplicate elements/rows removed.
}
\usage{
\method{unique}{H2OParsedData}(x, incomparables = FALSE, \dots)

h2o.unique(x, incomparables = FALSE, \dots)
}
\arguments{
  \item{x}{An \code{\linkS4class{H2OParsedData}} object.}
  \item{incomparables}{A vector of values that cannot be compared, or \code{FALSE} which indicates all values can be compared. (Currently unimplemented).}
  \item{\dots}{Potential further arguments. (Currently only partially unimplemented).}
}
\details{
Only \code{MARGIN = 2} is currently supported, that is, dropping duplicate rows in a H2O dataset. This method runs on top of ddply in H2O.
}
\value{
An \code{\linkS4class{H2OParsedData}} with the same columns, but all duplicate rows removed. 
}

\examples{
library(h2o)
localH2O = h2o.init()
prosPath = system.file("extdata", "prostate.csv", package="h2o")
prostate.hex = h2o.importFile(localH2O, path = prosPath)
nrow(prostate.hex$AGE)
prosAge.uniq = unique(prostate.hex$AGE)
nrow(prosAge.uniq)
head(prosAge.uniq)
}
