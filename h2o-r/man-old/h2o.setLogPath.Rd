\name{h2o.setLogPath}
\alias{h2o.setLogPath}
%- Also NEED an '\alias' for EACH other topic documented here.
\title{
Set Path Where H2O R Logs are Saved
}
\description{
Set the file path where H2O R command and error response logs are currently being saved.
}
\usage{
h2o.setLogPath(path, type)
}
\arguments{
  \item{path}{A character string indicating the new file path where logs should be saved.}
  \item{type}{Which log file's path to modify. Either \code{"Command"} for POST commands sent between R and H2O, or \code{"Error"} for errors returned by H2O in the HTTP response.}
}

\seealso{
\code{\link{h2o.startLogging}, \link{h2o.stopLogging}, \link{h2o.clearLogs}, \link{h2o.openLog}, \link{h2o.getLogPath}}
}
\examples{
library(h2o)
h2o.getLogPath("Command")
h2o.setLogPath(getwd(), "Command")
h2o.getLogPath("Command")
}
