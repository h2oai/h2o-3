# Change this global variable to match your own system's path
ROOT.PATH <- "/Users/anqi_fu/Documents/workspace/"
DEV.PATH  <- "h2o-dev/h2o-r/h2o-package/R/"
FULL.PATH <- paste(ROOT.PATH, DEV.PATH, sep="")

src <-
function() {
  warning("MAY NOT WORK ON YOUR SYSTEM -- **TRY TO CHANGE `ROOT.PATH`!**")
  to_src <- c("classes.R", "connection.R", "constants.R", "logging.R", "communication.R", "kvstore.R", "exec.R", "ops.R", "frame.R", "ast.R", "astfun.R", "import.R", "parse.R", "export.R", "models.R", "edicts.R", "glm.R", "pca.R", "kmeans.R", "gbm.R", "deeplearning.R", "locate.R")
  require(rjson); require(RCurl)
  invisible(lapply(to_src,function(x){source(paste(FULL.PATH, x, sep = ""))}))
}
src()


h <- h2o.init()
#hex <- as.h2o(iris, h)

hex <- h2o.importFile(h, paste(ROOT.PATH, "h2o-dev/smalldata/logreg/prostate.csv", sep = ""))
