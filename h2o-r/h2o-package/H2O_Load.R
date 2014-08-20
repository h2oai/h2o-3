# Change this global variable to match your own system's path
ROOT.PATH <- "/Users/spencer/0xdata/h2o-dev/h2o-r/h2o-package/R/"

src <-
function() {
  warning("MAY NOT WORK ON YOUR SYSTEM -- **TRY TO CHANGE `ROOT.PATH`!**")
  to_src <- c("wrapper.R", "constants.R", "logging.R", "h2o.R", "exec.R", "classes.R", "ops.R", "methods.R", "ast.R", "import.R", "parse.R", "export.R", "models.R", "algorithms.R")
  require(rjson); require(RCurl)
  invisible(lapply(to_src,function(x){source(paste(ROOT.PATH, x, sep = ""))}))
}
src()

h <- h2o.init()
hex <- as.h2o(h, iris)
