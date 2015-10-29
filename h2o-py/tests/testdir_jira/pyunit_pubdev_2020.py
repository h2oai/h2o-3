import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils

def test1():
    df = h2o.upload_file(pyunit_utils.locate("smalldata/jira/pubdev_2020.csv"))
    splits = df.split_frame(ratios=[0.75])
    assert df.nrow == splits[0].nrow + splits[1].nrow

    part0 = splits[0]
    split_was_sequential = True
    i = 0
    while i < part0.nrow:
        value = part0[i, "C1"]
        print(value)
        if value != (i + 1):
            split_was_sequential = False
        i += 1

    assert False == split_was_sequential

    part1 = splits[1]

    assert part0.nrow > 0
    assert part1.nrow > 1


def test2():
    df = h2o.upload_file(pyunit_utils.locate("smalldata/jira/pubdev_2020.csv"))
    splits = df.split_frame(ratios=[0.5, 0.25])
    assert df.nrow == splits[0].nrow + splits[1].nrow + splits[2].nrow
    assert splits[0].nrow > 0
    assert splits[1].nrow > 0
    assert splits[2].nrow > 0


def test3():
    df = h2o.upload_file(pyunit_utils.locate("smalldata/jira/pubdev_2020.csv"))
    splits = df.split_frame(ratios=[0.8], seed=2015)
    part1 = splits[1]
    value = part1[0, "C1"]
    assert value == 7
    value = part1[1, "C2"]
    assert value == 13
    value = part1[2, "C3"]
    assert value == 22

    df = h2o.upload_file(pyunit_utils.locate("smalldata/jira/pubdev_2020.csv"))
    splits = df.split_frame(ratios=[0.8], seed=2016)
    part1 = splits[1]
    value = part1[0, "C1"]
    assert value == 17

    df = h2o.upload_file(pyunit_utils.locate("smalldata/jira/pubdev_2020.csv"))
    splits = df.split_frame(ratios=[0.8], seed=2016)
    part1 = splits[1]
    value = part1[0, "C1"]
    assert value == 17


def test4():
    df = h2o.upload_file(pyunit_utils.locate("smalldata/jira/pubdev_2020.csv"))
    splits = df.split_frame(ratios=[0.8], destination_frames=["myf0", "myf1"])
    part0 = splits[0]
    assert part0.frame_id == "myf0"
    part1 = splits[1]
    assert part1.frame_id == "myf1"


def pubdev_2020():
    test1()
    test2()
    test3()
    test4()

if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_2020)
else:
    pubdev_2020()
