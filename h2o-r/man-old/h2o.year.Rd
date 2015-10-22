\name{h2o.year}
\alias{h2o.year}
\alias{year}
\alias{year.H2OParsedData}
%- Also NEED an '\alias' for EACH other topic documented here.
\title{
Convert Milliseconds to Years in H2O Dataset
}
\description{
Converts the entries of a \code{\linkS4class{H2OParsedData}} object from milliseconds to years, indexed starting from 1900.
}
\usage{
h2o.year(x)

\method{year}{H2OParsedData}(x)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{x}{
An \code{\linkS4class{H2OParsedData}} object
}
}
\details{
This method calls the functions of the MutableDateTime class in Java.
}
\value{
A \code{\linkS4class{H2OParsedData}} object containing the entries of \code{x} converted to years starting from 1900, e.g. 69 corresponds to the year 1969.
}
\seealso{
\code{\link{h2o.month}}
}
