\name{zzz_ShutdownAfterExamples}
\title{
Shutdown H2O cloud after examples run (for H2O developers only)
}
\description{
\code{zzz_ShutdownAfterExamples}, shutdown H2O cloud after examples run.  This is only relevant for H2O developers during the building of the CRAN package.
}

\examples{
# -- CRAN examples begin --
library(h2o)
localH2O = h2o.init()
h2o.shutdown(localH2O, prompt = FALSE)
Sys.sleep(2)
# -- CRAN examples end --
}
