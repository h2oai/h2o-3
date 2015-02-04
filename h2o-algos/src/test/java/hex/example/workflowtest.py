import h2o

# Connect to a cluster
h2o.init()

# Pick either the big or the small demo.
# Big data is 10M rows
small_test = ["bigdata/laptop/citibike-nyc/2013-10.csv"]
big_test =   ["bigdata/laptop/citibike-nyc/2013-07.csv",
              "bigdata/laptop/citibike-nyc/2013-08.csv",
              "bigdata/laptop/citibike-nyc/2013-09.csv",
              "bigdata/laptop/citibike-nyc/2013-10.csv",
              "bigdata/laptop/citibike-nyc/2013-11.csv",
              "bigdata/laptop/citibike-nyc/2013-12.csv",
              "bigdata/laptop/citibike-nyc/2014-01.csv",
              "bigdata/laptop/citibike-nyc/2014-02.csv",
              "bigdata/laptop/citibike-nyc/2014-03.csv",
              "bigdata/laptop/citibike-nyc/2014-04.csv",
              "bigdata/laptop/citibike-nyc/2014-05.csv",
              "bigdata/laptop/citibike-nyc/2014-06.csv",
              "bigdata/laptop/citibike-nyc/2014-07.csv",
              "bigdata/laptop/citibike-nyc/2014-08.csv"]

# 1- Load data
data = h2o.import_frame(path=small_test)


# 2- light data munging

# Convert start time to: Day since the Epoch
startime = data["starttime"]
data["Days"] = (startime/(1000*60*60*24)).floor()
data.describe()

# Now do a monster Group-By.  Count bike starts per-station per-day
bph = h2o.ddply(data,["Days","start station name"],"nrow")
