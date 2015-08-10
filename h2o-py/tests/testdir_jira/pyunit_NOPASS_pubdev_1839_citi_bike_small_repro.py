import sys
sys.path.insert(1, "../../")
import h2o

def pubdev_1839(ip, port):

    data = h2o.import_frame(h2o.locate("bigdata/laptop/citibike-nyc/2013-10.csv"))
    startime = data["starttime"]
    secsPerDay=1000*60*60*24
    data["Days"] = (startime/secsPerDay).floor()

    group_by_cols = ["Days","start station name"]
    aggregates = {"bikes": ["count", 0, "all"]}
    bpd = data.group_by(cols=group_by_cols, aggregates=aggregates) # Compute bikes-per-day

    print "Quantiles of bikes-per-day"
    bpd["bikes"].quantile().show()

    secs = bpd["Days"]*secsPerDay
    bpd["Month"]     = secs.month().asfactor()

    bpd["DayOfWeek"] = secs.dayOfWeek()
    print "Bikes-Per-Day"
    bpd.describe()

    def split_fit_predict(data):
        global gbm0,drf0,glm0,dl0
        r = data['Days'].runif()
        train = data[  r  < 0.6]
        test  = data[(0.6 <= r) & (r < 0.9)]
        hold  = data[ 0.9 <= r ]

        tr = h2o.as_list(train, use_pandas=False)
        te = h2o.as_list(test, use_pandas=False)
        h = h2o.as_list(hold, use_pandas=False)

        print "Training data has",train.ncol(),"columns and",train.nrow(),"rows, test has",test.nrow(),"rows, holdout has",hold.nrow()
        print "Training data: "
        for r in tr: print r
        print "Test data: "
        for r in te: print r
        print "Hold data: "
        for r in h: print r

        glm0 = h2o.glm(x           =train.drop("bikes"),
                       y           =train     ["bikes"],
                       validation_x=test .drop("bikes"),
                       validation_y=test      ["bikes"],
                       Lambda=[1e-5],
                       family="poisson")

    split_fit_predict(bpd)

    wthr1 = h2o.import_frame(path=[h2o.locate("bigdata/laptop/citibike-nyc/31081_New_York_City__Hourly_2013.csv"),
                                   h2o.locate("bigdata/laptop/citibike-nyc/31081_New_York_City__Hourly_2014.csv")])

    wthr2 = wthr1[["Year Local","Month Local","Day Local","Hour Local","Dew Point (C)","Humidity Fraction","Precipitation One Hour (mm)","Temperature (C)","Weather Code 1/ Description"]]

    wthr2.setName(wthr2.index("Precipitation One Hour (mm)"), "Rain (mm)")
    wthr2.setName(wthr2.index("Weather Code 1/ Description"), "WC1")

    wthr3 = wthr2[ wthr2["Hour Local"]==12 ]
    wthr3["msec"] = h2o.H2OFrame.mktime(year=wthr3["Year Local"], month=wthr3["Month Local"]-1, day=wthr3["Day Local"]-1, hour=wthr3["Hour Local"])
    secsPerDay=1000*60*60*24
    wthr3["Days"] = (wthr3["msec"]/secsPerDay).floor()

    wthr4 = wthr3.drop("Year Local").drop("Month Local").drop("Day Local").drop("Hour Local").drop("msec")

    rain = wthr4["Rain (mm)"]
    rain[ rain.isna() ] = 0

    print "Merge Daily Weather with Bikes-Per-Day"
    bpd_with_weather = bpd.merge(wthr4,allLeft=True,allRite=False)
    bpd_with_weather.describe()
    bpd_with_weather.show()

    split_fit_predict(bpd_with_weather)

if __name__ == "__main__":
    h2o.run_test(sys.argv, pubdev_1839)
