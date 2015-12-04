setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

options(echo=TRUE)

test.pub.767 <- function() {
  Log.info('Importing the altered covtype training_data from smalldata.')
  cov <- h2o.importFile( normalizePath(locate('smalldata/covtype/covtype.altered.gz')), 'cov')

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