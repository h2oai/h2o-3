options(echo=FALSE)
local({r <- getOption("repos"); r["CRAN"] <- "http://cran.us.r-project.org"; options(repos = r)})

Log.info("============== Setting up R-Unit environment... ================")
Log.info("Branch: ")
system('git branch')
Log.info("Hash: ")
system('git rev-parse HEAD')

defaultPath <- locate("R/src/contrib", "h2o-r"  )
ipPort <- get_args(commandArgs(trailingOnly = TRUE))
checkNLoadWrapper(ipPort)
checkNLoadPackages()


Log.info("Loading other required test packages")
if(!"glmnet" %in% rownames(installed.packages())) install.packages("glmnet")
if(!"gbm"    %in% rownames(installed.packages())) install.packages("gbm")
if(!"ROCR"   %in% rownames(installed.packages())) install.packages("ROCR")
if (!"plyr" %in% rownames(installed.packages())) install.packages("plyr")
#if (!"rgl" %in% rownames(installed.packages())) install.packages("rgl")
if (!"randomForest" %in% rownames(installed.packages())) install.packages("randomForest")
require(glmnet)
require(gbm)
require(ROCR)

#Global Variables
myIP   <- ipPort[[1]]
myPort <- ipPort[[2]]
PASSS <- FALSE
view_max <- 10000 #maximum returned by Inspect.java
SEED <- NULL
MASTER_SEED <- FALSE

