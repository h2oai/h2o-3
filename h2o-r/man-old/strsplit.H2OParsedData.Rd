\name{strsplit.H2OParsedData}
\alias{strsplit.H2OParsedData}
\title{Split the Elements of a Character Vector}
\description{ \code{strsplit.H2OParsedData}, a method for the \code{\link{strsplit}} base method. Obtain and
returns an \code{\linkS4class{H2OParsedData}} object.}
\usage{\method{strsplit}{H2OParsedData}(x, split, fixed, perl, useBytes)}
\arguments{
  \item{x}{An \code{\linkS4class{H2OParsedData}} object with a single factor column.}
  \item{split}{A non-empty string. Can be a regular expression.}
  \item{fixed}{ Used by the base method. Ignored by H2OParsedData strsplit. }
  \item{perl}{ Used by the base method. Ignored by H2OParsedData strsplit. }
  \item{useBytes}{ Used by the base method. Ignored by H2OParsedData strsplit. }
  }
\details{
Splits the given factor column on the input split. If split is '', then an error will be thrown. The default is to
split on whitespace.
}
\value{
An object of class "H2OParsedData".
}

\examples{
library(h2o)
localH2O <- h2o.init(ip = "localhost", port = 54321, startH2O = TRUE)
df <- data.frame(
    V1 = c("hello world", "the dog ate", "my friend Bob Ace", "meow meow"),
    V2 = c(92318, 34891.123, 21,99))
hex <- as.h2o(localH2O, df)
strsplit(hex$V1)  # split on ' '
}
