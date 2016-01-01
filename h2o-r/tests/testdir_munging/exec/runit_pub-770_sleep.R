setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")





# use this for interactive setup
#     library(h2o)
#     library(testthat)
#     h2o.startLogging()
#     conn = h2o.init()

test.cbind <- function() {

    # seemed to get different errors (cbind) if I pasted the stuff below in "paragraphs" into R
    # adding delays to test
    # Sys.sleep(2)
    df <- data.frame(matrix(1:30000, nrow=30000, ncol=150))
    sample.IDs <- 1:6000
    index <- data.frame(ifelse(df[,1] %in% sample.IDs,1,0))
    colnames(index) <- c("index")

    # Sys.sleep(2)
    df.hex <- as.h2o(df, destination_frame="df")
    index.h2o <- as.h2o(index, destination_frame="index.h2o")

    # Sys.sleep(2)
    df.hex <- h2o.cbind(df.hex,index.h2o)
    summary(df.hex[,"index"])

    # Sys.sleep(2)
    df.train <- h2o.assign(df.hex[df.hex$index==1,],"df.train")
    df.test <- h2o.assign(df.hex[df.hex$index==0,],"df.test")

    # Sys.sleep(2)
    df.hex[,"index"] <- index.h2o[,"index"]
    summary(df.hex[,"index"])
    df.train <- h2o.assign(df.hex[df.hex$index==1,],"df.train")
    df.test <- h2o.assign(df.hex[df.hex$index==0,],"df.test")

    
}

h2oTest.doTest("Test cbind.", test.cbind)
