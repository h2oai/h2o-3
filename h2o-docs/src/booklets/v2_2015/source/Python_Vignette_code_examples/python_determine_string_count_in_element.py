In [62]: df7 = h2o.H2OFrame.from_python(
  ['Hello', 'World', 'Welcome', 'To', 'H2O', 'World'])

In [63]: df7
Out[63]: 
   C1     C2     C3       C4    C5    C6
   -----  -----  -------  ----  ----  -----
0  Hello  World  Welcome  To    H2O   World

[1 row x 6 columns]


In [65]: df7.countmatches('l')
Out[65]: 
    C1    C2    C3    C4    C5    C6
  ----  ----  ----  ----  ----  ----
0    2     1     1     0     0     1

[1 row x 6 columns]