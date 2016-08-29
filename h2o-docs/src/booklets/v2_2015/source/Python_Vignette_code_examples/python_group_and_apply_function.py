df12 = h2o.H2OFrame(
     {'A' : ['foo', 'bar', 'foo', 'bar', 'foo', 'bar', 'foo', 'foo'],
     'B' : ['one', 'one', 'two', 'three', 'two', 'two', 'one', 'three'],
     'C' : np.random.randn(8).tolist(),
     'D' : np.random.randn(8).tolist()})

# View the H2OFrame
df12

#  A             C  B                D
#  ---  ----------  -----  -----------
#  foo  -0.710095   one     0.253189
#  bar  -0.165891   one    -0.433233
#  foo  -1.51996    two     1.12321
#  bar   2.25083    three   0.512449
#  foo  -0.618324   two     1.35158
#  bar   0.0817828  two     0.00830419
#  foo   0.634827   one     1.25897
#  foo   0.879319   three   1.48051
#
# [8 rows x 4 columns]

df12.group_by('A').sum().frame

#  A       sum_C    sum_B      sum_D
#  ---  --------  -------  ---------
#  bar   2.16672        3  0.0875206
#  foo  -1.33424        5  5.46746
#
# [2 rows x 4 columns]