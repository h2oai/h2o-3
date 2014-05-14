\name{head}
\alias{head.H2OParsedData}
\alias{tail.H2OParsedData}
\alias{head.H2OParsedDataVA}
\alias{tail.H2OParsedDataVA}
\title{
Return the First or Last Part of a H2O Dataset
}
\description{
Returns the first or last rows of an H2O parsed data object.
}
\usage{
\method{head}{H2OParsedData}(x, n = 6L, \dots)
\method{tail}{H2OParsedData}(x, n = 6L, \dots)
\method{head}{H2OParsedDataVA}(x, n = 6L, \dots)
\method{tail}{H2OParsedDataVA}(x, n = 6L, \dots)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{x}{An H2O parsed data object.
  }
  \item{n}{(Optional) A single integer. If positive, number of rows in \code{x} to return. If negative, all but the \code{n} first/last number of rows in \code{x}.
  }
  \item{\dots}{Arguments to be passed to or from other methods. (Currently unimplemented).
  }
}

\value{
A data frame containing the first or last \code{n} rows of an \code{\linkS4class{H2OParsedData}} object.
}

\examples{
library(h2o)
localH2O = h2o.init(ip = "localhost", port = 54321, startH2O = TRUE)
ausPath = system.file("extdata", "australia.csv", package="h2o")
australia.hex = h2o.importFile(localH2O, path = ausPath)
head(australia.hex, 10)
tail(australia.hex, 10)
h2o.shutdown(localH2O)
}
% Add one or more standard keywords, see file 'KEYWORDS' in the
% R documentation directory.
\keyword{ ~kwd1 }
\keyword{ ~kwd2 }% __ONLY ONE__ keyword per line
