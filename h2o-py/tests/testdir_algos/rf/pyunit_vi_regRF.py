import sys
sys.path.insert(1, "../../../")
import h2o

def vi_reg(ip,port):
    # Connect to h2o
    h2o.init(ip,port)

    data = h2o.import_frame(path=h2o.locate("smalldata/gbm_test/BostonHousing.csv"))
    #data.summary()

    rf = h2o.random_forest(x=data[0:13], y=data[13], ntrees=100, max_depth=20, nbins=100, seed=0)

    ranking = [rf._model_json['output']['variable_importances'].cell_values[v][0] for v in range(data.ncol()-1)]
    print(ranking)
    assert [ranking[0],ranking[1]] == ["rm","lstat"], "expected specific variable importance ranking"

if __name__ == "__main__":
  h2o.run_test(sys.argv, vi_reg)
