\name{h2o.logAndEcho}
\alias{h2o.logAndEcho}
%- Also NEED an '\alias' for EACH other topic documented here.
\title{
Write and Echo Message to H2O Log
}
\description{
Write a user-defined message to the H2O Java log file and echo it back to the user.
}
\usage{
h2o.logAndEcho(conn, message)
}
%- maybe also 'usage' for other objects documented here.
\arguments{
  \item{conn}{An \code{\linkS4class{H2OClient}} object containing the IP address and port of the server running H2O.
}
  \item{message}{A character string to write to the H2O Java log file.}
}
\seealso{
\code{\linkS4class{H2OClient}, \link{h2o.downloadAllLogs}}
}
\examples{
library(h2o)
localH2O = h2o.init(ip = "localhost", port = 54321, startH2O = TRUE)
h2o.logAndEcho(localH2O, "Test log and echo method.")
h2o.shutdown(localH2O)
}
% Add one or more standard keywords, see file 'KEYWORDS' in the
% R documentation directory.
\keyword{ ~kwd1 }
\keyword{ ~kwd2 }% __ONLY ONE__ keyword per line
