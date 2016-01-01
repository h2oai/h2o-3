setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



rtest <- function() {

hdfs_name_node = HADOOP.NAMENODE
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

}

h2oTest.doTest("Test",rtest)
