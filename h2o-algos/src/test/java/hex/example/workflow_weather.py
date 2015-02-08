# Import h2o and connect to the cluster
import h2o
h2o.init()

# Load weather data
df1 = h2o.import_frame(path=["bigdata/laptop/citibike-nyc/31081_New_York_City__Hourly_2013.csv","bigdata/laptop/citibike-nyc/31081_New_York_City__Hourly_2014.csv"])
# Peek at the data
df1.describe()

# Lots of columns in there!  Lets plan on converting to time-since-epoch to do
# a 'join' with the bike data, plus gather weather info that might affect
# cyclists - rain, snow, temperature.  Also dew point and humidity just in case.
# Slice out just the columns of interest and drop the rest.
df2 = df1["Year Local","Month Local","Day Local","Hour Local","Dew Point (C)","Humidity Fraction","Precipitation One Hour (mm)","Snow Depth (cm)","Temperature (C)","Weather Code 1/ Description"]
df2.describe()
# Much better!  

# Lets make daily averages - which will be a big group-by - and filter down to
# day-time bicycle weather (i.e., I'm ignoring the temperature at midnight here
# assuming there are fewer bike rides at midnight.  There's a Better Way to do
# this, but I'm just making easy baby steps).  

# Filtering first, between 7:00 and 19:00 (7am to 7pm).  Note: want to do
# pythonic 7 <= col <= 19, but cannot overload the double-ended range operator
# (probably turns into a boolean operator over 2 exprs, but cannot overload the
# boolean)
df3 = df2[ (7 <= df2["Hour Local"]) & (df2["Hour Local"] <= 19)]
df3.describe()
# A quick check at the row count shows we chopped out about half the rows
# (since we chopped half the hours), and the min and max for the Hour column is
# in the range from 7am to 7pm.

# Lets now get Days since the epoch... we'll convert year/month/day into Epoch
# time, and then back to Epoch days.  Need zero-based month and days, but have
# 1-based.
df3["msec"] = h2o.H2OVec.mktime(year=df3["Year Local"], month=df3["Month Local"]-1, day=df3["Day Local"]-1, hour=df3["Hour Local"])
secsPerDay=1000*60*60*24
df3["Epoch Day"] = (df3["msec"]/secsPerDay).floor()
df3.describe()
# msec looks sane (numbers like 1.3e12 are in the correct range for msec since
# 1970).  Epoch Day matches closely with the epoch day numbers from the
# CitiBike dataset.  

# Lets drop off the extra time columns to make a easy-to-handle dataet.
df4 = df3.drop("Year Local").drop("Month Local").drop("Day Local").drop("Hour Local").drop("msec")
df4.describe()
