setwd(normalizePath(dirname(R.utils::commandArgs(asValues = TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

# This test will test API for R clients using rankWithinGroupBy
test.rankwithingroupby <- function() {
  newColName <- "new_rank_within_group"
  
  if (runif(1,0,1)>0.5) { # do test 1
    groupCols <- c("GroupByCols0")
    sortCols <- c("sort0", "sort1")
    sortDirs <- c(TRUE, FALSE)
    train <- h2o.importFile(locate("bigdata/laptop/jira/train5456_1.csv"))
    answer <- h2o.importFile(locate("bigdata/laptop/jira/rankedFrame1.csv"))
    rankedF <- h2o.rank_within_group_by(train, groupCols, sortCols, sortDirs, newColName, sort_cols_sorted=TRUE)
    h2o.summary(rankedF)
    compareCol(as.data.frame(answer$new_rank_within_group), as.data.frame(rankedF$new_rank_within_group))
  } else { # do test 2
    groupCols <- c("GroupByCols0", "GroupByCols1")
    sortCols <- c("sort0", "sort1", "sort2")
    sortDirs <- c(TRUE, TRUE, TRUE)
    train <- h2o.importFile(locate("bigdata/laptop/jira/train5456_2.csv"))
    answer <- h2o.importFile(locate("bigdata/laptop/jira/rankedFrame2.csv"))
    rankedF <- h2o.rank_within_group_by(train, groupCols, sortCols, sort_cols_sorted=TRUE) # no optional argument
    rankedF1 <- h2o.rank_within_group_by(train, groupCols, sortCols, new_col_name=newColName, sort_cols_sorted=TRUE) # optional colname
    rankedF2 <- h2o.rank_within_group_by(train, groupCols, sortCols, sortDirs, sort_cols_sorted=TRUE) # optional directions only
    h2o.summary(rankedF)
    compareCol(as.data.frame(answer$new_rank_within_group), as.data.frame(rankedF$New_Rank_column))
    compareCol(as.data.frame(answer$new_rank_within_group), as.data.frame(rankedF1$new_rank_within_group))
    compareCol(as.data.frame(answer$new_rank_within_group), as.data.frame(rankedF2$New_Rank_column))
  }
}

compareCol <- function(t1,t2) {
  for (ind in range(length(t1))) {
    if (!(is.na(t1[ind,1]) && is.na(t2[ind,1]))) { # both entries are not NAs
      expect_true(t1[ind,1], t2[ind,1])
    }
  }
}

doTest("PUBDEV-5456: test rankWithinGroupBy", test.rankwithingroupby)

