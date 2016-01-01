setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



rtest <- function() {

hdfs_name_node = HADOOP.NAMENODE
hdfs_data_file = "/datasets/bigdata/7MRows_4400KCols.csv"
#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------

url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_data_file)
parse_time <- system.time(data.hex <- h2o.importFile(url))
print("Time it took to parse")
print(parse_time)

# Start modeling   
# GLM gaussian
response="C1" #1:1000 imbalance
predictors=c(3:ncol(data.hex))

glm_time <- system.time(mdl.glm <- h2o.glm(x=predictors, y=response, training_frame=data.hex, family="gaussian", solver = "L_BFGS"))
mdl.glm
print("Time it took to build GLM")
print(glm_time)

# GLM binomial
response="C2" #1:1000 imbalance
predictors=c(4:ncol(data.hex))

glm2_time <- system.time(mdl2.glm <- h2o.glm(x=predictors, y=response, training_frame=data.hex, family="binomial", solver = "L_BFGS"))
mdl2.glm
print("Time it took to build GLM")
print(glm2_time)


}

h2oTest.doTest("Test",rtest)

