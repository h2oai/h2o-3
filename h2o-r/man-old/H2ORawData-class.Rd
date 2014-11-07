\name{H2ORawData-class}
\Rdversion{1.1}
\docType{class}
\alias{H2ORawData-class}
\alias{show,H2ORawData-method}

\title{Class \code{"H2ORawData"}}
\description{
A class for representing imported data sets that have not been parsed.
}
\section{Objects from the Class}{
Objects can be created by calls of the form \code{new("H2ORawData", ...)}.
%%  ~~ describe objects here ~~ 
}
\section{Slots}{
  \describe{
    \item{\code{h2o}:}{Object of class \code{"H2OClient"}, which is the client object that was passed into the function call.}
    \item{\code{key}:}{Object of class \code{"character"}, which is the hex key assigned to the imported data.}
  }
}
\section{Methods}{
  \describe{
  \item{h2o.parseRaw}{\code{signature(data = "H2OParsedData", key = "character", header = "logical" sep = "character", col.names = "H2OParsedData")}: ... }
    \item{show}{\code{signature(object = "H2ORawData")}: ... }
    }
}
\seealso{
%%  ~~objects to See Also as \code{\link{~~fun~~}}, ~~~
%%  ~~or \code{\linkS4class{CLASSNAME}} for links to other classes ~~~
\code{\linkS4class{H2OParsedData}}
}
\examples{
showClass("H2ORawData")
}
\keyword{classes}
