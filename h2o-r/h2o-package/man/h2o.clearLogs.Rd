\name{h2o.clearLogs}
\alias{h2o.clearLogs}
%- Also NEED an '\alias' for EACH other topic documented here.
\title{
Delete All H2O R Logs
}
\description{
Clear all H2O R command and error response logs from local disk. Used primarily for debugging purposes.
}
\usage{
h2o.clearLogs()
}
\seealso{
\code{\link{h2o.startLogging}, \link{h2o.stopLogging}, \link{h2o.openLog}, \link{h2o.getLogPath}, \link{h2o.setLogPath}}
}
\examples{
library(h2o)
localH2O = h2o.init(ip = "localhost", port = 54321, startH2O = TRUE)
h2o.startLogging()
ausPath = system.file("extdata", "australia.csv", package="h2o")
australia.hex = h2o.importFile(localH2O, path = ausPath)
h2o.stopLogging()
h2o.clearLogs()
h2o.shutdown(localH2O)
}
% Add one or more standard keywords, see file 'KEYWORDS' in the
% R documentation directory.
\keyword{ ~kwd1 }
\keyword{ ~kwd2 }% __ONLY ONE__ keyword per line
