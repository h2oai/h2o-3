\name{H2ONBModel-class}
\Rdversion{1.1}
\docType{class}
\alias{H2ONBModel-class}
\alias{show,H2ONBModel-method}

\title{Class \code{"H2ONBModel"}}
\description{
A class for representing naive Bayes models.
}
\section{Objects from the Class}{
Objects can be created by calls of the form \code{new("H2ONBModel", ...)}.
%%  ~~ describe objects here ~~ 
}
\section{Slots}{
  \describe{
    \item{\code{key}:}{Object of class \code{"character"}, representing the unique hex key that identifies the model.}
    \item{\code{data}:}{Object of class \code{\linkS4class{H2OParsedData}}, which is the input data used to build the model.}
    \item{\code{model}:}{Object of class \code{"list"} containing the following elements:
    \itemize{
        \item{\code{laplace}: A positive number controlling Laplace smoothing. The default (0) disables Laplace smoothing.}
        \item{\code{levels}: Categorical levels of the dependent variable.}
        \item{\code{apriori}: Total occurrences of each level of the dependent variable.}
        \item{\code{apriori_prob}: A-priori class distribution for the dependent variable.}
        \item{\code{tables}: A list of tables, one for each predictor variable. For categorical predictors, the table displays, for each attribute level, the conditional probabilities given the target class. For numeric predictors, the table gives, for each target class, the mean and standard deviation of the variable.}
        }
    }
  }
}
\section{Extends}{
Class \code{"\linkS4class{H2OModel}"}, directly.
}
\section{Methods}{
  \describe{
    \item{show}{\code{signature(object = "H2ONBModel")}: ... }
	 }
}
\seealso{
%%  ~~objects to See Also as \code{\link{~~fun~~}}, ~~~
%%  ~~or \code{\linkS4class{CLASSNAME}} for links to other classes ~~~
\code{\link{h2o.naiveBayes}}
}
\examples{
showClass("H2ONBModel")
}
\keyword{classes}
