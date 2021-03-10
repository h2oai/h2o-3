from h2o.estimators.xgboost import *
from tests import pyunit_utils
from pandas.testing import assert_frame_equal

def xgboost_prostate_gamma_small():
    assert H2OXGBoostEstimator.available()

    prostate_frame = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    prostate_frame["RACE"] = prostate_frame["RACE"].asfactor()
    prostate_frame["CAPSULE"] = prostate_frame["CAPSULE"].asfactor()
    
    x = ["AGE", "RACE", "GLEASON", "DCAPS", "PSA", "VOL", "CAPSULE"]
    y = 'DPROS'

    xgboost_model = H2OXGBoostEstimator()
    xgboost_model.train(x=x, y=y, training_frame=prostate_frame)

    contribs_original = xgboost_model.predict_contributions(prostate_frame)
    assert contribs_original.col_names == [
        "RACE.0", "RACE.1", "RACE.2", "RACE.missing(NA)", "CAPSULE.0", "CAPSULE.1", "CAPSULE.missing(NA)", 
        "AGE", "DCAPS", "PSA", "VOL", "GLEASON", "BiasTerm"]

    contribs_compact = xgboost_model.predict_contributions(prostate_frame, output_format="Compact")
    assert contribs_compact.col_names == ["RACE", "CAPSULE", "AGE", "DCAPS", "PSA", "VOL", "GLEASON", "BiasTerm"]

    contribs_aggregated = contribs_original.as_data_frame()
    contribs_aggregated = contribs_aggregated.transpose().reset_index()
    contribs_aggregated["index"] = [i.split(".")[0] for i in contribs_aggregated["index"]]
    contribs_aggregated = contribs_aggregated.groupby("index").sum()
    contribs_aggregated = contribs_aggregated.transpose().reset_index(drop=True)  
    contribs_aggregated = contribs_aggregated[["RACE", "CAPSULE", "AGE", "DCAPS", "PSA", "VOL", "GLEASON", "BiasTerm"]]

    assert_frame_equal(contribs_aggregated, contribs_compact.as_data_frame(), check_names=False)


if __name__ == "__main__":
    pyunit_utils.standalone_test(xgboost_prostate_gamma_small)
else:
    xgboost_prostate_gamma_small()
