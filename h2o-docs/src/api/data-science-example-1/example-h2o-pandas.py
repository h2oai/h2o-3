#!/usr/bin/env python

from h2o import H2OFrame
import h2o as h2o

localH2O = h2o.init()
air = H2OFrame.from_csv(localH2O, "allyears_tiny.csv", index_col = False)
print(air.head())

air['RandNum'] = air.random.uniform()
print(air.head())

air_train = air.ix[air['RandNum'] <= 0.8]
air_valid = air.ix[(air['RandNum'] > 0.8) & (air['RandNum'] <= 0.9)]
air_test  = air.ix[air['RandNum'] > 0.9]

myX = ["Origin", "Dest", "Distance", "UniqueCarrier", "Month", "DayofMonth", "DayOfWeek"]
myY = "IsDepDelayed"

air_gbm = h2o.gbm(x = myX, y = myY, data = air_train, validation = air_valid,
                  distribution = "multinomial",
                  n_trees = 10, interaction_depth = 3, shrinkage = 0.01,
                  importance = True)
print(air_gbm)

pred = h2o.predict(air_gbm, air_test)
print(pred.head())
