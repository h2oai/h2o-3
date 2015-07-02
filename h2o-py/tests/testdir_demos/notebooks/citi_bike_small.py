import h2o
import time
# Explore a typical Data Science workflow with H2O and Python
#
# Goal: assist the manager of CitiBike of NYC to load-balance the bicycles
# across the CitiBike network of stations, by predicting the number of bike
# trips taken from the station every day.  Use 10 million rows of historical
# data, and eventually add weather data.


# Connect to a cluster
# Set this to True if you want to fetch the data directly from S3.
# This is useful if your cluster is running in EC2.
data_source_is_s3 = False

def mylocate(s):
    if data_source_is_s3:
        return "s3n://h2o-public-test-data/" + s
    else:
        return h2o.locate(s)
# Pick either the big or the small demo.
# Big data is 10M rows
small_test = [mylocate("bigdata/laptop/citibike-nyc/2013-10.csv")]
big_test =   [mylocate("bigdata/laptop/citibike-nyc/2013-07.csv"),
              mylocate("bigdata/laptop/citibike-nyc/2013-08.csv"),
              mylocate("bigdata/laptop/citibike-nyc/2013-09.csv"),
              mylocate("bigdata/laptop/citibike-nyc/2013-10.csv"),
              mylocate("bigdata/laptop/citibike-nyc/2013-11.csv"),
              mylocate("bigdata/laptop/citibike-nyc/2013-12.csv"),
              mylocate("bigdata/laptop/citibike-nyc/2014-01.csv"),
              mylocate("bigdata/laptop/citibike-nyc/2014-02.csv"),
              mylocate("bigdata/laptop/citibike-nyc/2014-03.csv"),
              mylocate("bigdata/laptop/citibike-nyc/2014-04.csv"),
              mylocate("bigdata/laptop/citibike-nyc/2014-05.csv"),
              mylocate("bigdata/laptop/citibike-nyc/2014-06.csv"),
              mylocate("bigdata/laptop/citibike-nyc/2014-07.csv"),
              mylocate("bigdata/laptop/citibike-nyc/2014-08.csv")]

# ----------

# 1- Load data - 1 row per bicycle trip.  Has columns showing the start and end
# station, trip duration and trip start time and day.  The larger dataset
# totals about 10 million rows
print "Import and Parse bike data"
data = h2o.import_frame(path=small_test)
# ----------

# 2- light data munging: group the bike starts per-day, converting the 10M rows
# of trips to about 140,000 station&day combos - predicting the number of trip
# starts per-station-per-day.

# Convert start time to: Day since the Epoch
startime = data["starttime"]
secsPerDay=1000*60*60*24
data["Days"] = (startime/secsPerDay).floor()
data.describe()
# Now do a monster Group-By.  Count bike starts per-station per-day.  Ends up
# with about 340 stations times 400 days (140,000 rows).  This is what we want
# to predict.
group_by_cols = ["Days","start station name"]
aggregates = {"bikes": ["count", 0, "all"]}
bpd = data.group_by(cols=group_by_cols, aggregates=aggregates) # Compute bikes-per-day
bpd.show()
bpd.describe()
bpd.dim()
# Quantiles: the data is fairly unbalanced; some station/day combos are wildly
# more popular than others.
print "Quantiles of bikes-per-day"
bpd["bikes"].quantile().show()
# A little feature engineering
# Add in month-of-year (seasonality; fewer bike rides in winter than summer)
secs = bpd["Days"]*secsPerDay
bpd["Month"]     = secs.month().asfactor()
# Add in day-of-week (work-week; more bike rides on Sunday than Monday)
bpd["DayOfWeek"] = secs.dayOfWeek()
print "Bikes-Per-Day"
bpd.describe()
# ----------
# 3- Fit a model on train; using test as validation

# Function for doing class test/train/holdout split
def split_fit_predict(data):
  global gbm0,drf0,glm0,dl0
  # Classic Test/Train split
  r = data['Days'].runif()   # Random UNIForm numbers, one per row
  train = data[  r  < 0.6]
  test  = data[(0.6 <= r) & (r < 0.9)]
  hold  = data[ 0.9 <= r ]
  print "Training data has",train.ncol(),"columns and",train.nrow(),"rows, test has",test.nrow(),"rows, holdout has",hold.nrow()
  
  # Run GBM
  s = time.time()
  gbm0 = h2o.gbm(x           =train.drop("bikes"),
                 y           =train     ["bikes"],
                 validation_x=test .drop("bikes"),
                 validation_y=test      ["bikes"],
                 ntrees=500, # 500 works well
                 max_depth=6,
                 learn_rate=0.1)
  gbm_elapsed = time.time() - s

  # Run DRF
  s = time.time()
  drf0 = h2o.random_forest(x =train.drop("bikes"),
                y           =train     ["bikes"],
                validation_x=test .drop("bikes"),
                validation_y=test      ["bikes"],
                ntrees=250,
                max_depth=30)
  drf_elapsed = time.time() - s 
    
    
  # Run GLM
  s = time.time()
  glm0 = h2o.glm(x           =train.drop("bikes"),
                 y           =train     ["bikes"],
                 validation_x=test .drop("bikes"),
                 validation_y=test      ["bikes"],
                 Lambda=[1e-5],
                 family="poisson")
  glm_elapsed = time.time() - s
  
  # Run DL
  s = time.time()
  dl0 = h2o.deeplearning(x  =train.drop("bikes"),
                y           =train     ["bikes"],
                validation_x=test .drop("bikes"),
                validation_y=test      ["bikes"],
                hidden=[50,50,50,50],
                epochs=50)
  dl_elapsed = time.time() - s
  
  # ----------
  # 4- Score on holdout set & report
  train_r2_gbm = gbm0.model_performance(train).r2()
  test_r2_gbm  = gbm0.model_performance(test ).r2()
  hold_r2_gbm  = gbm0.model_performance(hold ).r2()
