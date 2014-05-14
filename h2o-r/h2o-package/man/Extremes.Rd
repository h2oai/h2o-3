\name{Extremes}
\alias{max}
\alias{min}
%- Also NEED an '\alias' for EACH other topic documented here.
\title{
Maxima and Minima
}
\description{
Calculates the (parallel) minimum of the input values. This method extends the \code{\link{min}} generic to deal with \code{\linkS4class{H2OParsedData}} objects.
}
\usage{
max(\dots, na.rm = FALSE)
min(\dots, na.rm = FALSE)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{\dots}{Numeric, character or \code{\linkS4class{H2OParsedData}} arguments. 
  }
  \item{na.rm}{Logical value where FALSE does not remove NA's in the calculation and TRUE removes NA's in the calculation.
  }
}

\value{
Returns the maximum or minimum over all the input arguments. For a \code{\linkS4class{H2OParsedData}} object, the function is calculated over all entries in the dataset. An error will occur if any of those entries is non-numeric.
}

\examples{
library(h2o)
localH2O = h2o.init(ip = "localhost", port = 54321, startH2O = TRUE)
ausPath = system.file("extdata", "australia.csv", package = "h2o")
australia.hex = h2o.importFile(localH2O, path = ausPath, key = "australia.hex")
min(australia.hex)
h2o.shutdown(localH2O)
}
% Add one or more standard keywords, see file 'KEYWORDS' in the
% R documentation directory.
\keyword{ ~kwd1 }
\keyword{ ~kwd2 }% __ONLY ONE__ keyword per line
