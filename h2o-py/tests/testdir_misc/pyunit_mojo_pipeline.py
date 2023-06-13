#!/usr/bin/env python
# -*- encoding: utf-8 -*-
import sys
sys.path.insert(1,"../../")
import h2o
import os
from h2o.pipeline import H2OMojoPipeline
from tests import pyunit_utils

def mojo_pipeline():
    test_dir = os.environ.get('MOJO_PIPELINE_TEST_DIR')
    if test_dir is None:
        print("Test dir not configured, MOJO Pipeline test skipped")
        return
    if not H2OMojoPipeline.available():
        print("Backend is not configured for MOJO Pipelines, MOJO Pipeline test skipped")
        return

    example_csv = os.path.join(test_dir, "example.csv")
    mojo_file = os.path.join(test_dir, "pipeline.mojo")

    example_data = h2o.import_file(example_csv)
    pipeline = H2OMojoPipeline(mojo_file)

    transformed = pipeline.transform(example_data)
    transformed.show()

    assert transformed.dim == [10, 2]
    totals = transformed[0] + transformed[1]
    assert totals.min() == 1
    assert totals.max() == 1


if __name__ == "__main__":
    pyunit_utils.standalone_test(mojo_pipeline)
else:
    mojo_pipeline()
