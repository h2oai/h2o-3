import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils


def h2o_group_by_types():
    """
    This test checks that if the returned frame after a group_by operation returns correct type of group_by column.
    """

    data = h2o.H2OFrame([["4/1/07", 1, "A", 2.2],
                         ["5/1/07", 23, "B", 223.4],
                         ["6/1/07", 3, "A", 224.5]],
                        column_names=["date", "int", "string", "double"])

    group_by_column = "date"
    grouped_type = get_group_by_type(data, group_by_column)
    assert data[group_by_column].types == grouped_type, \
        "The type of group by column should be the same before and after group by."

    group_by_column = "int"
    grouped_type = get_group_by_type(data, group_by_column)
    assert data[group_by_column].types == grouped_type, \
        "The type of group by column should be the same before and after group by."

    group_by_column = "double"
    grouped_type = get_group_by_type(data, group_by_column)
    assert data[group_by_column].types == grouped_type, \
        "The type of group by column should be the same before and after group by."


def get_group_by_type(data, group_by_column):
    grouped = data.group_by(by=[group_by_column]).mean('int')
    grouped_frame = grouped.get_frame()
    return grouped_frame[group_by_column].types


if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_group_by_types)
else:
    h2o_group_by_types()
