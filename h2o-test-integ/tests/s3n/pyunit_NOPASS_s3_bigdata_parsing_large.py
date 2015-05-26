import sys, os, timeit,time
sys.path.insert(1, "../../../h2o-py")
import h2o

def s3timings(ip, port):
  t = time.time()
  # connect to cluster
  h2o.init(ip, port)

  # defining timers
  air_run = timeit.Timer(stmt = 'h2o.import_frame("s3n://h2o-airlines-unpacked/allyears.1987.2013.csv")',
    setup = 'import h2o')
  bigx_run = timeit.Timer(stmt = 'h2o.import_frame("s3://h2o-public-test-data/bigdata/server/flow-tests/BigCross.data")',
    setup = 'import h2o')
  higg_run = timeit.Timer(stmt = 'h2o.import_frame("s3://h2o-public-test-data/bigdata/server/HIGGS.csv")',
    setup = 'import h2o')
  citi_run = timeit.Timer(stmt = 'h2o.import_frame(path = big_citi)',
    setup = 'import h2o;\
             big_citi = ["s3://h2o-public-test-data/bigdata/laptop/citibike-nyc/2013-07.csv",\
                          "s3://h2o-public-test-data/bigdata/laptop/citibike-nyc/2013-08.csv",\
                          "s3://h2o-public-test-data/bigdata/laptop/citibike-nyc/2013-09.csv",\
                          "s3://h2o-public-test-data/bigdata/laptop/citibike-nyc/2013-10.csv",\
                          "s3://h2o-public-test-data/bigdata/laptop/citibike-nyc/2013-11.csv",\
                          "s3://h2o-public-test-data/bigdata/laptop/citibike-nyc/2013-12.csv",\
                          "s3://h2o-public-test-data/bigdata/laptop/citibike-nyc/2014-01.csv",\
                          "s3://h2o-public-test-data/bigdata/laptop/citibike-nyc/2014-02.csv",\
                          "s3://h2o-public-test-data/bigdata/laptop/citibike-nyc/2014-03.csv",\
                          "s3://h2o-public-test-data/bigdata/laptop/citibike-nyc/2014-04.csv",\
                          "s3://h2o-public-test-data/bigdata/laptop/citibike-nyc/2014-05.csv",\
                          "s3://h2o-public-test-data/bigdata/laptop/citibike-nyc/2014-06.csv",\
                          "s3://h2o-public-test-data/bigdata/laptop/citibike-nyc/2014-07.csv",\
                          "s3://h2o-public-test-data/bigdata/laptop/citibike-nyc/2014-08.csv"]')
  mils_run = timeit.Timer(stmt = 'h2o.import_frame(path = mill_songs)',
    setup = 'import h2o;\
             mill_songs = ["s3://h2o-public-test-data/bigdata/server/milsongs/milsongs-test.csv",\
                           "s3://h2o-public-test-data/bigdata/server/milsongs/milsongs-train.csv"]')
  cup_run = timeit.Timer(stmt = 'h2o.import_frame(path = cup98)',
    setup = 'import h2o;\
             cup98 = ["s3://h2o-public-test-data/bigdata/laptop/usecases/cup98LRN_z.csv",\
                      "s3://h2o-public-test-data/bigdata/laptop/usecases/cup98VAL_z.csv"]')
  mnist_run = timeit.Timer(stmt  = 'h2o.import_frame(path = mnist)',
    setup = 'import h2o;\
             mnist = ["s3://h2o-public-test-data/bigdata/laptop/mnist/test.csv.gz",\
                      "s3://h2o-public-test-data/bigdata/laptop/mnist/train.csv.gz"]')
  arc_run = timeit.Timer(stmt = 'h2o.import_frame(path = arcene)',
    setup = 'import h2o;\
             arcene = ["s3://h2o-public-test-data/smalldata/arcene/arcene_test.data",\
                       "s3://h2o-public-test-data/smalldata/arcene/arcene_train.data",\
                       "s3://h2o-public-test-data/smalldata/arcene/arcene_valid.data"]')

  # Running with timers
  air_first   =   air_run.timeit(number=1)
  bigx_first  =  bigx_run.timeit(number=1)
  higg_first  =  higg_run.timeit(number=1)
  citi_first  =  citi_run.timeit(number=1)
  mils_first  =  mils_run.timeit(number=1)
  cup_first   =   cup_run.timeit(number=1)
  mnist_first = mnist_run.timeit(number=1)
  arc_first   =   arc_run.timeit(number=1)

  # Clear kvstore and run again
  s = time.time()
  h2o.remove_all()
  print "Elapsed Time for RemoveAll: " + str(time.time() - s) + " (s)."
  air_second   =   air_run.timeit(number=1)
  bigx_second  =  bigx_run.timeit(number=1)
  higg_second  =  higg_run.timeit(number=1)
  citi_second  =  citi_run.timeit(number=1)
  mils_second  =  mils_run.timeit(number=1)
  cup_second   =   cup_run.timeit(number=1)
  mnist_second = mnist_run.timeit(number=1)
  arc_second   =   arc_run.timeit(number=1)
  print("Airlines: " + str(air_first) + " vs " + str(air_second))
  print("BigCross: " + str(bigx_first) + " vs " + str(bigx_second))
  print("Higgs: " + str(higg_first) + " vs " + str(higg_second))
  print("Citi_bikes: " + str(citi_first) + " vs " + str(citi_second))
  print("Million Songs: " + str(mils_first) + " vs " + str(mils_second))
  print("KDD Cup98: " + str(cup_first) + " vs " + str(cup_second))
  print("Mnist: " + str(mnist_first) + " vs " + str(mnist_second))
  print("Arcene: " + str(arc_first) + " vs " + str(arc_second))
  s = time.time()
  h2o.remove_all()
  print "Elapsed Time for RemoveAll: " + str(time.time() - s) + " (s)."
  print "Exiting scope... Test elapsed time: " + str(time.time() - t) + " (s)."

if __name__ == "__main__":
  h2o.run_test(sys.argv, s3timings)