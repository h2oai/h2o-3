setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



rtest <- function() {

hdfs_name_node = HADOOP.NAMENODE
z_file = "/datasets/z_repro.csv.gz"
url <- sprintf("hdfs://%s%s", hdfs_name_node, z_file)
fr <- h2o.importFile(url)
fr[,1] <- as.factor(fr[,1])
rf <- h2o.randomForest(x=2:ncol(fr), y=1, training_frame=fr, min_rows=1, ntrees=25, max_depth=45)
h2o.download_pojo(rf)

}

h2oTest.doTest("Test",rtest)
