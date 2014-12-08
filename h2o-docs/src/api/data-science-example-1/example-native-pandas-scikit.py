# -*- coding: utf-8 -*-
# <nbformat>3.0</nbformat>

# <codecell>

from pandas import Series, DataFrame
import pandas as pd
import numpy as np

import sklearn
from sklearn.ensemble import GradientBoostingClassifier
from sklearn import preprocessing

# <codecell>

air_raw = DataFrame.from_csv("allyears_tiny.csv", index_col = False)
print(air_raw.head())

air_raw['RandNum'] = Series(np.random.uniform(size = len(air_raw['Origin'])))
print(air_raw.head())

# <codecell>

air_mapped = DataFrame()

air_mapped['RandNum'] = air_raw['RandNum']

air_mapped['IsDepDelayed'] = air_raw['IsDepDelayed']
air_mapped['IsDepDelayedInt'] = air_mapped.apply(lambda row:
                                                 1 if row['IsDepDelayed'] == 'YES' else 0,
                                                 axis=1)
del air_mapped['IsDepDelayed']
print(air_mapped.shape)

lb_origin = sklearn.preprocessing.LabelBinarizer()
lb_origin.fit(air_raw['Origin'])
tmp_origin = lb_origin.transform(air_raw['Origin'])
tmp_origin_df = DataFrame(tmp_origin)
print(tmp_origin_df.shape)

lb_dest = sklearn.preprocessing.LabelBinarizer()
lb_dest.fit(air_raw['Dest'])
tmp_dest = lb_origin.transform(air_raw['Dest'])
tmp_dest_df = DataFrame(tmp_dest)
print(tmp_dest_df.shape)

lb_uniquecarrier = sklearn.preprocessing.LabelBinarizer()
lb_uniquecarrier.fit(air_raw['UniqueCarrier'])
tmp_uniquecarrier = lb_origin.transform(air_raw['UniqueCarrier'])
tmp_uniquecarrier_df = DataFrame(tmp_uniquecarrier)
print(tmp_uniquecarrier_df.shape)

air_mapped = pd.concat([
                        air_mapped, 
                        tmp_origin_df, 
                        tmp_dest_df, 
                        air_raw['Distance'],
                        tmp_uniquecarrier_df, 
                        air_raw['Month'],
                        air_raw['DayofMonth'],
                        air_raw['DayOfWeek'],
                        ],
                       axis=1)
print(air_mapped.shape)
air_mapped

air = air_mapped

# <codecell>

air_train = air.ix[air['RandNum'] <= 0.8]
# air_valid = air.ix[(air['RandNum'] > 0.8) & (air['RandNum'] <= 0.9)]
air_test  = air.ix[air['RandNum'] > 0.9]

print(air_train.shape)
print(air_test.shape)

# <codecell>

X_train = air_train.copy(deep=True)
del X_train['RandNum']
del X_train['IsDepDelayedInt']
print(list(X_train.columns.values))
print(X_train.shape)

y_train = air_train['IsDepDelayedInt']
print(y_train.shape)

# <codecell>

clf = GradientBoostingClassifier(n_estimators = 10, max_depth = 3, learning_rate = 0.01)
clf.fit(X_train, y_train)

# <codecell>

X_test = air_test.copy(deep=True)
del X_test['RandNum']
del X_test['IsDepDelayedInt']
print(list(X_test.columns.values))
print(X_test.shape)

print("")
print("--- PREDICTIONS ---")
print("")
pred = clf.predict(X_test)
print(pred)

