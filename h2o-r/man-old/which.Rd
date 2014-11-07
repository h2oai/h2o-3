\name{which}
\alias{which}
\title{Return the row numbers for which the condition is true}
\description{ \code{which}, a method for the \code{\link{which}} base method. }
\usage{ which(x, arr.ind = FALSE, useNames = TRUE) }
\arguments{
  \item{x}{An \code{\linkS4class{H2OParsedData}} object}
  \item{arr.ind}{Ignored}
  \item{useNames}{Ignored}
  }
\details{
Similar to R's \code{\link{which}}.
}

\examples{
library(h2o)
localH2O = h2o.init()
hex <- as.h2o(localH2O, iris)
which(hex[,5] == "setosa")
}
