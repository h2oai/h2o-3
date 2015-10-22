\name{h2o.month}
\alias{h2o.month}
\alias{month}
\alias{month.H2OParsedData}
%- Also NEED an '\alias' for EACH other topic documented here.
\title{
Convert Milliseconds to Months in H2O Dataset
}
\description{
Converts the entries of a \code{\linkS4class{H2OParsedData}} object from milliseconds to months (on a 0 to 11 scale).
}
\usage{
h2o.month(x)

\method{month}{H2OParsedData}(x)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{x}{
An \code{\linkS4class{H2OParsedData}} object.
}
}
\details{
This method calls the functions of the MutableDateTime class in Java.
}
\value{
A \code{\linkS4class{H2OParsedData}} object containing the entries of \code{x} converted to months of the year.
}
\seealso{
\code{\link{h2o.year}}
}
