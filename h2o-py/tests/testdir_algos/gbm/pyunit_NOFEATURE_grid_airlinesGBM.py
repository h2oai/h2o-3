

import h2o, tests

def grid_airlinesGBM():
    
    

    air =  h2o.import_file(path=tests.locate("smalldata/airlines/allyears2k_headers.zip"))
    #air.summary()
    myX = ["DayofMonth", "DayOfWeek"]
    air_grid = h2o.gbm(y=air["IsDepDelayed"], x=air[myX],
                   distribution="bernoulli",
                   ntrees=[5,10,15],
                   max_depth=[2,3,4],
                   learn_rate=[0.1,0.2])
    air_grid.show()


pyunit_test = grid_airlinesGBM
