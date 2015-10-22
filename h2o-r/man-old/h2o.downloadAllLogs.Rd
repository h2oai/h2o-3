\name{h2o.downloadAllLogs}
\alias{h2o.downloadAllLogs}
%- Also NEED an '\alias' for EACH other topic documented here.
\title{
Download H2O Log Files to Disk
}
\description{
Download all H2O log files to local disk. Generally used for debugging purposes.
}
\usage{
h2o.downloadAllLogs(client, dirname = ".", filename = NULL)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{client}{An \code{\linkS4class{H2OClient}} object containing the IP address and port of the server running H2O.
}
  \item{dirname}{(Optional) A character string indicating the directory that the log file should be saved in.}
  \item{filename}{(Optional) A character string indicating the name that the log file should be saved to.}
}
\seealso{
\code{\linkS4class{H2OClient}}
}
\examples{
library(h2o)
localH2O = h2o.init()
h2o.downloadAllLogs(localH2O, dirname = getwd(), filename = "h2o_logs.log")
file.info(paste(getwd(), "h2o_logs.log", sep = .Platform$file.sep))
file.remove(paste(getwd(), "h2o_logs.log", sep = .Platform$file.sep))
}
