# -*- encoding: utf-8 -*-
#
# Copyright 2016 H2O.ai;  Apache License Version 2.0 (see LICENSE for details)
#
from __future__ import division, print_function, absolute_import, unicode_literals
# noinspection PyUnresolvedReferences
from h2o.utils.compatibility import *  # NOQA

from .schema import H2OSchema


class H2OErrorV3(H2OSchema):

    _schema_endpoint_ = "/3/Metadata/schemas/H2OErrorV3"
    
    @classmethod
    def make(cls, keyvals):
        return cls.instantiate_from_json(keyvals)

    def __init__(self):
        super(H2OErrorV3, self).__init__()
        self.endpoint = None
        self.payload = None

    def __setitem__(self, key, value):
        if key in self._schema_attrs_.keys():
            if key.endswith("msg"):
                value = value.replace("ERROR MESSAGE:", "").strip()
            self._props[key] = value
        
    def __str__(self):
        res = "Server error %s:\n" % self.exception_type
        res += "  Error: %s\n" % self.msg
        res += "  Request: %s\n" % self.endpoint
        if self.payload:
            if self.payload[0]: res += "    data: %r\n" % self.payload[0]
            if self.payload[1]: res += "    json: %r\n" % self.payload[1]
            if self.payload[2]: res += "    file: %r\n" % self.payload[2]
            if self.payload[3]: res += "    params: %r\n" % self.payload[3]
        return res


class H2OModelBuilderErrorV3(H2OErrorV3):

    _schema_endpoint_ = "/3/Metadata/schemas/H2OModelBuilderErrorV3"
    
    def __str__(self):
        res = "ModelBuilderErrorV3  (%s):\n" % self.exception_type
        for k, v in self._props.items():
            if k in {"exception_type"}: continue
            if k == "stacktrace":
                res += "    stacktrace =\n"
                for line in v:
                    res += "        %s\n" % line.strip()
            else:
                res += "    %s = %r\n" % (k, v)
        return res