#   print "GBM R2 TRAIN=",train_r2_gbm,", R2 TEST=",test_r2_gbm,", R2 HOLDOUT=",hold_r2_gbm
  
  train_r2_drf = drf0.model_performance(train).r2()
  test_r2_drf  = drf0.model_performance(test ).r2()
  hold_r2_drf  = drf0.model_performance(hold ).r2()
#   print "DRF R2 TRAIN=",train_r2_drf,", R2 TEST=",test_r2_drf,", R2 HOLDOUT=",hold_r2_drf
  
  train_r2_glm = glm0.model_performance(train).r2()
  test_r2_glm  = glm0.model_performance(test ).r2()
  hold_r2_glm  = glm0.model_performance(hold ).r2()
#   print "GLM R2 TRAIN=",train_r2_glm,", R2 TEST=",test_r2_glm,", R2 HOLDOUT=",hold_r2_glm
    
  train_r2_dl = dl0.model_performance(train).r2()
  test_r2_dl  = dl0.model_performance(test ).r2()
  hold_r2_dl  = dl0.model_performance(hold ).r2()
#   print " DL R2 TRAIN=",train_r2_dl,", R2 TEST=",test_r2_dl,", R2 HOLDOUT=",hold_r2_dl
    
  # make a pretty HTML table printout of the results

  header = ["Model", "R2 TRAIN", "R2 TEST", "R2 HOLDOUT", "Model Training Time (s)"]
  table  = [
            ["GBM", train_r2_gbm, test_r2_gbm, hold_r2_gbm, round(gbm_elapsed,3)],
            ["DRF", train_r2_drf, test_r2_drf, hold_r2_drf, round(drf_elapsed,3)],
            ["GLM", train_r2_glm, test_r2_glm, hold_r2_glm, round(glm_elapsed,3)],
            ["DL ", train_r2_dl,  test_r2_dl,  hold_r2_dl , round(dl_elapsed,3) ],
           ]
  h2o.H2ODisplay(table,header)
  # --------------
# Split the data (into test & train), fit some models and predict on the holdout data
split_fit_predict(bpd)
# Here we see an r^2 of 0.91 for GBM, and 0.71 for GLM.  This means given just
# the station, the month, and the day-of-week we can predict 90% of the
# variance of the bike-trip-starts.
# ----------
# 5- Now lets add some weather
# Load weather data
wthr1 = h2o.import_frame(path=[mylocate("bigdata/laptop/citibike-nyc/31081_New_York_City__Hourly_2013.csv"),
                               mylocate("bigdata/laptop/citibike-nyc/31081_New_York_City__Hourly_2014.csv")])
# Peek at the data
wthr1.describe()
# Lots of columns in there!  Lets plan on converting to time-since-epoch to do
# a 'join' with the bike data, plus gather weather info that might affect
# cyclists - rain, snow, temperature.  Alas, drop the "snow" column since it's
# all NA's.  Also add in dew point and humidity just in case.  Slice out just
# the columns of interest and drop the rest.
wthr2 = wthr1[["Year Local","Month Local","Day Local","Hour Local","Dew Point (C)","Humidity Fraction","Precipitation One Hour (mm)","Temperature (C)","Weather Code 1/ Description"]]

wthr2.setName(wthr2.index("Precipitation One Hour (mm)"), "Rain (mm)")
wthr2.setName(wthr2.index("Weather Code 1/ Description"), "WC1")
wthr2.describe()
# Much better!  
# Filter down to the weather at Noon
wthr3 = wthr2[ wthr2["Hour Local"]==12 ]
# Lets now get Days since the epoch... we'll convert year/month/day into Epoch
# time, and then back to Epoch days.  Need zero-based month and days, but have
# 1-based.
wthr3["msec"] = h2o.H2OFrame.mktime(year=wthr3["Year Local"], month=wthr3["Month Local"]-1, day=wthr3["Day Local"]-1, hour=wthr3["Hour Local"])
secsPerDay=1000*60*60*24
wthr3["Days"] = (wthr3["msec"]/secsPerDay).floor()
wthr3.describe()
# msec looks sane (numbers like 1.3e12 are in the correct range for msec since
# 1970).  Epoch Days matches closely with the epoch day numbers from the
# CitiBike dataset.  
# Lets drop off the extra time columns to make a easy-to-handle dataset.
wthr4 = wthr3.drop("Year Local").drop("Month Local").drop("Day Local").drop("Hour Local").drop("msec")
# Also, most rain numbers are missing - lets assume those are zero rain days
rain = wthr4["Rain (mm)"]
rain[ rain.isna() ] = 0
# ----------
# 6 - Join the weather data-per-day to the bike-starts-per-day
print "Merge Daily Weather with Bikes-Per-Day"
bpd_with_weather = bpd.merge(wthr4,allLeft=True,allRite=False)
bpd_with_weather.describe()
bpd_with_weather.show()
# 7 - Test/Train split again, model build again, this time with weather
split_fit_predict(bpd_with_weather)
