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
h2o.init(ip=myIP, port=myPort, startH2O = FALSE)
h2o.removeAll()

z_file = "/datasets/z_repro.csv.gz"
url <- sprintf("hdfs://%s%s", hdfs_name_node, z_file)
fr <- h2o.importFile(url)
fr[,1] <- as.factor(fr[,1])
rf <- h2o.randomForest(x=2:ncol(fr), y=1, training_frame=fr, min_rows=1, ntrees=25, max_depth=45)
h2o.download_pojo(rf)

PASS_BANNER()