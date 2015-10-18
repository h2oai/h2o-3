library(h2o)
h2o.init()

filePath <- "https://raw.github.com/0xdata/h2o/master/smalldata/airlines/allyears2k_headers.zip"
testFilePath <-"https://raw.github.com/0xdata/h2o/master/smalldata/airlines/allyears2k_headers.zip"

# Uploading data file to h2o.
air <- h2o.importFile(filePath, "air")

# Print dataset size.
dim(air)

#
# Example 1: Select all flights not departing from SFO
#

not.sfo <- air[air$Origin != "SFO",]
print(dim(not.sfo))