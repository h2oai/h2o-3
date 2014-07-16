src <-
function() {
  warning("MAY NOT WORK ON YOUR SYSTEM -- HARD CODED PATHS")
  source("/Users/spencer/master/h2o/R/h2o-package/R/wrapper.R")
  source("/Users/spencer/master/h2o/R/h2o-package/R/constants.R")
  source("/Users/spencer/master/h2o/R/h2o-package/R/logging.R")
  source("/Users/spencer/master/h2o/R/h2o-package/R/h2o.R")
  source("/Users/spencer/master/h2o/R/h2o-package/R/exec.R")
  source("/Users/spencer/master/h2o/R/h2o-package/R/classes.R")
  source("/Users/spencer/master/h2o/R/h2o-package/R/ops.R")
  source("/Users/spencer/master/h2o/R/h2o-package/R/methods.R")
  source("/Users/spencer/master/h2o/R/h2o-package/R/ast.R")
  source("/Users/spencer/master/h2o/R/h2o-package/R/import.R")
  source("/Users/spencer/master/h2o/R/h2o-package/R/parse.R")
  source("/Users/spencer/master/h2o/R/h2o-package/R/export.R")
#  source("/Users/spencer/master/h2o/R/h2o-package/R/algorithms.R")
  require(rjson)
  require(RCurl)
}

src()
