#######################################################################################
#' A way to get relevant information from Master Jenkins.
#' Display summary tables from getDataMySQL.R
#' Will output to a html file
#' @author  navdeepgill
#######################################################################################
source("scripts/getDataMySQL.R")
library(xtable)
print(xtable(master_jobname), type="html", file="plots/JobName.html")
print(xtable(master_testname), type="html", file="plots/TestName.html")
print(xtable(master_jobname_testname), type="html", file="plots/JobNameTestName.html")
print(xtable(master_testname_failures), type="html", file="plots/TestNameFailures.html")
master_os_failures$Date = as.character(master_os_failures$Date)
print(xtable(master_os_failures), type="html", file="plots/OSFailures.html")