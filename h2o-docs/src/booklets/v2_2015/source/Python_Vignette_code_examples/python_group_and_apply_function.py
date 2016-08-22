In [123]: df12 = h2o.H2OFrame(
    {'A' : ['foo', 'bar', 'foo', 'bar',
            'foo', 'bar', 'foo', 'foo'],
     'B' : ['one', 'one', 'two', 'three',
            'two', 'two', 'one', 'three'],
     'C' : np.random.randn(8),
     'D' : np.random.randn(8)})

Parse Progress: [###############################] 100%
Uploaded pyd297bab5-4e4e-4a89-9b85-f8fecf37f264 into cluster with 8 rows and 4 cols

In [124]: df12
Out[124]: H2OFrame with 8 rows and 4 columns:
     A         C      B         D
0  foo  1.583908    one -0.441779
1  bar  1.055763    one  1.733467
2  foo -1.200572    two  0.970428
3  bar -1.066722  three -0.311055
4  foo -0.023385    two  0.077905
5  bar  0.758202    two  0.521504
6  foo  0.098259    one -1.391587
7  foo  0.412450  three -0.050374

In [125]: df12.group_by('A').sum().frame
Out[125]: H2OFrame with 2 rows and 4 columns:
     A     sum_C  sum_B     sum_D
0  bar  0.747244      3  1.943915
1  foo  0.870661      5 -0.835406