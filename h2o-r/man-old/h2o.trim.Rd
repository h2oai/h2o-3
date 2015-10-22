\name{trim}
\alias{trim}
\title{Trim the leading and trailing white space.}
\description{ \code{h2o.trim}, a method for removing leading and trailing white space. }
\usage{ trim(x) }
\arguments{
  \item{x}{An \code{\linkS4class{H2OParsedData}} object with a single factor column.}
  }
\details{
Remove trailing and leading white space.
}

\examples{
library(h2o)
localH2O = h2o.init()
fr <- data.frame(
    x = c("   asdfhuash ", "  # a    ", "hello    "),
    y = c(1,2,3)
)
hex <- as.h2o(localH2O, fr)
trim(hex$x)
}
