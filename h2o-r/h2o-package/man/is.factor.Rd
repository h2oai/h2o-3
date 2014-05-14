\name{is.factor}
\alias{is.factor}

\title{Tells user if given column is categorical data or not. 
}
\description{Tells user if given column is categorical data or not. 
}
\usage{
is.factor(x)
}
\arguments{
  \item{x}{
Columns of an H2O parsed data object. 
}
}

\value{
A logical value TRUE if column contains categorical data, FALSE otherwise. 
}

\examples{
library(h2o)
localH2O = h2o.init(ip = "localhost", port = 54321, startH2O = TRUE)
prosPath = system.file("extdata", "prostate.csv", package="h2o")
prostate.hex = h2o.importFile(localH2O, path = prosPath)
prostate.hex[,4]=as.factor(prostate.hex[,4])
is.factor(prostate.hex[,4])
is.factor(prostate.hex[,3])
h2o.shutdown(localH2O)
}
% Add one or more standard keywords, see file 'KEYWORDS' in the
% R documentation directory.
\keyword{ ~kwd1 }
\keyword{ ~kwd2 }% __ONLY ONE__ keyword per line
