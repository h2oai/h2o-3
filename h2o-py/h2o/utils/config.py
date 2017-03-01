#!/usr/bin/env python3
# Copyright 2016 H2O.ai; Apache License Version 2.0;  -*- encoding: utf-8 -*-
"""Read h2opy configuration file(s)."""

import io
import logging
import os
import re

__all__ = ("H2OConfigReader", "get_config_value")


class H2OConfigReader(object):
    """
    Helper class responsible for reading h2o config files.

    This module will look for file(s) named ".h2oconfig" in the current folder, in all parent folders, and finally in
    the user's home directory. The first such file found will be used for configuration purposes. The format for such
    file is a simple "key = value" store, with possible section names in square brackets. Single-line comments starting
    with '#' are also allowed.
    """

    @staticmethod
    def get_config():
        """Retrieve the config as a dictionary of key-value pairs."""
        self = H2OConfigReader._get_instance()
        if not self._config_loaded:
            self._read_config()
        return self._config


    #-------------------------------------------------------------------------------------------------------------------
    # Private
    #-------------------------------------------------------------------------------------------------------------------

    _allowed_config_keys = {
        "init.check_version", "init.proxy", "init.url", "init.verify_ssl_certificates",
        "init.cookies", "init.username", "init.password",
        "general.allow_breaking_changes"
    }

    def __init__(self):
        """Initialize the singleton instance of H2OConfigReader."""
        assert not hasattr(H2OConfigReader, "_instance"), "H2OConfigReader is intended to be used as a singleton"
        self._logger = logging.getLogger("h2o")
        self._config = {}
        self._config_loaded = False

    @staticmethod
    def _get_instance():
        """Return the singleton instance of H2OConfigReader."""
        if not hasattr(H2OConfigReader, "_instance"):
            H2OConfigReader._instance = H2OConfigReader()
        return H2OConfigReader._instance

    def _read_config(self):
        """Find and parse config file, storing all variables in ``self._config``."""
        self._config_loaded = True
        conf = []
        for f in self._candidate_log_files():
            if os.path.isfile(f):
                self._logger.info("Reading config file %s" % f)
                section_rx = re.compile(r"^\[(\w+)\]$")
                keyvalue_rx = re.compile(r"^(\w+:)?([\w.]+)\s*=(.*)$")
                with io.open(f, "rt", encoding="utf-8") as config_file:
                    section_name = None
                    for lineno, line in enumerate(config_file):
                        line = line.strip()
                        if line == "" or line.startswith("#"): continue
                        m1 = section_rx.match(line)
                        if m1:
                            section_name = m1.group(1)
                            continue
                        m2 = keyvalue_rx.match(line)
                        if m2:
                            lng = m2.group(1)
                            key = m2.group(2)
                            val = m2.group(3).strip()
                            if lng and lng.lower() != "py:": continue
                            if section_name:
                                key = section_name + "." + key
                            if key in H2OConfigReader._allowed_config_keys:
                                conf.append((key, val))
                            else:
                                self._logger.error("Key %s is not a valid config key" % key)
                            continue
                        self._logger.error("Syntax error in config file line %d: %s" % (lineno, line))
                self._config = dict(conf)
                return

    @staticmethod
    def _candidate_log_files():
        """Return possible locations for the .h2oconfig file, one at a time."""
        # Search for .h2oconfig in the current directory and all parent directories
        relpath = ".h2oconfig"
        prevpath = None
        while True:
            abspath = os.path.abspath(relpath)
            if abspath == prevpath: break
            prevpath = abspath
            relpath = "../" + relpath
            yield abspath
        # Also check if .h2oconfig exists in the user's directory
        yield os.path.expanduser("~/.h2oconfig")



def get_config_value(key, default=None):
    """Return config value corresponding to the provided `key`."""
    return H2OConfigReader.get_config().get(key, default)
