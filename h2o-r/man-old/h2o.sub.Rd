\name{h2o.sub}
\alias{h2o.sub}
\title{Pattern Replacement}
\description{ \code{h2o.sub}, a method for the \code{\link{sub}} base method.}
\usage{h2o.sub(pattern, replacement, x, ignore.case)}
\arguments{
 \item{pattern}{A regex or string to match on.}
 \item{replacement}{A string that replaces the matched pattern.}
  \item{x}{An \code{\linkS4class{H2OParsedData}} object with a single factor column.}
  \item{ignore.case}{If TRUE, case will be ignored in the pattern match}
  }
\details{
  Matches a pattern and replaces first instance of the
  matched pattern with the replacement string. Differs from 
  h2o.gsub that does a global substitution for all instances 
  of the matched pattern.
}
\value{
An object of class "H2OParsedData".
}

\examples{
library(h2o)
localH2O <- h2o.init(ip = "localhost", port = 54321, startH2O = TRUE)
df <- data.frame(
    V1 = c("HELLO WoR@&^LD", "the dOg ATE", "my friENd BOb Ace", "mEow meOW"),
    V2 = c(92318, 34891.123, 21,99))
hex <- as.h2o(localH2O, df)
h2o.sub("HELLO", "WHY HELLO THERE", hex$V1)
}
