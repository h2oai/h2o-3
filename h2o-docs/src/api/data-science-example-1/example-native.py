#!/usr/bin/env python

from pandas import Series, DataFrame
import pandas as pd
import numpy as np

air = DataFrame.from_csv("allyears_tiny.csv", index_col = False)
print(air.head())

air['RandNum'] = Series(np.random.uniform(size = len(air['Origin'])))
print(air.head())

air_train = air.ix[air['RandNum'] <= 0.8]
air_valid = air.ix[(air['RandNum'] > 0.8) & (air['RandNum'] <= 0.9)]
air_test  = air.ix[air['RandNum'] > 0.9]
