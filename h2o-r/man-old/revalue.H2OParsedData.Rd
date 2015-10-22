\name{Revalue.H2OParsedData}
\alias{revalue.H2OParsedData}
\title{
Replace specified values with new values, in a factor or character vector.
}
\description{
\code{revalue} If x is a factor, the named levels of the factor will be replaced with the new values.
}
\usage{
\method{revalue}{H2OParsedData}(x, replace = NULL, warn_missing = TRUE)
}
\arguments{
  \item{x}{factor or character vector to modify}
  \item{replace}{named character vector, with new values as values, and old values as names. If NULL, then no replacement is performed.}
  \item{warn_missing}{print a message if any of the old values are not actually present in x}
}

\details{
This function works only on character vectors and factors,
but the related mapvalues function works on vectors of any type and factors,
and instead of a named vector specifying the original and replacement values, it takes two separate vectors
}

\examples{
library(h2o)
localH2O = h2o.init()
iris.hex <- as.h2o(localH2O, iris)

# display current factor levels
levels(iris.hex$Species)

revalue(iris.hex$Species, c(setosa = "A", versicolor = "B", virginica = "C"))

# display new levels
levels(iris.hex$Species)

}
