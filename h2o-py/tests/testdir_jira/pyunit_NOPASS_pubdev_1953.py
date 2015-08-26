import sys
sys.path.insert(1, "../../")
import h2o, tests

def pubdev_1953(ip, port):

    # small_test = [h2o.locate("bigdata/laptop/citibike-nyc/2013-10.csv")]
    # data = h2o.import_file(path=small_test)
    # startime = data["starttime"]
    # secsPerDay=1000*60*60*24
    # data["Days"] = (startime/secsPerDay).floor()
    # grouped = data.group_by(["Days","start station name"])
    # bpd = grouped.count(name="bikes").get_frame()
    # secs = bpd["Days"]*secsPerDay
    # bpd["Month"]     = secs.month().asfactor()
    # bpd["DayOfWeek"] = secs.dayOfWeek()
    # wthr1 = h2o.import_file(path=[h2o.locate("bigdata/laptop/citibike-nyc/31081_New_York_City__Hourly_2013.csv"), h2o.locate("bigdata/laptop/citibike-nyc/31081_New_York_City__Hourly_2014.csv")])
    # wthr2 = wthr1[["Year Local","Month Local","Day Local","Hour Local","Dew Point (C)","Humidity Fraction","Precipitation One Hour (mm)","Temperature (C)","Weather Code 1/ Description"]]
    # wthr2.setName(wthr2.index("Precipitation One Hour (mm)"), "Rain (mm)")
    # wthr2.setName(wthr2.index("Weather Code 1/ Description"), "WC1")
    # wthr3 = wthr2[ wthr2["Hour Local"]==12 ]
    # wthr3["msec"] = h2o.H2OFrame.mktime(year=wthr3["Year Local"], month=wthr3["Month Local"]-1, day=wthr3["Day Local"]-1, hour=wthr3["Hour Local"])
    # secsPerDay=1000*60*60*24
    # wthr3["Days"] = (wthr3["msec"]/secsPerDay).floor()
    # wthr4 = wthr3.drop("Year Local").drop("Month Local").drop("Day Local").drop("Hour Local").drop("msec")
    # rain = wthr4["Rain (mm)"]
    # rain[ rain.isna() ] = 0
    # bpd_with_weather = bpd.merge(wthr4,allLeft=True,allRite=False)
    # r = bpd_with_weather['Days'].runif(seed=356964763)
    # train = bpd_with_weather[  r  < 0.6]
    # test  = bpd_with_weather[(0.6 <= r) & (r < 0.9)]

    predictors = ['DayOfWeek', 'WC1', 'start station name', 'Temperature (C)', 'Days', 'Month', 'Humidity Fraction', 'Rain (mm)', 'Dew Point (C)']

    train = h2o.import_file(h2o.locate("smalldata/glm_test/citibike_small_train.csv"))
    test = h2o.import_file(h2o.locate("smalldata/glm_test/citibike_small_test.csv"))

    glm0 = h2o.glm(x=train[predictors], y=train["bikes"], validation_x=test[predictors], validation_y=test["bikes"], family="poisson")

if __name__ == "__main__":
    tests.run_test(sys.argv, pubdev_1953)
