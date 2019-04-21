setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
##
# PUBDEV-5266
# Test out the merge() functionality with String columns in both left and right frames
# Test out the sort() functionality with String columns in the frame
##

test.merge <- function() {
  name1 <- "bigdata/laptop/jira/PUBDEV_5266_merge_with_string_columns/PUBDEV_5266_f1.csv"
  name2 <- "bigdata/laptop/jira/PUBDEV_5266_merge_with_string_columns/PUBDEV_5266_f2.csv"
  cnames1 <- c("stringf1-1", "stringf1-2", "intf1-1", "iintUnique")
  cnames2 <- c("stringf2-1","intf2-1", "iintf2-2", "stringf2-2","intf2-3",  "stringf2-3", "stringf2-4",  "iintUnique")
  
  f1names <- c(name2, name1, name1, name2)
  f2names <- c(name2, name2, name2, name1)
  ansnames <- c("bigdata/laptop/jira/PUBDEV_5266_merge_with_string_columns/sortedF2_R_C2_C3_C10.csv",
                "bigdata/laptop/jira/PUBDEV_5266_merge_with_string_columns/mergedf1_unique_f2_unique.csv",
                "bigdata/laptop/jira/PUBDEV_5266_merge_with_string_columns/mergedf1_unique_f2_unique_x_T.csv",
                "bigdata/laptop/jira/PUBDEV_5266_merge_with_string_columns/mergedf2_unique_f1_unique_x_T.csv")
  f1colnames <- list(cnames2, cnames1, cnames1, cnames2)
  f2colnames <- list(cnames2, cnames2, cnames2, cnames1)
  xvals <-c(F,F,T,T)
  yvals <-c(F,F,F,F)
  
  testIndexA <- sample(c(1:length(cnames1))) # choose one test to run
  testIndex <- testIndexA[1]

  if (testIndex > 1) { # test merge
    f1 <- h2o.importFile(locate(f1names[testIndex])) # c1:int, c2:real, c3:string, c4:enum
    colnames(f1) <- f1colnames[[testIndex]]
    f2 <- h2o.importFile(locate(f2names[testIndex])) # c1:real, c2:int, c3:string, c4/c5:enum, c6/c7:string, c8:int
    colnames(f2) <- f2colnames[[testIndex]]
    h2omerge <- h2o.merge(x=f1, y=f2, all.x=xvals[testIndex], all.y=yvals[testIndex])  # perform merge by h2o
    h2o.ncol(h2omerge)
    colTypes <- unlist(h2o.getTypes(h2omerge))
    fmergedXans <- h2o.importFile(locate(ansnames[testIndex]), col.types=colTypes)
    all.equal(as.data.frame(fmergedXans), as.data.frame(h2omerge))
  } else {  # test sorting
    f2 <- h2o.importFile(locate(f1names[testIndex])) # c1:real, c2:int, c3:string, c4/c5:enum, c6/c7:string, c8:int
    h2osort <- h2o.arrange(f2, C2, C3, C10)
    h2o.ncol(h2osort)
    colTypes <- unlist(h2o.getTypes(h2osort))
    fsortedf2ans <- h2o.importFile(locate(ansnames[testIndex]), col.types=colTypes)
    all.equal(as.data.frame(fsortedf2ans), as.data.frame(h2osort))
  }
}

doTest("Test out the merge() functionality", test.merge)
