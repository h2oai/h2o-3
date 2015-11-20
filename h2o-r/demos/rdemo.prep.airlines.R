library(h2o)
h2o.init()

filePath <- "smalldata/airlines/allyears2k_headers.zip"
air.hex <- h2o.uploadFile(h2o:::.h2o.locate(filePath), "air.hex")

dim(air.hex)
colnames(air.hex)
numCols <- ncol(air.hex)

x_cols <- c("Month", "DayofMonth", "DayOfWeek", "CRSDepTime", "CRSArrTime", "UniqueCarrier", "CRSElapsedTime", "Origin", "Dest", "Distance")
y_col <- "SynthDepDelayed"

noDepDelayedNAs.hex <- air.hex[!is.na(air.hex$DepDelay), ]
dim(noDepDelayedNAs.hex)

minutesOfDelayWeTolerate <- 15
plusOne <- numCols + 1
noDepDelayedNAs.hex[, plusOne] <- noDepDelayedNAs.hex$DepDelay > minutesOfDelayWeTolerate
noDepDelayedNAs.hex[, plusOne] <- as.factor(noDepDelayedNAs.hex[,plusOne])
cn <- colnames(noDepDelayedNAs.hex)
cn[numCols+1] <- y_col
colnames(noDepDelayedNAs.hex) = cn

air.gbm <- h2o.gbm(x = x_cols, y = y_col, training_frame = noDepDelayedNAs.hex, distribution = "multinomial")
print(air.gbm)