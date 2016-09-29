# -*- encoding: utf-8 -*-
#
# Copyright 2016 H2O.ai;  Apache License Version 2.0 (see LICENSE for details)
#
from __future__ import division, print_function, absolute_import, unicode_literals

# noinspection PyUnresolvedReferences
from h2o.utils.compatibility import *  # NOQA


class H2OErrorV3(object):
    """
    """
    def __init__(self, keyvals):
        self._props = {}
        self._endpoint = None
        self._payload = None
        for k, v in keyvals:
            if k == "__meta" or k == "_exclude_fields" or k == "__schema": continue
            if k in _h2oerror_v3_valid_keys:
                if k.endswith("msg"): v = v.replace("ERROR MESSAGE:", "").strip()
                self._props[k] = v
            else:
                raise AttributeError("Attribute %s cannot be set on H2OErrorV3 (= %r)" % (k, v))

    @property
    def stacktrace(self):
        return self._props["stacktrace"]

    @property
    def timestamp(self):
        return self._props["timestamp"]

    @property
    def error_url(self):
        return self._props["error_url"]

    @property
    def exception_type(self):
        return self._props["exception_type"]

    @property
    def exception_msg(self):
        return self._props["exception_msg"]

    @property
    def dev_msg(self):
        return self._props["dev_msg"]

    @property
    def http_status(self):
        return self._props["http_status"]

    @property
    def msg(self):
        return self._props["msg"]

    @property
    def values(self):
        return self._props["values"]


    @property
    def endpoint(self):
        return self._endpoint

    @endpoint.setter
    def endpoint(self, value):
        self._endpoint = value

    @property
    def payload(self):
        return self._payload

    @payload.setter
    def payload(self, value):
        self._payload = value

    def __repr__(self):
        res = "Server error %s:\n" % self.exception_type
        res += "  Error: %s\n" % self.msg
        res += "  Request: %s\n" % self.endpoint
        if self._payload:
            if self._payload[0]: res += "    data: %r\n" % self._payload[0]
            if self._payload[1]: res += "    json: %r\n" % self._payload[1]
            if self._payload[2]: res += "    file: %r\n" % self._payload[2]
            if self._payload[3]: res += "    params: %r\n" % self._payload[3]
        return res


class H2OModelBuilderErrorV3(object):
    def __init__(self, keyvals):
        self._props = {}
        self._endpoint = None
        self._payload = None
        for k, v in keyvals:
            if k == "__meta" or k == "_exclude_fields" or k == "__schema": continue
            if k in _h2omberror_v3_valid_keys:
                if k.endswith("msg"): v = v.replace("ERROR MESSAGE:", "").strip()
                self._props[k] = v
            else:
                raise AttributeError("Attribute %s cannot be set on H2OModelBuilderErrorV3 (= %r)" % (k, v))

    def __getitem__(self, key):
        if key in self._props:
            return self._props[key]

    @property
    def stacktrace(self):
        return self._props["stacktrace"]

    @property
    def timestamp(self):
        return self._props["timestamp"]

    @property
    def error_url(self):
        return self._props["error_url"]

    @property
    def exception_type(self):
        return self._props["exception_type"]

    @property
    def exception_msg(self):
        return self._props["exception_msg"]

    @property
    def dev_msg(self):
        return self._props["dev_msg"]

    @property
    def http_status(self):
        return self._props["http_status"]

    @property
    def msg(self):
        return self._props["msg"]

    @property
    def values(self):
        return self._props["values"]

    @property
    def messages(self):
        return self._props["messages"]

    @property
    def error_count(self):
        return self._props["error_count"]

    @property
    def parameters(self):
        return self._props["parameters"]

    def __repr__(self):
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




_h2oerror_v3_valid_keys = {"stacktrace", "timestamp", "error_url", "exception_type", "exception_msg", "dev_msg",
                           "http_status", "msg", "values"}

_h2omberror_v3_valid_keys = {"stacktrace", "timestamp", "error_url", "exception_type", "exception_msg", "dev_msg",
                             "http_status", "msg", "values", "messages", "error_count", "parameters"}
