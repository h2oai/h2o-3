setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

# Connect to a cluster
# Set this to True if you want to fetch the data directly from S3.
# This is useful if your cluster is running in EC2.
data_source_is_s3 = F

locate_source <- function(s) {
  if (data_source_is_s3)
    myPath <- paste0("s3n://h2o-public-test-data/", s)
  else
    myPath <- locate(s)
}

test.lendingclub.demo <- function(conn) {
# Pick either the big or the small demo.
  small_test <-  locate("bigdata/laptop/lending-club/LoanStats3a.csv")
  big_test <-  c(locate("bigdata/laptop/lending-club/LoanStats3a.csv"),
                 locate("bigdata/laptop/lending-club/LoanStats3b.csv"),
                 locate("bigdata/laptop/lending-club/LoanStats3c.csv"),
                 locate("bigdata/laptop/lending-club/LoanStats3d.csv"))
  
  print("Import approved loan requests for Lending Club...")
  loanStats <- h2o.importFile(path = big_test, destination_frame = "LoanStats")
  
  print("Create bad loan label, this will include charged off and defaulted loans...")
  loan_statuses      <- h2o.levels(loanStats$loan_status)
  bad_loan_labels    <- setdiff(loan_statuses, c("Current", "Fully Paid", 
                         "In Grace Period", "Late (16-30 days)", "Late (31-120 days)"))
  completed_loans    <- c(bad_loan_labels, "Fully Paid")
  loanStats$bad_loan <- loanStats$loan_status %in% bad_loan_labels
  loanStats$bad_loan <- as.factor(loanStats$bad_loan)
  
  loanStats$complete <- loanStats$loan_status %in% completed_loans
  loanStats$complete <- as.factor(loanStats$complete)
  
  testEnd()
}

doTest("Test out Citibike Demo", test.citibike.demo)