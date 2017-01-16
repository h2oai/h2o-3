from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glrm import H2OGeneralizedLowRankEstimator
from h2o.two_dim_table import H2OTwoDimTable

def glrm_catagorical_bug_fix():
  print("Importing prostate.csv data...")

  tbl2 = H2OTwoDimTable(cell_values=[[1, 2, 4]] * 10, col_header=["q1", "q2", "q3"], row_header=range(10),
                        table_header="Table 2")

  # H2OTwoDimTable containing the correct archetype values run before Wendy optimized memory for GLRM
  cell_values = [['Arch1', 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 0.0, 1.0, 0.0, 58.295918367346935,
                  8.810102040816325, 11.344897959183678, 6.285714285714286],
                 ['Arch2', 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0, 1.0, 0.0, 1.0, 0.0, 69.35514018691589, 7.538224299065424,
                  10.087757009345797, 5.6168224299065415],
                 ['Arch3', 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 1.0, 0.0, 64.68, 75.892, 10.812000000000001,
                  7.44],
                 ['Arch4', 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 0.0, 1.0, 0.0, 68.77083333333333, 13.368750000000002,
                  49.44583333333334, 5.9375],
                 ['Arch5', 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 0.0, 1.0, 1.0, 0.0, 69.04901960784314, 16.140196078431373,
                  11.510000000000005, 7.235294117647059]]
  col_header = ['dprosboth', 'dprosleft', 'dprosnone', 'dprosright', 'raceblack', 'racena', 'racewhite', 'capsuleno',
                'capsuleyes', 'dcapsno', 'dcapsyes', 'age', 'psa', 'vol', 'gleason']
  row_header = ['Arch1', 'Arch2', 'Arch3', 'Arch4', 'Arch5']
  table_header = "archetypes"
  correct_archetype = H2OTwoDimTable(cell_values=cell_values, col_header=col_header, row_header=row_header,
                                     table_header=table_header)

  prostateF = h2o.upload_file(pyunit_utils.locate("smalldata/prostate/prostate_cat.csv"))

  glrm_h2o = H2OGeneralizedLowRankEstimator(k=5, recover_svd=True, seed=1234)
  glrm_h2o.train(x=prostateF.names, training_frame=prostateF)
  glrm_h2o.show()

  assert pyunit_utils.equal_2D_tables(glrm_h2o._model_json["output"]["archetypes"]._cell_values,
                                      correct_archetype._cell_values, tolerance=1e-4), \
      "GLRM model archetypes generated from current model are not correct."

if __name__ == "__main__":
  pyunit_utils.standalone_test(glrm_catagorical_bug_fix)
else:
  glrm_catagorical_bug_fix()
