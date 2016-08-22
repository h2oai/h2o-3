In [49]: df6 = h2o.H2OFrame(
      np.random.randint(0, 7, size=100).tolist())

Parse Progress: [###############################] 100%
Uploaded py5b584604-73ff-4037-9618-c53122cd0343 into cluster with 100 rows and 1 cols

In [50]: df6.hist(plot=False)

Parse Progress: [###############################] 100%
Uploaded py8a993d29-e354-44cf-b10e-d97aa6fdfd74 into cluster with 8 rows and 1 cols
Out[50]: H2OFrame with 8 rows and 5 columns:
   breaks  counts  mids_true   mids   density
0    0.75     NaN        NaN    NaN  0.000000
1    1.50      10        0.0  1.125  0.116667
2    2.25       6        0.5  1.875  0.070000
3    3.00      17        1.0  2.625  0.198333
4    3.75       0        0.0  3.375  0.000000
5    4.50      16        1.5  4.125  0.186667
6    5.25      19        2.0  4.875  0.221667
7    6.00      32        2.5  5.625  0.373333