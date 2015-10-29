import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils




import random
import numpy as np
from sklearn import preprocessing
from sklearn.cluster import KMeans

def emptyclusKmeans():
    # Connect to a pre-existing cluster
      # connect to localhost:54321

    #Log.info("Importing ozone.csv data...\n")
    ozone_sci = np.loadtxt(pyunit_utils.locate("smalldata/glm_test/ozone.csv"), delimiter=',', skiprows=1)
    ozone_h2o = h2o.import_file(path=pyunit_utils.locate("smalldata/glm_test/ozone.csv"))

    ncent = 10
    nempty = random.randint(1,ncent/2)
    initial_centers = [[41,190,67,7.4],
                       [36,118,72,8],
                       [12,149,74,12.6],
                       [18,313,62,11.5],
                       [23,299,65,8.6],
                       [19,99,59,13.8],
                       [8,19,61,20.1],
                       [16,256,69,9.7],
                       [11,290,66,9.2],
                       [14,274,68,10.9]]
    for i in random.sample(range(0,ncent-1), nempty):
        initial_centers[i] = [100*i for z in range(1,len(initial_centers[0])+1)]

    initial_centers_sci = np.asarray(initial_centers)
    initial_centers = zip(*initial_centers)

    initial_centers_h2o = h2o.H2OFrame(initial_centers)


    #Log.info("Initial cluster centers:")
    print "H2O initial centers:"
    initial_centers_h2o.show()
    print "scikit initial centers:"
    print initial_centers_sci

    # H2O can handle empty clusters and so can scikit
    #Log.info("Check that H2O can handle badly initialized centers")
    km_sci = KMeans(n_clusters=ncent, init=initial_centers_sci, n_init=1)
    km_sci.fit(preprocessing.scale(ozone_sci))
    print "scikit final centers"
    print km_sci.cluster_centers_

    km_h2o = h2o.kmeans(x=ozone_h2o, k=ncent, user_points=initial_centers_h2o, standardize=True)
    print "H2O final centers"
    print km_h2o.centers()



if __name__ == "__main__":
    pyunit_utils.standalone_test(emptyclusKmeans)
else:
    emptyclusKmeans()
