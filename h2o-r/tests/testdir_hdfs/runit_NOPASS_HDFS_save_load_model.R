library(h2o)
#----------------------------------------------------------------------
# Purpose:  Test save/load of H2O GLM models to/from HDFS
#----------------------------------------------------------------------

# setwd("/Users/tomk/0xdata/ws/h2o/R/tests/testdir_hdfs")

local({r <- getOption("repos"); r["CRAN"] <- "http://cran.us.r-project.org"; options(repos = r)})
if(!"R.utils" %in% rownames(installed.packages())) install.packages("R.utils")

options(echo=TRUE)
TEST_ROOT_DIR <- ".."
source(sprintf("%s/%s", TEST_ROOT_DIR, "findNSourceUtils.R"))

#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------

# Check if we are running inside the 0xdata network by seeing if we can touch
# the cdh3 namenode. Update if using other clusters.
# Note this should fail on home networks, since 176 is not likely to exist
# also should fail in ec2.
running_inside_hexdata = url.exists("http://192.168.1.176:80", timeout=1)

if (running_inside_hexdata) {
  # cdh3 cluster
  hdfs_name_node = "192.168.1.176"    
  hdfs_covtype_file = "/datasets/runit/covtype.data"
  hdfs_tmp_dir = "/tmp/runit"
} else {
  stop("Not running on 0xdata internal network.  No access to HDFS.")
}

#----------------------------------------------------------------------
#heading("BEGIN TEST")
conn <- new("H2OClient", ip=myIP, port=myPort)

#----------------------------------------------------------------------
# Single file cases.
#----------------------------------------------------------------------

#heading("Testing single file importHDFS")
url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_covtype_file)
model_path <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_tmp_dir)
covtype.hex <- h2o.importFile(conn, url)
covtype.hex[,55] <- ifelse(covtype.hex[,55] == 1, 1, 0)
#heading("Running covtype GLM")
covtype.glm <- h2o.glm(y = 55, x = setdiff(1:54, c(21,29)), data = covtype.hex, family = "gaussian", nfolds = 2, alpha = 0, lambda = 0)
covtype.glm

# covtype.glm.path <- h2o.saveModel(covtype.glm, dir = model_path)
myName <- paste(Sys.info()["user"], "GLM_model", sep = "_")
covtype.glm.path <- h2o.saveModel(covtype.glm, dir = model_path, name = myName, force = TRUE)
covtype.glm2 <- h2o.loadModel(conn, covtype.glm.path)

expect_equal(class(covtype.glm), class(covtype.glm2))
expect_equal(covtype.glm@data, covtype.glm2@data)
expect_equal(covtype.glm@model, covtype.glm2@model)
expect_equal(covtype.glm@xval, covtype.glm2@xval)

PASS_BANNER()
