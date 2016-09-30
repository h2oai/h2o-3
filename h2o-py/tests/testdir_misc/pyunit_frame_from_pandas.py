#!/usr/bin/env python
# -*- encoding: utf-8 -*-
import h2o
import pandas as pd
from tests import pyunit_utils


def test_pandas_to_h2oframe():

    def compare_frames(h2ofr, pdfr, colnames=None):
        if not colnames:
            colnames = list(pdfr.columns)
        assert h2ofr.shape == pdfr.shape
        assert h2ofr.columns == colnames, "Columns differ: %r vs %r" % (h2ofr.columns, colnames)
        for i in range(len(h2ofr.columns)):
            s1 = pdfr[pdfr.columns[i]].tolist()
            s2 = h2ofr[colnames[i]].as_data_frame()[colnames[i]].tolist()
            assert s1 == s2, ("The columns are different: h2oframe[%d] = %r, pdframe[%d] = %r"
                              % (i, s1, i, s2))


    pddf = pd.DataFrame({"one": [4, 6, 1], "two": ["a", "b", "cde"], "three": [0, 5.2, 14]})
    h2odf1 = h2o.H2OFrame.from_python(pddf)
    h2odf2 = h2o.H2OFrame.from_python(pddf, column_names=["A", "B", "C"])
    h2odf3 = h2o.H2OFrame(pddf)

    compare_frames(h2odf1, pddf)
    compare_frames(h2odf2, pddf, ["A", "B", "C"])
    compare_frames(h2odf3, pddf)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_pandas_to_h2oframe)
else:
    test_pandas_to_h2oframe()
