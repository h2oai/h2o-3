# Change this global variable to match your own system's path
SPENCER.ROOT.PATH <- "/Users/spencer/0xdata/"
LUDI.ROOT.PATH <- "/Users/ludirehak/"
ARNO.ROOT.PATH <- "/home/arno/"
AMY.ROOT.PATH <- "/Users/amy/"
MAGNUS.ROOT.PATH <- "/Users/magnus/Git/"
USER.PATHS <- c(SPENCER.ROOT.PATH, LUDI.ROOT.PATH, ARNO.ROOT.PATH, AMY.ROOT.PATH, MAGNUS.ROOT.PATH) 
ROOT.PATH <- USER.PATHS [ sapply(USER.PATHS, dir.exists)]
DEV.PATH  <- "h2o-3/h2o-r/h2o-package/R/"
FULL.PATH <- paste(ROOT.PATH, DEV.PATH, sep="")

src <-
function() {
  warning("MAY NOT WORK ON YOUR SYSTEM -- **TRY TO CHANGE `ROOT.PATH`!**")
  to_src <- c("astfun.R", "classes.R", "config.R", "connection.R", "constants.R", "logging.R", "communication.R",
              "import.R", "frame.R", "kvstore.R", "grid.R", 
              "parse.R", "export.R", "models.R", "edicts.R", "coxph.R", "coxphutils.R", "glm.R", "glrm.R", "pca.R", "kmeans.R",
              "gbm.R", "deeplearning.R", "deepwater.R", "naivebayes.R", "randomforest.R", "svd.R", "locate.R", "predict.R",
              "isolationforest.R")
  require(jsonlite); require(RCurl)
  invisible(lapply(to_src,function(x){source(paste(FULL.PATH, x, sep = ""))}))
}
src()

h <- conn <- h2o.init(strict_version_check = F)

#hex <- as.h2o(iris)
#hex <- h2o.importFile(h, paste(ROOT.PATH, "h2o-dev/smalldata/logreg/prostate.csv", sep = ""))
