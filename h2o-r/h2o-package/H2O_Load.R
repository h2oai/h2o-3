# Change this global variable to match your own system's path
ROOT.PATH <- "/Users/spencer/master/h2o/R/h2o-package/R/"
src <-
function() {
  warning("MAY NOT WORK ON YOUR SYSTEM -- **TRY TO CHANGE `ROOT.PATH`!**")
  to_src <- c("wrapper.R", "constants.R", "logging.R", "h2o.R", "exec.R", "classes.R", "ops.R", "methods.R", "ast.R", "import.R", "parse.R", "export.R")
  require(rjson); require(RCurl)
  invisible(lapply(to_src,function(x){source(paste(ROOT.PATH, x, sep = ""))}))
}
src()