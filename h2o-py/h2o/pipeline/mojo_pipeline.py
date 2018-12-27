# -*- encoding: utf-8 -*-
"""
H2O MOJO Pipeline.

:copyright: (c) 2018 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
from __future__ import absolute_import, division, print_function, unicode_literals


import h2o
from h2o.expr import ExprNode
from h2o.frame import H2OFrame
from h2o.utils.typechecks import assert_is_type

__all__ = ("H2OMojoPipeline", )


class H2OMojoPipeline(object):
    """
    Representation of a MOJO Pipeline. This is currently an experimental feature.
    """


    #-------------------------------------------------------------------------------------------------------------------
    # Construction
    #-------------------------------------------------------------------------------------------------------------------

    def __init__(self, mojo_path=None):
        """
        Create a new H2OMojoPipeline object.

        :param mojo_path path to a MOJO file.
        """
        assert_is_type(mojo_path, str)

        self.pipeline_id = h2o.lazy_import(mojo_path)

    def transform(self, data, allow_timestamps=False):
        """
        Transform H2OFrame using a MOJO Pipeline.

        :param data: Frame to be transformed.
        :param allow_timestamps: Allows datetime columns to be used directly with MOJO pipelines. It is recommended
        to parse your datetime columns as Strings when using pipelines because pipelines can interpret certain datetime
        formats in a different way. If your H2OFrame is parsed from a binary file format (eg. Parquet) instead of CSV
        it is safe to turn this option on and use datetime columns directly.

        :returns: A new H2OFrame.
        """
        assert_is_type(data, H2OFrame)
        assert_is_type(allow_timestamps, bool)
        return H2OFrame._expr(ExprNode("mojo.pipeline.transform", self.pipeline_id[0], data, allow_timestamps))

    # Ask the H2O server whether MOJO Pipelines are enabled (depends on availability of MOJO Runtime)
    @staticmethod
    def available():
        """
        Returns True if a MOJO Pipelines can be used, or False otherwise.
        """
        if "MojoPipeline" not in h2o.cluster().list_core_extensions():
            print("Cannot use MOJO Pipelines - runtime was not found.")
            return False
        else:
            return True
