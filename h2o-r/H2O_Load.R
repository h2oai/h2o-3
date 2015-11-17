# Change this global variable to match your own system's path
CLIFF.ROOT.PATH <- "C:/Users/cliffc/Desktop/"
ANQIS.ROOT.PATH <- "/Users/anqi_fu/Documents/workspace/"
ANQIS.WIN.PATH <- "C:/Users/Anqi/Documents/Work/"
SPENCER.ROOT.PATH <- "/Users/spencer/0xdata/"
LUDI.ROOT.PATH <- "/Users/ludirehak/"
ROOT.PATH <- ANQIS.ROOT.PATH
DEV.PATH  <- "h2o-3/h2o-r/h2o-package/R/"
FULL.PATH <- paste(ROOT.PATH, DEV.PATH, sep="")

src <-
function() {
  warning("MAY NOT WORK ON YOUR SYSTEM -- **TRY TO CHANGE `ROOT.PATH`!**")
  to_src <- c("astfun.R", "classes.R", "connection.R", "constants.R", "logging.R", "communication.R",
              "import.R", "frame.R", "kvstore.R", "grid.R",
              "parse.R", "export.R", "models.R", "edicts.R", "glm.R", "glrm.R", "pca.R", "kmeans.R",
              "gbm.R", "deeplearning.R", "naivebayes.R", "randomForest.R", "svd.R", "locate.R")
  require(jsonlite); require(RCurl)
  invisible(lapply(to_src,function(x){source(paste(FULL.PATH, x, sep = ""))}))
}
src()


h <- conn <- h2o.init()
#hex <- as.h2o(iris)
#hex <- h2o.importFile(h, paste(ROOT.PATH, "h2o-dev/smalldata/logreg/prostate.csv", sep = ""))
