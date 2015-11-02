#To import small iris data file from H2O's package:
irisPath = system.file("extdata", "iris.csv", package="h2o")
iris.hex = h2o.importFile(path = irisPath, destination_frame = "iris.hex")

#To import an entire folder of files as one data object:
# pathToFolder = "/Users/data/airlines/"
# airlines.hex = h2o.importFile(path = pathToFolder, destination_frame = "airlines.hex")

#To import from HDFS and connect to H2O in R using the IP and port of an H2O instance running on your  Hadoop cluster:
# h2o.init(ip= <IPAddress>, port =54321, nthreads = -1)
# pathToData = "hdfs://mr-0xd6.h2oai.loc/datasets/airlines_all.csv"
# airlines.hex = h2o.importFile(path = pathToData, destination_frame = "airlines.hex")