import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from pandas.testing import assert_frame_equal 


def test_relevel_by_freq():
    prostate_cat = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate_cat.csv"))
    prostate_cat_pd = prostate_cat.as_data_frame()
    col = "DPROS"

    print(prostate_cat.group_by(by=col).count().get_frame())

    dpros_levels_ordered = prostate_cat[col].table().as_data_frame()[col].tolist()
    assert dpros_levels_ordered == ["Both", "Left", "None", "Right"]
    print(dpros_levels_ordered)
    print(prostate_cat_pd)

    prostate_cat_relevel = prostate_cat.relevel_by_frequency()

    print(prostate_cat_relevel.group_by(by=col).count().get_frame())

    prostate_cat_relevel_pd = prostate_cat_relevel.as_data_frame()
    print(prostate_cat_relevel_pd)

    dpros_relevel_levels = prostate_cat_relevel[col].table().as_data_frame()[col].tolist()
    assert dpros_relevel_levels == ["Left", "None", "Right", "Both"]
    print(dpros_relevel_levels)

    # data didn't change at all
    assert_frame_equal(prostate_cat_pd, prostate_cat_relevel_pd)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_relevel_by_freq)
else:
    test_relevel_by_freq()
