\name{sum}
\alias{sum}
%- Also NEED an '\alias' for EACH other topic documented here.
\title{
Sum of Numeric Values
}
\description{
Calculates the sum of all the values present in its arguments. This method extends the \code{\link{sum}} generic to deal with \code{\linkS4class{H2OParsedData}} objects.
}
\usage{
sum(\dots, na.rm = FALSE)
}
\arguments{
  \item{\dots}{Numeric, complex, logical or \code{\linkS4class{H2OParsedData}} arguments. 
  }
  \item{na.rm}{Logical value where FALSE does not remove NA's in the calculation and TRUE removes NA's in the calculation.
  }
}

\value{ Returns the sum over all the input arguments. For a \code{\linkS4class{H2OParsedData}} object, the sum is taken over all entries in the dataset. An error will occur if any of those entries is non-numeric.
}

\examples{
library(h2o)
localH2O = h2o.init()
ausPath = system.file("extdata", "australia.csv", package="h2o")
australia.hex = h2o.importFile(localH2O, path = ausPath, key = "australia.hex")
sum(australia.hex)
sum(c(400, 1234, -1250), TRUE, australia.hex[,1:4])
}
