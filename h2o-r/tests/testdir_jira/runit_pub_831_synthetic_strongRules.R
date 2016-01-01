setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
##
# Testing glm completion with strong rules on data with added synthetic noise.
##






test <- function() {

    print("Reading in original prostate data.")
        prostate.hex <- h2o.importFile(h2oTest.locate("smalldata/prostate/prostate.csv.zip"), destination_frame="prostate.hex", header=TRUE)
    
    print("Reading in synthetic columns.")
        BIN <- h2o.importFile(h2oTest.locate("smalldata/prostate/prostate.bin.csv.zip"), destination_frame="BIN", header=FALSE)
        FLOAT <- h2o.importFile(h2oTest.locate("smalldata/prostate/prostate.float.csv.zip"), destination_frame="FLOAT", header=FALSE)
        INT <- h2o.importFile(h2oTest.locate("smalldata/prostate/prostate.int.csv.zip"), destination_frame="INT", header=FALSE)
        colnames(BIN) <- "BIN"
        colnames(FLOAT) <- "FLOAT"
        colnames(INT) <- "INT"

    print("Bind synthetic columns to original data.")
        prostate.data <- h2o.assign(h2o.cbind(prostate.hex, BIN, FLOAT, INT),"prostate.data")
   
    print("Run test/train split at 20/80.")
        prostate.data$split <- ifelse(h2o.runif(prostate.data)>0.8, yes=1, no=0)
        prostate.train <- h2o.assign(prostate.data[prostate.data$split == 0, c(1:12)], "prostate.train")
        prostate.test <- h2o.assign(prostate.data[prostate.data$split == 1, c(1:12)], "prostate.test")
    
    print("Run modeling with with and without synthetic data.")
        # Test that run time is within reasonable timeframe (ie. 30 seconds)
        # GLM aborted if exceeds time frame and test fails
        startTime <- proc.time()
        prostate.def.model <- h2o.glm(x=c("ID","CAPSULE","AGE","RACE","DPROS","DCAPS","PSA","VOL"), y=c("GLEASON"), prostate.train, family="gaussian", lambda_search=FALSE, alpha=1, nfolds=0)
        endTime <- proc.time()
        elapsedTime <- endTime - startTime
        print(elapsedTime["elapsed"])
        stopifnot(elapsedTime["elapsed"] < 60)
        
        startTime <- proc.time()
        prostate.bin.model <- h2o.glm(x=c("ID","CAPSULE","AGE","RACE","DPROS","DCAPS","PSA","VOL","BIN"), y=c("GLEASON"), prostate.train, family="gaussian", lambda_search=FALSE, alpha=1, nfolds=0)
        endTime <- proc.time()
        elapsedTime <- endTime - startTime
        print(elapsedTime["elapsed"])
        stopifnot(elapsedTime["elapsed"] < 60)
        
        startTime <- proc.time()
        prostate.float.model <- h2o.glm(x=c("ID","CAPSULE","AGE","RACE","DPROS","DCAPS","PSA","VOL","FLOAT"), y=c("GLEASON"), prostate.train, family="gaussian", lambda_search=FALSE, alpha=1, nfolds=0)
        endTime <- proc.time()
        elapsedTime <- endTime - startTime
        print(elapsedTime["elapsed"])
        stopifnot(elapsedTime["elapsed"] < 60)
        
        startTime <- proc.time()
        prostate.int.model <- h2o.glm(x=c("ID","CAPSULE","AGE","RACE","DPROS","DCAPS","PSA","VOL","INT"), y=c("GLEASON"), prostate.train, family="gaussian", lambda_search=FALSE, alpha=1, nfolds=0)
        endTime <- proc.time()
        elapsedTime <- endTime - startTime
        print(elapsedTime["elapsed"])
        stopifnot(elapsedTime["elapsed"] < 60)
        
    }

h2oTest.doTest("Testing glm completion with strong rules on data with added synthetic noise.", test)
