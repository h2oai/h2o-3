\name{toupper.H2OParsedData}
\alias{toupper.H2OParsedData}
\title{Transform Elements of a Character Vector Into Upper Case}
\description{ \code{toupper.H2OParsedData}, a method for the \code{\link{toupper}} base method. Obtain and
returns an \code{\linkS4class{H2OParsedData}} object.}
\usage{\method{toupper}{H2OParsedData}(x)}
\arguments{
  \item{x}{An \code{\linkS4class{H2OParsedData}} object with a single factor column.}
  }
\details{
  Converts alphabetic characters from lower to upper case in the English locale.
  Non-alphabetic characters are left unchanged.
}
\value{
An object of class "H2OParsedData".
}

\examples{
library(h2o)
localH2O <- h2o.init(ip = "localhost", port = 54321, startH2O = TRUE)
df <- data.frame(
  V1 = c("HellO WoR@&^LD", "the dOg ATE", "my friENd BOb Ace", "mEow meOW"),
  V2 = c(92318, 34891.123, 21,99))
hex <- as.h2o(localH2O, df)
toupper(hex$V1)
}
