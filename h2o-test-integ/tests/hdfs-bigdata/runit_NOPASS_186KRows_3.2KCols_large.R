#----------------------------------------------------------------------
# Purpose:  This test exercises building GLM/GBM/DL  model 
#           for 186K rows and 3.2K columns 
#----------------------------------------------------------------------
    
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit-hadoop.R') 

ipPort <- get_args(commandArgs(trailingOnly = TRUE))
myIP   <- ipPort[[1]]
myPort <- ipPort[[2]]
hdfs_name_node <- Sys.getenv(c("NAME_NODE"))
print(hdfs_name_node)

library(RCurl)
library(h2o)

running_inside_hexdata = file.exists("/mnt/0xcustomer-datasets/c25/df_h2o.csv")

heading("BEGIN TEST")
h2o.init(ip=myIP, port=myPort, startH2O = FALSE)
h2o.removeAll()

#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------
data.hex <- h2o.importFile("/mnt/0xcustomer-datasets/c25/df_h2o.csv", header = T)

colNames = {}
for(col in names(data.hex)) {
    colName <- if(is.na(as.numeric(col))) col else paste0("C", as.character(col))
    colNames = append(colNames, colName)
}

colNames[1] <- "C1"
names(data.hex) <- colNames

myY = colNames[1] 
myX = setdiff(names(data.hex), myY)

# Start modeling
# GLM
data1.glm <- h2o.glm(x=myX, y=myY, training_frame = data.hex, family="gaussian", solver = "L_BFGS") 
data1.glm

#GBM on original dataset
data1.gbm = h2o.gbm(x = myX, y = myY, training_frame = data.hex,
                    ntrees = 10, max_depth = 5, distribution = "multinomial")
data1.gbm 

#Deep Learning
data1.dl <- h2o.deeplearning(x=myX, y=myY, training_frame=data.hex, epochs=.1, hidden=c(10,10))
data1.dl 

#Random Forest
data1.rf = h2o.randomForest(x = myX, y = myY, training_frame = data.hex,
                    ntrees = 10, max_depth = 5)
data1.rf

PASS_BANNER()
