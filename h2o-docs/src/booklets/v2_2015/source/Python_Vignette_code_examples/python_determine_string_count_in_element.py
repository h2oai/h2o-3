In [62]: df7 = h2o.H2OFrame.from_python(
  ['Hello', 'World', 'Welcome', 'To', 'H2O', 'World'])

In [63]: df7
Out[63]: H2OFrame with 6 rows and 1 columns:
        C1
0    Hello
1    World
2  Welcome
3       To
4      H2O
5    World

In [65]: df7.countmatches('l')
Out[65]: H2OFrame with 6 rows and 1 columns:
   C1
0   2
1   1
2   1
3   0
4   0
5   1