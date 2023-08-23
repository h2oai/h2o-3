import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator
import tempfile
import os

# Check and make sure transformed gam frame is correctedly saved with h2o.save_moddel
def test_gam_transformed_frame_serialization():
    h2o_data = h2o.import_file(
        path=pyunit_utils.locate("smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    myX = ["C1", "C2"]
    myY = "C11"
    h2o_data["C11"] = h2o_data["C11"].asfactor()
    h2o_model = H2OGeneralizedAdditiveEstimator(family="multinomial", gam_columns=["C6", "C7", "C8"], seed=1234, 
                                                keep_gam_cols=True, scale=[1, 1, 1], num_knots=[5, 5, 5], bs=[0, 1, 3])
    h2o_model.train(x=myX, y=myY, training_frame=h2o_data)
    gam_frame = h2o.get_frame(h2o_model._model_json["output"]["gam_transformed_center_key"])
    tmpdir = tempfile.mkdtemp()
    filename = os.path.join(tmpdir, "gamXFrame.csv")
    h2o.download_csv(gam_frame, filename)
    model_path = h2o.save_model(h2o_model, tmpdir)

    h2o.remove_all()
    loaded_model = h2o.load_model(model_path)
    gam_frame_loaded = h2o.get_frame(loaded_model._model_json["output"]["gam_transformed_center_key"])
    gam_frame_original = h2o.import_file(filename)
    pyunit_utils.compare_frames_local(gam_frame_loaded[2:15], gam_frame_original[2:15], prob=1, tol=1e-6)
    print("Test completed.")


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gam_transformed_frame_serialization)
else:
    test_gam_transformed_frame_serialization()
