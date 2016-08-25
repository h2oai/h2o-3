In [123]: df12 = h2o.H2OFrame(
    {'A' : ['foo', 'bar', 'foo', 'bar',
            'foo', 'bar', 'foo', 'foo'],
     'B' : ['one', 'one', 'two', 'three',
            'two', 'two', 'one', 'three'],
     'C' : np.random.randn(8).tolist(),
     'D' : np.random.randn(8).tolist()})

Parse Progress: [###############################] 100%

In [124]: df12
Out[124]: 
   A             C  B                D
   ---  ----------  -----  -----------
0  foo  -0.710095   one     0.253189
1  bar  -0.165891   one    -0.433233
2  foo  -1.51996    two     1.12321
3  bar   2.25083    three   0.512449
4  foo  -0.618324   two     1.35158
5  bar   0.0817828  two     0.00830419
6  foo   0.634827   one     1.25897
7  foo   0.879319   three   1.48051

[8 rows x 4 columns]

In [125]: df12.group_by('A').sum().frame
Out[125]: 
   A       sum_C    sum_B      sum_D
   ---  --------  -------  ---------
0  bar   2.16672        3  0.0875206
1  foo  -1.33424        5  5.46746

[2 rows x 4 columns]