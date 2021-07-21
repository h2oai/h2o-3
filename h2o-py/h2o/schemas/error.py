# -*- encoding: utf-8 -*-
#
# Copyright 2016 H2O.ai;  Apache License Version 2.0 (see LICENSE for details)
#
from __future__ import division, print_function, absolute_import, unicode_literals
# noinspection PyUnresolvedReferences
from h2o.utils.compatibility import *  # NOQA

from functools import partial
from h2o.schemas import _ignored_schema_keys


class H2OErrorV3(object):
    
    @classmethod
    def make(cls, keyvals):
        err = cls()
        for k, v in keyvals:
            if k in _ignored_schema_keys: continue
            if not hasattr(cls, k):
                # we can add properties dynamically here as they are defined statically on the backend
                # as properties of water.api.schemas3.H2OErrorV3 (or subclasses)
                setattr(cls, k, property(partial(cls.__getitem__, name=k)))
            if k.endswith("msg"):
                v = v.replace("ERROR MESSAGE:", "").strip()
            err._props[k] = v
        return err

    def __init__(self):
        self._props = {}
        self.endpoint = None
        self.payload = None

    def __getitem__(self, name):
        return self._props.get(name)

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
