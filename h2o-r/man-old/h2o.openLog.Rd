\name{h2o.openLog}
\alias{h2o.openLog}
%- Also NEED an '\alias' for EACH other topic documented here.
\title{
View H2O R Logs
}
\description{
Open existing logs of H2O R POST commands and error responses on local disk. Used primarily for debugging purposes.
}
\usage{
h2o.openLog(type)
}
\arguments{
  \item{type}{Which log file to open. Either \code{"Command"} for POST commands sent between R and H2O, or \code{"Error"} for errors returned by H2O in the HTTP response.}
}

\seealso{
\code{\link{h2o.startLogging}, \link{h2o.stopLogging}, \link{h2o.clearLogs}, \link{h2o.getLogPath}, \link{h2o.setLogPath}}
}
\examples{
\dontrun{
# Skip running this to avoid windows being opened during R CMD check
library(h2o)
localH2O = h2o.init()

h2o.startLogging()
ausPath = system.file("extdata", "australia.csv", package="h2o")
australia.hex = h2o.importFile(localH2O, path = ausPath)
h2o.stopLogging()

h2o.openLog("Command")
h2o.openLog("Error")
}
}
