from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils


def test_relevel_by_freq_topn():
    prostate_cat = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate_cat.csv"))

    dpros_levels_ordered = prostate_cat["DPROS"].table().as_data_frame()["DPROS"].tolist()
    assert dpros_levels_ordered == ["Both", "Left", "None", "Right"]

    prostate_cat_relevel = prostate_cat.relevel_by_frequency(top_n=1)

    dpros_relevel_levels = prostate_cat_relevel["DPROS"].table().as_data_frame()["DPROS"].tolist()
    assert dpros_relevel_levels == ['Left', 'Both', 'None', 'Right']

    top_drops_level = prostate_cat["DPROS"].table().as_data_frame().sort_values(by="Count")["DPROS"].tolist()[-1]
    prostate_cat_relevel_manual = prostate_cat["DPROS"].relevel(y=top_drops_level)
    assert prostate_cat_relevel_manual.levels() == [dpros_relevel_levels] 


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_relevel_by_freq_topn)
else:
    test_relevel_by_freq_topn()
