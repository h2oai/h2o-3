\name{h2o.getLogPath}
\alias{h2o.getLogPath}
%- Also NEED an '\alias' for EACH other topic documented here.
\title{
Get Path Where H2O R Logs are Saved
}
\description{
Get the file path where H2O R command and error response logs are currently being saved.
}
\usage{
h2o.getLogPath(type)
}
\arguments{
  \item{type}{Which log file's path to get. Either \code{"Command"} for POST commands sent between R and H2O, or \code{"Error"} for errors returned by H2O in the HTTP response.}
}

\seealso{
\code{\link{h2o.startLogging}, \link{h2o.stopLogging}, \link{h2o.clearLogs}, \link{h2o.openLog}, \link{h2o.setLogPath}}
}
\examples{
library(h2o)
h2o.getLogPath("Command")
h2o.getLogPath("Error")
}
