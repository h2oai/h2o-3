\name{ifelse}
\alias{ifelse}
%- Also NEED an '\alias' for EACH other topic documented here.
\title{
Applies conditional statements to an \code{\linkS4class{H2OParsedData} object. 
}}
\description{
Applies conditional statements to numeric vectors in H2O parsed data objects when the data are numeric.
}
\usage{
ifelse(test, yes, no)
}

\arguments{
  \item{test}{A logical description of the condition to be met (>, <, =, etc...)
}
  \item{yes}{The value to return if the condition is TRUE.
}
  \item{no}{The value to return if the condition is FALSE. 
}
}
\details{
Only numeric values can be tested, and only numeric results can be returned for either condition. Categorical data is not currently supported for this funciton and returned values cannot be categorical in nature. 
}
\value{
Retruns a vector of new values matching the conditions stated in the ifelse call. 
}

\examples{
library(h2o)
localH2O = h2o.init()
ausPath = system.file("extdata", "australia.csv", package="h2o")
australia.hex = h2o.importFile(localH2O, path = ausPath)
australia.hex[,9] <- ifelse(australia.hex[,3] < 279.9, 1, 0)
summary(australia.hex)
}

