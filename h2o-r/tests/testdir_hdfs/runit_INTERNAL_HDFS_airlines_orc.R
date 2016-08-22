setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
#----------------------------------------------------------------------
# Purpose:  This tests orc parser on multi-file parsing in HDFS.
#----------------------------------------------------------------------

# Check if we are running inside the H2O network by seeing if we can touch
# the namenode.
hdfs_name_node <- Sys.getenv(c("NAME_NODE"))
print(hdfs_name_node)

#myIP   <- H2O.IP
#myPort <- H2O.PORT

hdfs_air_orc = "/datasets/airlines_all_orc_parts"
hdfs_air_original = "/datasets/airlines/airlines_all.csv"

#h2o.init(ip=myIP, port=myPort, startH2O = FALSE)

#----------------------------------------------------------------------

heading("BEGIN TEST")
check.hdfs_airorc <- function() {

  heading("Import airlines 116M dataset in original csv format ")
  url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_air_original)

  print("************** csv parsing time: ")
  ptm <- proc.time()
  csv.hex <- h2o.importFile(url,destination_frame = "csv.hex")
  timepassed = proc.time() - ptm
  print(timepassed)

  n <- nrow(csv.hex)
  print(paste("Imported n =", n, "rows from csv"))

  heading("Import airlines 116M dataset in ORC format ")

  #print("************** orc parsing time without forcing column types: ")
  #ptm <- proc.time()
  #orc2.hex <- h2o.importFolder(url,destination_frame = "dd2")
  #timepassed = proc.time() - ptm
  #print(timepassed)
  #h2o.rm(orc2.hex)

  url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_air_orc)
  print("************** orc parsing time: ")
  ptm <- proc.time()
  orc.hex <- h2o.importFile(url,destination_frame = "orc.hex",col.names = names(csv.hex),
                      col.types = c("Numeric","Numeric","Numeric","Numeric","Numeric","Numeric","Numeric","Numeric","Enum","Numeric",
                      "Numeric","Numeric","Numeric","Numeric","Numeric","Numeric","Enum","Enum","Numeric","Numeric","Numeric","Numeric"
                      ,"Numeric","Numeric","Numeric","Numeric","Numeric","Numeric","Numeric","Enum","Enum"))
  timepassed = proc.time() - ptm
  print(timepassed)

  n <- nrow(orc.hex)
  print(paste("Imported n =", n, "rows from orc"))


  expect_equal(dim(orc.hex),dim(csv.hex))
  expect_equal(summary(orc.hex),summary(csv.hex))

  h2o.rm(orc.hex)   # remove file
  h2o.rm(csv.hex)
}

doTest("ORC multifile parse test", check.hdfs_airorc)