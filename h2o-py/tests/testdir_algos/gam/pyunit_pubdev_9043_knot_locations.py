import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator

# in this test, want to check that knot locations are returned correct.
def test_gam_knot_locations():
    h2o_data = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/binomial_20_cols_10KRows.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    h2o_data["C21"] = h2o_data["C21"].asfactor()

    knotsC11 = [-1.999662934845682, -0.008421144219463272, 1.999459888242264]
    frameKnotC11 = h2o.H2OFrame(python_obj=knotsC11)
    knotsC12 = [-1.9997515661932348, -0.6738945321313676, 0.6508273358344479, 1.9992225172858493]
    frameKnotC12 = h2o.H2OFrame(python_obj=knotsC12)
    knotsC13 = [-1.999891109720008, -0.9927241013095163, 0.02801505726801068, 1.033088395720594, 1.9999726397528468]
    frameKnotC13 = h2o.H2OFrame(python_obj=knotsC13)
    knotsC145 = [[-1.9995687220225768, -1.3179329594362712, -0.6660494855259786, 0.018720676310811868, 
                  0.6698634783003867, 1.3416090970090029], [0.59241038898347, -0.5351329568631273, -0.5172803270321329,
                                                            0.3428670679538648, -1.6541548995899171, -1.8415195601489895]]
    temp = [list(x) for x in zip(*knotsC145)]
    frameKnotC145 = h2o.H2OFrame(python_obj=temp)
    usedGam = ["C11", "C12", "C13", "C14", "C15"]
    gam = H2OGeneralizedAdditiveEstimator(family='binomial', gam_columns=["C11", "C12", "C13", ["C14", "C15"]],  
                                          knot_ids = [frameKnotC11.key, frameKnotC12.key, frameKnotC13.key, frameKnotC145.key],
                                          bs=[0,2,3,1], standardize=True, lambda_=[0], alpha=[0], max_iterations=1, 
                                          store_knot_locations=True)
    gam.train(x=["C1","C2"], y="C21", training_frame=h2o_data)
    # check and make sure the returned knot locations and knot gam names are correct
    allKnots = gam.get_knot_locations()
    c11Knots = gam.get_knot_locations("C11")
    assert pyunit_utils.equal_two_arrays(allKnots[0], c11Knots)
    assert pyunit_utils.equal_two_arrays(knotsC11, c11Knots)
    c12Knots = gam.get_knot_locations("C12")
    assert pyunit_utils.equal_two_arrays(allKnots[1], c12Knots)
    assert pyunit_utils.equal_two_arrays(knotsC12, c12Knots)
    c13Knots = gam.get_knot_locations("C13")
    assert pyunit_utils.equal_two_arrays(allKnots[2], c13Knots)
    assert pyunit_utils.equal_two_arrays(knotsC13, c13Knots)
    c14Knots = gam.get_knot_locations("C14")
    assert pyunit_utils.equal_two_arrays(allKnots[3], c14Knots)
    assert pyunit_utils.equal_two_arrays(knotsC145[0], c14Knots)
    c15Knots = gam.get_knot_locations("C15")
    assert pyunit_utils.equal_two_arrays(allKnots[4], c15Knots)
    assert pyunit_utils.equal_two_arrays(knotsC145[1], c15Knots)
    gam_knot_columns = gam.get_gam_knot_column_names()
    gam_knot_columns.sort()
    usedGam.sort()
    assert gam_knot_columns == usedGam, "Expected array: {0}, actual: {1}".format(usedGam, gam_knot_columns)

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gam_knot_locations)
else:
    test_gam_knot_locations()
