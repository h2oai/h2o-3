import sys
sys.path.insert(1, "../../../")
import h2o

def frame_as_list(ip,port):
  # Connect to h2o
  h2o.init(ip,port)

  prostate = h2o.import_frame(path=h2o.locate("smalldata/prostate/prostate.csv.zip"))

  print (prostate % 10).show()
  print (prostate[4] % 10).show()

if __name__ == "__main__":
  h2o.run_test(sys.argv, frame_as_list)
