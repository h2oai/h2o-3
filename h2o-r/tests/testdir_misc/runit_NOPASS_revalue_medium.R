#----------------------------------------------------------------------
# Try to slice by using != factor_level
#----------------------------------------------------------------------
source('../h2o-runit.R')
options(echo=TRUE)
library(h2o)
conn <- h2o.init(ip=myIP, port=myPort, startH2O=FALSE)

filePath <- "/home/0xdiag/datasets/airlines/airlines_all.csv"

# Uploading data file to h2o.
air <- h2o.importFile(conn, filePath, "air")

# Print dataset size.
print(levels(air$Origin))

revalue(air$Origin, c(SFO = "SAN FRANCISCO TREAT AIRPOT"))

print(levels(air$Origin))


PASS_BANNER()
