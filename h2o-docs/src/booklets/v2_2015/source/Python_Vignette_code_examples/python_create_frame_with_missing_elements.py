df3 = h2o.H2OFrame.from_python(
    {'A': [1, 2, 3,None,''],                          
     'B': ['a', 'a', 'b', 'NA', 'NA'],
     'C': ['hello', 'all', 'world', None, None],
     'D': ['12MAR2015:11:00:00',None,
           '13MAR2015:12:00:00',None,
           '14MAR2015:13:00:00']},   
    column_types=['numeric', 'enum', 'string', 'time'])