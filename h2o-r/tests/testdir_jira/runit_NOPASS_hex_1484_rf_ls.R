######################################################################
# Test for HEX-1484
# h2o.ls() fails as json response from Store view returns junk (/u0000)
######################################################################

# setwd("/Users/tomk/0xdata/ws/h2o/R/tests/testdir_jira")

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
options(echo=TRUE)
source('../h2o-runit.R')

heading("BEGIN TEST")
conn <- new("H2OConnection", ip=myIP, port=myPort)

path = locate("smalldata/logreg/prostate.csv")
prostate.hex = h2o.importFile(conn, path, key="prostate.hex")
h2o.ls(conn)
    
rf = h2o.randomForest(x=c(1,4), y="CAPSULE", data=prostate.hex, ntree=5, type = "BigData")
h2o.ls(conn)

PASS_BANNER()
