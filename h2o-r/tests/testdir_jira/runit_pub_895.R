setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
# Parse the header,test  and train files, transform all columns to enums.
# 
# It is passing on 1,2 JVMs, but failing on 3JVMs
#




test.pub.895 <- function() {
print("Parse header file")
spect_header = h2o.importFile(normalizePath(h2oTest.locate("smalldata/jira/SPECT_header.txt")),destination_frame = "spect_header")
print("Parse train and test files")
spect_train = h2o.importFile(normalizePath(h2oTest.locate("smalldata/jira/SPECT_train.txt")),destination_frame = "spect_train",col.names=spect_header)
spect_test = h2o.importFile(normalizePath(h2oTest.locate("smalldata/jira/SPECT_test.txt")),destination_frame = "spect_test", col.names=spect_header)

print("Summary of the train set")
#print(summary(spect_train))
print(str(spect_train))

print("As all columns in the dataset are binary, converting the datatype to factors")
for(i in 1:length(colnames(spect_train))){
  spect_train[,i] = as.factor(spect_train[,i])
  spect_test[,i] = as.factor(spect_test[,i])
}
#print(summary(spect_train))
#print(summary(spect_test))

   

}

h2oTest.doTest("Test pub 895", test.pub.895)
