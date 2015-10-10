######################################################################
# Test for PUB-767
#
#  Handle discontinuity in integer respones classes
#   covtype.altered has classes: -1, 2, 3, 6, 10000
#
# From disk:
#  cut -d, -f55 covtype.altered | sort | uniq -c | sort
#
#  -1     2160
#   1     22025
#   2     66751
#   3     2160
#   4     2160
#   6     2160
#   10000 2583
#
######################################################################

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
options(echo=TRUE)
source('../h2o-runit.R')

test.pub.767 <- function() {
  Log.info('Importing the altered covtype training_data from smalldata.')
  cov <- h2o.importFile(normalizePath(locate('smalldata/jira/covtype.altered.gz')), 'cov')

  Log.info('Print head of dataset')
  Log.info(head(cov))

  Log.info("Show the counts of each response level")
  cnts <- h2o.ddply(cov, "V55", nrow)
  print(as.data.frame(cnts))

  m <- h2o.randomForest(x = 1:54, y = 55, training_frame = cov, ntrees = 2,
                        max_depth = 100)

  print(m)
  
}

doTest("PUB-767: randomForest on discontinuous integer classes.", test.pub.767)
