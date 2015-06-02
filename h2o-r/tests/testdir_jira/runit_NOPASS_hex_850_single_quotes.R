######################################################################
# Test for HEX-850
######################################################################

# setwd("/Users/tomk/0xdata/ws/h2o/R/tests/testdir_jira")

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
options(echo=TRUE)
source('../h2o-runit.R')

heading("BEGIN TEST")
conn <- new("H2OConnection", ip=myIP, port=myPort)

path = locate("smalldata/jira/850.csv")
j.fv = h2o.importFile(conn, path, destination_frame="jira850.hex")
h2o.ls(conn)

if (nrow(j.fv) != 4) {
    stop("j.fv should have 4 rows")
}

if (ncol(j.fv) != 3) {
    stop ("j.fv should have 3 cols")
}

summary(j.fv)

rj.fv = as.data.frame(j.fv)
j.fv$age = j.fv$age + 1
head(rj.fv)

coolname = j.fv[2,2]
rcoolname = (as.data.frame(coolname))
print(rcoolname)
if (rcoolname != "orange'jello") {
    stop ("rcoolname mismatch")
}

PASS_BANNER()
