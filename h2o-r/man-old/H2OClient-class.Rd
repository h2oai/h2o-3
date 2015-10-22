\name{H2OClient-class}
\Rdversion{1.1}
\docType{class}
\alias{H2OClient-class}
\alias{h2o.importFile,H2OClient,character,character,logical-method}
\alias{h2o.importFolder,H2OClient,character,logical-method}
\alias{h2o.importURL,H2OClient,character,character,logical-method}
\alias{show,H2OClient-method}

\title{Class \code{"H2OClient"}}
\description{
An object representing the server/local machine on which H2O is running.
}
\section{Objects from the Class}{
Objects can be created by calls of the form \code{new("H2OClient", ...)}
%%  ~~ describe objects here ~~ 
}
\section{Slots}{
  \describe{
    \item{\code{ip}:}{Object of class \code{"character"} representing the IP address of the H2O server. }
    \item{\code{port}:}{Object of class \code{"numeric"} representing the port number of the H2O server. }
  }
}
\section{Methods}{
  \describe{
    \item{h2o.importFile}{\code{signature(object = "H2OClient", path = "character", 
    		+ key = "character", parse = "logical")}: ... }
    \item{h2o.importFolder}{\code{signature(object = "H2OClient", path = "character", 
    		+ parse = "logical")}: ... }
    \item{h2o.importURL}{\code{signature(object = "H2OClient", path = "character", 
    		+ key = "character", parse = "logical")}: ... }
    \item{show}{\code{signature(object = "H2OClient")}: ... }
	 }
}
\examples{
showClass("H2OClient")
}
\keyword{classes}
