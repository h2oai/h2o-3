#----------------------------------------------------------------------
# Purpose:  Condition an Airline dataset by filtering out NAs where the
#           departure delay in the input dataset is unknown.
#
#           Then treat anything longer than minutesOfDelayWeTolerate
#           as delayed.
#----------------------------------------------------------------------

# setwd("/Users/tomk/0xdata/ws/h2o/R/tests/testdir_demos")

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')
options(echo=TRUE)

heading("BEGIN TEST")
conn <- h2o.init(ip=myIP, port=myPort)

filePath <- "smalldata/airlines/allyears2k_headers.zip"
air.hex <- h2o.uploadFile(conn, locate(filePath), "air.hex")

dim(air.hex)
colnames(air.hex)
numCols <- ncol(air.hex)

x_cols <- c("Month", "DayofMonth", "DayOfWeek", "CRSDepTime", "CRSArrTime", "UniqueCarrier", "CRSElapsedTime", "Origin", "Dest", "Distance")
y_col <- "SynthDepDelayed"

noDepDelayedNAs.hex <- air.hex[!is.na(air.hex$DepDelay)]
dim(noDepDelayedNAs.hex)

minutesOfDelayWeTolerate <- 15
noDepDelayedNAs.hex[,numCols+1] <- noDepDelayedNAs.hex$DepDelay > minutesOfDelayWeTolerate
noDepDelayedNAs.hex[,numCols+1] <- as.factor(noDepDelayedNAs.hex[,numCols+1])
cn <- colnames(noDepDelayedNAs.hex)
cn[numCols+1] <- y_col
colnames(noDepDelayedNAs.hex) = cn

air.gbm <- h2o.gbm(x = x_cols, y = y_col, data = noDepDelayedNAs.hex)
air.gbm


PASS_BANNER()
