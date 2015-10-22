#####################################################
## Starter Script for Kaggle Will it Rain competition
#####################################################
##
## Performance: 0.00973457
## Relative performance: 70th out of 188; improves upon 
## provided benchmark (0.01177621), and using all 1's (0.01017651)
######################################################
#record the starting time
start<-Sys.time()

## Point to directory where the Kaggle data is
dir <- paste0(path.expand("~"), "/h2o-kaggle/rain/")

## read in a fixed number of rows (increase/decrease based on memory footprint)
## you can use read.csv in place of data.table; it's just much slower
library(data.table) 
train<-fread(paste0(dir,"train_2013.csv"), select="Expected")
gc()

## collect the probability it will rain 0mm, 1mm, 2mm...9mm
for(i in 1:10){
if(i==1){avgRainRate<-train[,mean(ifelse(Expected<=(i-1),1,0))]}
if(i>1){avgRainRate<-c(avgRainRate,train[,mean(ifelse(Expected<=(i-1),1,0))])}
}

## fill in 10mm+ with 100% (it will be lower than 10mm 100% of the time)
avgRainRate<-c(avgRainRate,rep(1,60))

## now construct a prediction by using the matrix as a lookup table to the first 10 prediction levels
test<-fread(paste0(dir,"test_2014.csv"),select="Id")
gc()
predictions<-as.data.frame(cbind(test$Id,as.data.frame(t(avgRainRate))))
colnames(predictions)<-c("Id",paste0("Predicted",(seq(1:70)-1)))

## output predictions; outputs as 188MB, but compresses to <3MB (lot of 1's)
write.table(predictions,"histogram_benchmark.csv",quote = FALSE, sep = ",",row.names=FALSE)

##how long did it take
stop<-Sys.time()
stop-start
##all done. compress and submit
