import sys
sys.path.insert(1, "../../../")
import h2o

def mnist_manyCols_largeGBM(ip,port):
    
    

    #Log.info("Importing mnist train data...\n")
    train = h2o.import_frame(path=h2o.locate("bigdata/laptop/mnist/train.csv.gz"))
    #Log.info("Check that tail works...")
    train.tail()

    #Log.info("Doing gbm on mnist training data.... \n")
    gbm_mnist = h2o.gbm(x=train[0:784], y=train[784], ntrees=1, max_depth=1, min_rows=10, learn_rate=0.01)
    gbm_mnist.show()

if __name__ == "__main__":
    h2o.run_test(sys.argv, mnist_manyCols_largeGBM)
