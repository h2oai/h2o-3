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

# ----------
# 1- Load data
data = h2o.import_frame(path=small_test)


# ----------
# 2- light data munging

# Convert start time to: Day since the Epoch
startime = data["starttime"]
secsPerDay=1000*60*60*24
data["Days"] = (startime/secsPerDay).floor()
data.describe()

# Now do a monster Group-By.  Count bike starts per-station per-day
ddplycols=["Days","start station name"]
bph = h2o.ddply(data[ddplycols],ddplycols,"(%nrow)")
bph["C1"]._name = "bikes"
bph["bikes"].quantile().show()

# A little feature engineering
# Add in month-of-year (seasonality; fewer bike rides in winter than summer)
secs = bph["Days"]*secsPerDay
bph["Month"]     = secs.month()
# Add in day-of-week (work-week; more bike rides on Sunday than Monday)
bph["DayOfWeek"] = secs.dayOfWeek()
bph.describe()

# Test/train split
r = bph['Days'].runif()
train = bph[ r < 0.6  ]
test  = bph[(0.6 <= r) & (r < 0.9)]
hold  = bph[ 0.9 <= r ]
train.describe()
test .describe()

# ----------
# 3- build model on train; using test as validation

# Run GBM
gbm = h2o.gbm(x           =train.drop("bikes"),
              y           =train     ["bikes"],
              validation_x=test .drop("bikes"),
              validation_y=test      ["bikes"],
              ntrees=500, # 500 works well
              max_depth=6,
              min_rows=10,
              nbins=20,
              learn_rate=0.1)
#gbm.show()

# Run GLM
glm = h2o.glm(x           =train.drop("bikes"),
              y           =train     ["bikes"],
              validation_x=test .drop("bikes"),
              validation_y=test      ["bikes"])
#glm.show()


# ----------
# 4- Score on holdout set & report
train_r2_gbm = gbm.model_performance(train).r2()
test_r2_gbm  = gbm.model_performance(test ).r2()
hold_r2_gbm  = gbm.model_performance(hold ).r2()
print "GBM R2 TRAIN=",train_r2_gbm,", R2 TEST=",test_r2_gbm,", R2 HOLDOUT=",hold_r2_gbm

train_r2_glm = glm.model_performance(train).r2()
test_r2_glm  = glm.model_performance(test ).r2()
hold_r2_glm  = glm.model_performance(hold ).r2()
print "GLM R2 TRAIN=",train_r2_glm,", R2 TEST=",test_r2_glm,", R2 HOLDOUT=",hold_r2_glm

