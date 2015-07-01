import h2o
air = h2o.upload_file(h2o.locate("smalldata/airlines/allyears2k_headers.zip"))
air.dim()
numNAs = air["DepTime"].isna().sum()
print numNAs
DepTime_mean = air["DepTime"].mean(na_rm=True)
print DepTime_mean
air.impute("DepTime", method = "median", combine_method="low")   
numNAs = air["DepTime"].isna().sum()
print numNAs
air = h2o.upload_file(h2o.locate("smalldata/airlines/allyears2k_headers.zip"))
air.impute("DepTime", method = "mean", by = ["Origin", "Distance"]).show()
air = h2o.upload_file(h2o.locate("smalldata/airlines/allyears2k_headers.zip"))
air.impute("TailNum", method = "mode").show()
air = h2o.upload_file(h2o.locate("smalldata/airlines/allyears2k_headers.zip"))
air.impute("TailNum", method = "mode", by=["Month", "Year"]).show()
