import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils


def missing():
    # Connect to a pre-existing cluster
    

    missing_ratios = [0, 0.1, 0.25, 0.5, 0.75, 0.99]
    errors = [0, 0, 0, 0, 0, 0]

    for i in range(len(missing_ratios)):
        data = h2o.upload_file(pyunit_utils.locate("smalldata/junit/weather.csv"))
        data[15] = data[15].asfactor() #ChangeTempDir
        data[16] = data[16].asfactor() #ChangeTempMag
        data[17] = data[17].asfactor() #ChangeWindDirect
        data[18] = data[18].asfactor() #MaxWindPeriod
        data[19] = data[19].asfactor() #RainToday
        data[21] = data[21].asfactor() #PressureChange
        data[23] = data[23].asfactor() #RainTomorrow

        print "For missing {0}%".format(missing_ratios[i]*100)

        # add missing values to the data section of the file (leave the response alone)
        if missing_ratios[i] > 0:
            resp = data[23]
            pred = data[:,range(23)+range(24,data.ncol)]
            data_missing = pred.insert_missing_values(fraction=missing_ratios[i])
            data_fin = data_missing.cbind(resp)
        else:
            data_fin = data

        # split into train + test datasets
        ratio = data_fin[0].runif()
        train = data_fin[ratio <= .75]
        test  = data_fin[ratio >  .75]

        hh = h2o.deeplearning(x=train[2:22], y=train[23], validation_x=test[2:22], validation_y=test[23], epochs=5,
                            reproducible=True, seed=12345, activation='RectifierWithDropout', l1=1e-5,
                            input_dropout_ratio=0.2)

        errors[i] = hh.error()[0][1]

    for i in range(len(missing_ratios)):
        print "missing ratio: {0}% --> classification error: {1}".format(missing_ratios[i]*100, errors[i])

    assert sum(errors) < 2.2, "Sum of classification errors is too large!"



if __name__ == "__main__":
    pyunit_utils.standalone_test(missing)
else:
    missing()
