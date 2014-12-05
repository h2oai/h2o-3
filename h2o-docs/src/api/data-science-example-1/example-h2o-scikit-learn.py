#!/usr/bin/env python

from h2o import H2OFrame, H2OModel
import h2o as h2o

localH2O = h2o.init()
air = H2OFrame.from_csv(localH2O, "allyears_tiny.csv", index_col = False)
air.head().print()

X_air = air['Origin', 'Dest', 'Distance', 'UniqueCarrier', 'Month', 'DayofMonth', 'DayOfWeek']
y_air = air['IsDepDelayed']

X_air_train, X_air_valid, X_air_test, y_air_train, y_air_valid, y_air_test = \
  H2OFrame.train_valid_test(X_air, y_air, valid_size = 0.1, test_size = 0.1)

my_gbm = H2OModel.GBM(distribution = "multinomial", n_trees = 10,
                      interaction_depth = 3, shrinkage = 0.01,
                      importance = True)
air_gbm = my_gbm.fit(x=X_air_train, y=y_air_train, x_valid=X_air_valid, y_valid=y_air_valid)
air_gbm.print()

pred = air_gbm.predict(X_air_test)
pred.head().print()
