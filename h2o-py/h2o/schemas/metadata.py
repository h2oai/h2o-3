# -*- encoding: utf-8 -*-
#
# Copyright 2016 H2O.ai;  Apache License Version 2.0 (see LICENSE for details)
#
from __future__ import division, print_function, absolute_import, unicode_literals
# noinspection PyUnresolvedReferences
from h2o.utils.compatibility import *  # NOQA


class H2OMetadataV3(object):

    @classmethod
    def make(cls, json_kv_pairs):
        return cls(json_kv_pairs)

    def __init__(self, json_kv_pairs):
        self._schemas = next((v for k, v in json_kv_pairs if k == 'schemas'), []) or []
        self._schema = self._schemas[0] if self._schemas else None
        self._routes = next((v for k, v in json_kv_pairs if k == 'routes'), []) or []
        
    @property
    def name(self):
        return self._schema.get('name') if self._schema else None

    @property
    def fields(self):
        return [_Field(f) for f in self._schema.get('fields')] if self._schema else None
    
    @property
    def routes(self):
        return [_Route(r) for r in self._routes]
    
    def __repr__(self):
        return repr({k: getattr(self, k) for k in dir(self) if not k.startswith('_')})
    
    
class _Field(object):
    
    def __init__(self, j_field):
        self._field = j_field
    
    @property
    def name(self):
        return self._field.get('name')
    
    @property
    def is_schema(self):
        return self._field.get('is_schema')
    
    @property
    def help(self):
        return self._field.get('help')
    
    def __repr__(self):
        return repr({k: getattr(self, k) for k in dir(self) if not k.startswith('_')})
    
    
class _Route(object):

    def __init__(self, j_route):
        self._route = j_route

    @property
    def http_method(self):
        return self._route.get('http_method')

    @property
    def url_pattern(self):
        return self._route.get('url_pattern')

    @property
    def summary(self):
        return self._route.get('summary')

    @property
    def input_schema(self):
        return self._route.get('input_schema')

    @property
    def output_schema(self):
        return self._route.get('output_schema')
    
    def __repr__(self):
        return repr({k: getattr(self, k) for k in dir(self) if not k.startswith('_')})
