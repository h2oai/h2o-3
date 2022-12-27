# -*- encoding: utf-8 -*-
#
# Copyright 2016 H2O.ai;  Apache License Version 2.0 (see LICENSE for details)
#
from __future__ import division, print_function, absolute_import, unicode_literals
# noinspection PyUnresolvedReferences
from h2o.utils.compatibility import *  # NOQA

from functools import partial

import h2o
from h2o.exceptions import H2OConnectionError, H2OServerError


def define_classes_from_schema(classes, connection):
    for cls in classes:
        cls.define_from_schema(connection)


class H2OSchema(object):

    _ignored_schema_keys_ = {"__meta", "_exclude_fields", "__schema"}
    _schema_attrs_ = {}
    _default_attrs_values_ = {}
    _schema_endpoint_ = None

    @classmethod
    def define_from_schema(cls, connection):
        meta = connection.request("GET %s" % cls._schema_endpoint_)

        if not hasattr(meta, "fields"):
            raise H2OServerError("Unexpected API output. Please verify server url and port. Response is '%s'" % meta)

        cls_vars = vars(cls).keys()
        for attr, dyn in cls._schema_attrs_.items():
            if dyn and attr in cls_vars:  # to not delete attributes that were not dynamically added in the current class
                delattr(cls, attr)
        cls._schema_attrs_ = {}

        for f in meta.fields:
            name = f.name
            if name not in cls._ignored_schema_keys_:
                if hasattr(cls, name):
                    cls._schema_attrs_[name] = False 
                else:
                    cls._schema_attrs_[name] = True
                    setattr(cls, name, property(partial(cls.__getitem__, name=name), doc=f.help))

    @classmethod
    def instantiate_from_json(cls, json_kv_pairs):
        obj = cls()
        for k, v in json_kv_pairs:
            if k in cls._ignored_schema_keys_: continue
            obj[k] = v
        return obj

    def __init__(self):
        self._props = {}

    def __getitem__(self, name):
        return self._props.get(name, self._default_attrs_values_.get(name))

    def __setitem__(self, key, value):
        if key in self._schema_attrs_.keys():
            self._props[key] = value

    def __getattr__(self, item):
        if h2o.cluster():
            raise AttributeError(("Unknown attribute `{prop}` on object of type `{cls}`, "
                                  "this property is not available for this H2O backend [version={version}].").format(
                prop=item,
                cls=self.__class__.__name__,
                version=h2o.cluster().version
            ))
        else:
            raise H2OConnectionError("Not connected to a cluster. Did you run `h2o.init()` or `h2o.connect()`?")
            

