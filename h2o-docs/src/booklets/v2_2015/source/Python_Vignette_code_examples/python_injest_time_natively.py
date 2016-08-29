df14 = h2o.H2OFrame.from_python(
       {'D': ['18OCT2015:11:00:00',
       '19OCT2015:12:00:00',
       '20OCT2015:13:00:00']},
       column_types=['time'])

df14.types
# {u'D': u'time'}