\name{H2OParsedDataVA-class}
\Rdversion{1.1}
\docType{class}
\alias{H2OParsedDataVA-class}
\alias{colnames,H2OParsedDataVA-method}
\alias{colnames<-,H2OParsedDataVA,character-method}
\alias{colnames<-,H2OParsedDataVA,H2OParsedDataVA-method}
\alias{dim,H2OParsedDataVA-method}
\alias{head,H2OParsedDataVA-method}
\alias{names,H2OParsedDataVA-method}
\alias{names<-,H2OParsedDataVA-method}
\alias{ncol,H2OParsedDataVA-method}
\alias{nrow,H2OParsedDataVA-method}
\alias{show,H2OParsedDataVA-method}
\alias{summary,H2OParsedDataVA-method}
\alias{tail,H2OParsedDataVA-method}

\title{Class \code{"H2OParsedDataVA"}}
\description{
A class for representing imported ValueArray data sets that have been parsed.
}
\section{Objects from the Class}{
Objects can be created by calls of the form \code{new("H2OParsedDataVA", ...)}.
%%  ~~ describe objects here ~~ 
}
\section{Slots}{
  \describe{
    \item{\code{h2o}:}{Object of class \code{"H2OClient"}, which is the client object that was passed into the function call.}
    \item{\code{key}:}{Object of class \code{"character"}, which is the hex key assigned to the imported data.}
    \item{\code{logic}:}{Object of class \code{"logical"}, indicating whether the \code{"H2OParsedDataVA"} object represents logical data}
  }
}
\section{Extends}{
Class \code{"\linkS4class{H2OParsedData}"}, directly.
}
\section{Methods}{
  \describe{
    \item{colnames}{\code{signature(x = "H2OParsedDataVA")}: ... }
    \item{colnames<-}{\code{signature(x = "H2OParsedDataVA", value = "character")}: ... }
    \item{colnames<-}{\code{signature(x = "H2OParsedDataVA", value = "H2OParsedDataVA")}: ... }
    \item{dim}{\code{signature(x = "H2OParsedDataVA")}: ... }
    \item{head}{\code{signature(x = "H2OParsedDataVA")}: ... }
    \item{names}{\code{signature(x = "H2OParsedDataVA")}: ... }
    \item{names<-}{\code{signature(x = "H2OParsedDataVA")}: ... }
    \item{ncol}{\code{signature(x = "H2OParsedDataVA")}: ... }
    \item{nrow}{\code{signature(x = "H2OParsedDataVA")}: ... }
    \item{show}{\code{signature(object = "H2OParsedDataVA")}: ... }
    \item{summary}{\code{signature(object = "H2OParsedDataVA")}: ... }
    \item{tail}{\code{signature(x = "H2OParsedDataVA")}: ... }
	 }
}
\seealso{
%%  ~~objects to See Also as \code{\link{~~fun~~}}, ~~~
%%  ~~or \code{\linkS4class{CLASSNAME}} for links to other classes ~~~
\code{\linkS4class{H2ORawDataVA}, \link{h2o.parseRaw.VA}}
}
\examples{
showClass("H2OParsedDataVA")
}
\keyword{classes}
