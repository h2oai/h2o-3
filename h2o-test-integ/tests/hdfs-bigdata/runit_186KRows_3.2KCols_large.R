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

heading("BEGIN TEST")
conn <- h2o.init(ip=myIP, port=myPort)

#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------

data.hex <- h2o.importFile(conn, "/mnt/0xcustomer-datasets/c25/df_h2o.csv", header = T)

colNames = {}
for(col in names(data.hex)) {
    colName <- if(is.na(as.numeric(col))) col else paste0("C", as.character(col))
    colNames = append(colNames, colName)
}

colNames
names(data.hex) <- colNames

myY = "C1"
myX = setdiff(names(data.hex), myY)

# Start modeling
# GLM
data1.glm <- h2o.glm(x=myX, y=myY, training_frame = data.hex, family="gaussian") 
data1.glm

#GBM on original dataset
data1.gbm = h2o.gbm(x = myX, y = myY, training_frame = data.hex,
                    ntrees = 50, max_depth = 10, loss = "multinomial")
data1.gbm 

#Deep Learning
data1.dl <- h2o.deeplearning(x=myX, y=myY, training_frame=data.hex)
data1.dl 

PASS_BANNER()
