# Change this global variable to match your own system's path
ANQIS.ROOT.PATH <- "/Users/anqi_fu/Documents/workspace/h2o-3"
ANQIS.WIN.PATH <- "C:/Users/Anqi/Documents/Work/h2o-3"
SPENCERS.ROOT.PATH <- "/Users/spencer/0xdata/h2o-dev"
ROOT.PATH <- ANQIS.ROOT.PATH
DEV.PATH  <- "/h2o-r/h2o-package/R/"
FULL.PATH <- paste(ROOT.PATH, DEV.PATH, sep="")

src <-
function() {
  warning("MAY NOT WORK ON YOUR SYSTEM -- **TRY TO CHANGE `ROOT.PATH`!**")
  to_src <- c("classes.R", "connection.R", "constants.R", "logging.R", "communication.R", 
              "kvstore.R", "exec.R", "ops.R", "frame.R", "ast.R", "astfun.R", "import.R", 
              "parse.R", "export.R", "models.R", "edicts.R", "glm.R", "glrm.R", "pca.R", "kmeans.R", 
              "gbm.R", "deeplearning.R", "naivebayes.R", "randomForest.R", "svd.R", "locate.R")
  require(rjson); require(RCurl)
  invisible(lapply(to_src,function(x){source(paste(FULL.PATH, x, sep = ""))}))
}
src()


conn <- h <- h2o.init("localhost", 54321)
hex <- as.h2o(iris, conn)
#hex <- h2o.importFile(h, paste(ROOT.PATH, "h2o-dev/smalldata/logreg/prostate.csv", sep = ""))
