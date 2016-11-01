#!/usr/bin/env python
# -*- encoding: utf-8 -*-
import h2o
from h2o.exceptions import H2OValueError
from tests import pyunit_utils


def additional_parameters():
    """
    Verifying that Python can support additional parameters of destination_frame,
    column_names, and column_types and that certain characters are allowed.
    """
    input_file = pyunit_utils.locate("smalldata/jira/hexdev_29.csv")

    # col_types as list
    dest_frame = "-._~!$&*+,=0123456789"
    c_names = ["a", "b", "c"]
    c_types = ["enum", "enum", "string"]

    fhex = h2o.import_file(input_file,
                           destination_frame=dest_frame,
                           col_names=c_names,
                           col_types=c_types)
    fhex.describe()

    assert fhex.frame_id == dest_frame
    assert fhex.names == c_names
    col_summary = h2o.frame(fhex.frame_id)["frames"][0]["columns"]
    for i in range(len(col_summary)):
        assert col_summary[i]["type"] == c_types[i]

    # col_types as dictionary
    dest_frame = "._~!$&-,=*+"
    c_names = ["a", "b", "c"]
    c_types = {"c": "string", "a": "string"}

    fhex = h2o.import_file(input_file,
                           destination_frame=dest_frame,
                           col_names=c_names,
                           col_types=c_types)
    fhex.describe()

    assert fhex.frame_id == dest_frame
    assert fhex.col_names == c_names
    col_summary = h2o.frame(fhex.frame_id)["frames"][0]["columns"]
    for i in range(len(col_summary)):
        name = c_names[i]
        if name in c_types:
            assert col_summary[i]["type"] == c_types[name]

    def test_bad_id(frameid):
        try:
            h2o.import_file(input_file, destination_frame=frameid)
            assert False, "Frame id '%s' should not have been allowed" % frameid
        except H2OValueError:
            pass

    test_bad_id("ab;cd")
    test_bad_id("one/two/three/four")
    test_bad_id("I'm_declaring_a_thumb_war")
    test_bad_id("five\\six\\seven\\eight")
    test_bad_id("finger guns proliferate")
    test_bad_id("9_10_11_12")
    test_bad_id("digits|cant|protect|themselves")
    test_bad_id("(thirteen,fourteen,fifteen,sixteen)")
    test_bad_id("UNSC_cant_intervene?")




if __name__ == "__main__":
    pyunit_utils.standalone_test(additional_parameters)
else:
    additional_parameters()
