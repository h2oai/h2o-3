import sys
sys.path.insert(1, "../../../")
import h2o

def grid_wineGBM(ip,port):
    
    

    wine = h2o.import_file(path=h2o.locate("smalldata/gbm_test/wine.data"))
    #wine.summary()
    x_cols = range(2,14) + [0]
    wine_grid = h2o.gbm(y=wine[1],
                        x=wine[x_cols],
                        distribution='gaussian',
                        ntrees=[5,10,15],
                        max_depth=[2,3,4],
                        learn_rate=[0.1,0.2])
    wine_grid.show()

if __name__ == "__main__":
    h2o.run_test(sys.argv, grid_wineGBM)
