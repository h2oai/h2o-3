In [5]: df5 = h2o.H2OFrame.from_python(
          np.random.randn(4,100).tolist(), 
          column_names=list('ABCD'))
Parse Progress: [###############################] 100%

In [6]: df5.apply(lambda x: x.mean(na_rm=True))
Out[6]: H2OFrame with 1 rows and 4 columns:
          A         B         C        D
0  0.020849 -0.052978 -0.037272 -0.01664