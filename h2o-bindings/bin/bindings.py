#!/usr/bin/env python  -- encoding: utf-8
#
# This is a helper module for scripts that auto-generate bindings for different languages.
# Usage:
#     import bindings as b
#     b.init(language="C#", output_dir="CSharp")
#     for schema in b.schemas():
#         name = schema["name"]
#         b.write_to_file("schemas/%s.cs" % name, gen_file_from_schema(schema))
#
from __future__ import print_function
from __future__ import division
from __future__ import unicode_literals
from __future__ import absolute_import
from collections import defaultdict
from builtins import range
import argparse
import atexit
import codecs
import errno
import os
import pprint
import re
import requests
import shutil
import sys
import textwrap
import time


class TypeTranslator:
    """
    Helper class to assist translating H2O types into native types of your target languages. Typically the user extends
    this class, providing the types dictionary, as well as overwriting any type-conversion lambda-functions.
    """
    def __init__(self):
        # This is a conversion dictionary for simple types that have no schema
        self.types = {
            "byte": "byte", "short": "short", "int": "int", "long": "long",
            "float": "float", "double": "double", "string": "string", "boolean": "boolean",
            "Polymorphic": "Object", "Object": "Object"
        }
        self.make_array = lambda itype: itype + "[]"
        self.make_array2 = lambda itype: itype + "[][]"
        self.make_map = lambda ktype, vtype: "Map<%s,%s>" % (ktype, vtype)
        self.make_key = lambda itype, schema: schema
        self.make_enum = lambda schema: schema
        self.translate_object = lambda otype, schema: schema
        self._mem = set()  # Store all types seen, for debug purposes

    def translate(self, h2o_type, schema):
        if config["verbose"]:
            self._mem.add((h2o_type, schema))
        if h2o_type.endswith("[][]"):
            return self.make_array2(self.translate(h2o_type[:-4], schema))
        if h2o_type.endswith("[]"):
            return self.make_array(self.translate(h2o_type[:-2], schema))
        if h2o_type.startswith("Map<"):
            t1, t2 = h2o_type[4:-1].split(",", 2)  # Need to be fixed once we have keys with commas...
            return self.make_map(self.translate(t1, schema), self.translate(t2, schema))
        if h2o_type.startswith("Key<"):
            return self.make_key(self.translate(h2o_type[4:-1], schema), schema)
        if h2o_type == "enum":
            return self.make_enum(schema)
        if schema is None:
            if h2o_type in self.types:
                return self.types[h2o_type]
            else:
                return h2o_type
        return self.translate_object(h2o_type, schema)

    def vprint_translation_map(self):
        if config["verbose"]:
            print("\n" + "-"*80)
            print("Type conversions done:")
            print("-"*80)
            for t, s in sorted(self._mem):
                print("(%s, %s)  =>  %s" % (t, s, self.translate(t, s)))
            print()


def init(language, output_dir, clear_dir=True):
    """
    Entry point for the bindings module. It parses the command line arguments and verifies their
    correctness.
      :param language -- name of the target language (used to show the command-line description).
      :param output_dir -- folder where the bindings files will be generated. If the folder does
        not exist, it will be created. This folder is relative to ../src-gen/main/. The user may
        specify a different output dir through the commandline argument.
      :param clear_dir -- if True (default), the target folder will be cleared before any new
        files created in it.
    """
    config["start_time"] = time.time()
    print("Generating %s bindings... " % language, end="")
    sys.stdout.flush()

    this_module_dir = os.path.dirname(os.path.realpath(__file__))
    default_output_dir = os.path.abspath(this_module_dir + "/../src-gen/main/" + output_dir)

    # Parse command-line options
    parser = argparse.ArgumentParser(
        description="""
        Generate %s REST API bindings (with docs) and write them to the filesystem.
        Must attach to a running H2O instance to query the interface.""" % language,
    )
    parser.add_argument("-v", "--verbose", help="Verbose output", action="store_true")
    parser.add_argument("--usecloud", metavar="IP:PORT", default="localhost:54321",
                        help="Address of an H2O server (defaults to http://localhost:54321/)")
    # Note: Output folder should be in build directory, however, Idea has problems to recognize them
    parser.add_argument("--dest", metavar="DIR", default=default_output_dir,
                        help="Destination directory for generated bindings")
    args = parser.parse_args()

    # Post-process the options
    base_url = args.usecloud
    if not(base_url.startswith("http://") or base_url.startswith("https://")):
        base_url = "http://" + base_url
    if not(base_url.endswith("/")):
        base_url += "/"
    config["baseurl"] = base_url
    config["verbose"] = args.verbose
    config["destdir"] = os.path.abspath(args.dest)
    vprint("\n\n")

    # Attempt to create the output directory
    try:
        vprint("Output directory = " + config["destdir"])
        os.makedirs(config["destdir"])
    except OSError as e:
        if e.errno != errno.EEXIST:
            print("Cannot create directory " + config["destdir"])
            print("Error %d: %s" % (e.errno, e.strerror))
            sys.exit(6)

    # Clear the content of the output directory. Note: deleting the directory and then recreating it may be
    # faster, but it creates side-effects that we want to avoid (i.e. clears permissions on the folder).
    if clear_dir:
        try:
            vprint("Deleting contents of the output directory...")
            for filename in os.listdir(config["destdir"]):
                filepath = os.path.join(config["destdir"], filename)
                if os.path.isdir(filepath):
                    shutil.rmtree(filepath)
                else:
                    os.unlink(filepath)
        except Exception as e:
            print("Unable to remove file %s: %r" % (filepath, e))
            sys.exit(9)

    # Check that the provided server is accessible; then print its status (if in --verbose mode).
    json = _request_or_exit("/3/About")
    l1 = max(len(e["name"]) for e in json["entries"])
    l2 = max(len(e["value"]) for e in json["entries"])
    ll = max(29 + len(config["baseurl"]), l1 + l2 + 2)
    vprint("-"*ll)
    vprint("Connected to an H2O instance " + config["baseurl"] + "\n")
    for e in json["entries"]:
        vprint(e["name"] + ":" + " "*(1+l1 - len(e["name"])) + e["value"])
    vprint("-"*ll)


def vprint(msg, pretty=False):
    """
    Print the provided string {msg}, but only when the --verbose option is on.
      :param msg     String to print.
      :param pretty  If on, then pprint() will be used instead of the regular print function.
    """
    if not config["verbose"]:
        return
    if pretty:
        pp(msg)
    else:
        print(msg)


def wrap(msg, indent, indent_first=True):
    """
    Helper function that wraps msg to 120-chars page width. All lines (except maybe 1st) will be prefixed with
    string {indent}. First line is prefixed only if {indent_first} is True.
      :param msg: string to indent
      :param indent: string that will be used for indentation
      :param indent_first: if True then the first line will be indented as well, otherwise not
    """
    wrapper.width = 120
    wrapper.initial_indent = indent
    wrapper.subsequent_indent = indent
    msg = wrapper.fill(msg)
    return msg if indent_first else msg[len(indent):]


def endpoints(raw=False):
    """
    Return the list of REST API endpoints. The data is enriched with the following fields:
      class_name: which back-end class handles this endpoint (the class is derived from the URL);
      ischema: input schema object (input_schema is the schema's name)
      oschema: output schema object (output_schema is the schema's name)
      algo: for special-cased calls (ModelBuilders/train and Grid/train) -- name of the ML algo requested
      input_params: list of all input parameters (first path parameters, then all the others). The parameters are
            given as objects, not just names. There is a flag "is_path_param" on each field.
    Additionally certain buggy/deprecated endpoints are removed.
    For Grid/train and ModelBuilders/train endpoints we fix the method name and parameters info (there is some mangling
    of those on the server side).

      :param raw: if True, then the complete untouched response to .../endpoints is returned (including the metadata)
    """
    json = _request_or_exit("/3/Metadata/endpoints")
    if raw: return json

    schmap = schemas_map()
    apinames = {}  # Used for checking for api name duplicates
    assert "routes" in json, "Unexpected result from /3/Metadata/endpoints call"
    re_api_name = re.compile(r"^\w+$")
    def gen_rich_route():
        for e in json["routes"]:
            path = e["url_pattern"]
            method = e["handler_method"]
            apiname = e["api_name"]
            assert apiname not in apinames, "Duplicate api name %s (for %s and %s)" % (apiname, apinames[apiname], path)
            assert re_api_name.match(apiname), "Bad api name %s" % apiname
            apinames[apiname] = path

            # These redundant paths cause conflicts, remove them
            if path == "/3/NodePersistentStorage/categories/{category}/exists": continue
            if path == "/3/ModelMetrics/frames/{frame}/models/{model}": continue
            if path == "/3/ModelMetrics/frames/{frame}": continue
            if path == "/3/ModelMetrics/models/{model}": continue
            if path == "/3/ModelMetrics": continue
            if apiname.endswith("_deprecated"): continue

            # Resolve one name conflict
            if path == "/3/DKV": e["handler_method"] = "removeAll"

            # Find the class_name (first part of the URL after the version: "/3/About" => "About")
            mm = classname_pattern.match(path)
            assert mm, "Cannot determine class name in URL " + path
            e["class_name"] = mm.group(1)

            # Resolve input/output schemas into actual objects
            assert e["input_schema"] in schmap, "Encountered unknown schema %s in %s" % (e["input_schema"], path)
            assert e["output_schema"] in schmap, "Encountered unknown schema %s in %s" % (e["output_schema"], path)
            e["ischema"] = schmap[e["input_schema"]]
            e["oschema"] = schmap[e["output_schema"]]

            # For these special cases, the actual input schema is not the one reported by the endpoint, but the schema
            # of the 'parameters' field (which is fake).
            if (e["class_name"], method) in set([("Grid", "train"), ("ModelBuilders", "train"),
                                                 ("ModelBuilders", "validate_parameters")]):
                pieces = path.split("/")
                assert len(pieces) >= 4, "Expected to see algo name in the path: " + path
                e["algo"] = pieces[3]
                method = method + e["algo"].capitalize()  # e.g. trainGlm()
                e["handler_method"] = method
                for field in e["ischema"]["fields"]:
                    if field["name"] == "parameters":
                        e["input_schema"] = field["schema_name"]
                        e["ischema"] = schmap[e["input_schema"]]
                        break

            # Create the list of input_params (as objects, not just names)
            e["input_params"] = []
            for parm in e["path_params"]:
                # find the metadata for the field from the input schema:
                fields = [field for field in e["ischema"]["fields"] if field["name"] == parm]
                assert len(fields) == 1, "Failed to find parameter: %s for endpoint: %r" % (parm, e)
                field = fields[0].copy()
                schema = field["schema_name"] or ""   # {schema} is null for primitive types
                ftype = field["type"]
                assert ftype == "string" or ftype == "int" or schema.endswith("KeyV3") or schema == "ColSpecifierV3", \
                    "Unexpected param %s of type %s (schema %s)" % (field["name"], ftype, schema)
                assert field["direction"] != "OUTPUT", "A path param %s cannot be of type OUTPUT" % field["name"]
                field["is_path_param"] = True
                field["required"] = True
                e["input_params"].append(field)
            for parm in e["ischema"]["fields"]:
                if parm["direction"] == "OUTPUT" or parm["name"] in e["path_params"]: continue
                field = parm.copy()
                field["is_path_param"] = False
                e["input_params"].append(field)
            yield e

    return list(gen_rich_route())


def endpoint_groups():
    """
    Return endpoints, grouped by the class which handles them
    """
    groups = defaultdict(list)
    for e in endpoints():
        groups[e["class_name"]].append(e)
    return groups


def schemas(raw=False):
    """
    Return the list of H₂O schemas.
      :param raw: if True, then the complete response to .../schemas is returned (including the metadata)
    """
    json = _request_or_exit("/3/Metadata/schemas")
    if raw: return json
    assert "schemas" in json, "Unexpected result from /3/Metadata/schemas call"

    # Simplify names of some horribly sounding enums
    pattern0 = re.compile(r"^\w+(V\d+)\D\w+$")
    pattern1 = re.compile(r"^(\w{3,})(\1)Model\1Parameters(\w+)$", re.IGNORECASE)
    pattern2 = re.compile(r"^(\w{3,})(\1)(\w+)$", re.IGNORECASE)
    def translate_name(name):
        if name is None: return
        if name == "ApiTimelineV3EventV3EventType": return "ApiTimelineEventTypeV3"
        assert not pattern0.match(name), "Bad schema name %s (version number in the middle)" % name
        mm = pattern1.match(name) or pattern2.match(name)
        if mm: return mm.group(2) + mm.group(3)
        return name
    for schema in json["schemas"]:
        schema["name"] = translate_name(schema["name"])
        for field in schema["fields"]:
            field["schema_name"] = translate_name(field["schema_name"])

    return json["schemas"]


def schemas_map():
    """
    Returns a dictionary of H₂O schemas, indexed by their name.
    """
    m = {}
    for schema in schemas():
        m[schema["name"]] = schema
    return m


def model_builders():
    """
    Return the list of models and their parameters.
    """
    json = _request_or_exit("/3/ModelBuilders")
    assert "model_builders" in json, "Unexpected result from /3/ModelBuilders call"
    return json["model_builders"]


def enums():
    """
    Return the dictionary of H₂O enums, retrieved from data in schemas(). For each entry in the dictionary its key is
    the name of the enum, and the value is the set of all enum values.
    """
    enumset = defaultdict(set)
    for schema in schemas():
        for field in schema["fields"]:
            if field["type"] == "enum":
                enumset[field["schema_name"]].update(field["values"])
    return enumset


def write_to_file(filename, content):
    """
    Writes content to the given file. The file's directory will be created if needed.
      :param filename: name of the output file, relative to the "destination folder" provided by the user
      :param content: iterable (line-by-line) that should be written to the file. Either a list or a generator. Each
                      line will be appended with a "\n". Lines containing None will be skipped.
    """
    if not config["destdir"]:
        print("{destdir} config variable not present. Did you forget to run init()?")
        sys.exit(8)
    abs_filename = os.path.abspath(config["destdir"] + "/" + filename)
    abs_filepath = os.path.dirname(abs_filename)
    if not os.path.exists(abs_filepath):
        try:
            os.makedirs(abs_filepath)
        except OSError as e:
            print("Cannot create directory " + abs_filepath)
            print("Error %d: %s" % (e.errno, e.strerror))
            sys.exit(6)
    with codecs.open(abs_filename, "w", "utf-8") as out:
        if isinstance(content, str): content = [content]
        for line in content:
            if line is not None:
                out.write(line)
                out.write("\n")



# ----------------------------------------------------------------------------------------------------------------------
#   Private
# ----------------------------------------------------------------------------------------------------------------------
config = defaultdict(bool)  # will be populated during the init() stage
pp = pprint.PrettyPrinter(indent=4).pprint  # pretty printer
wrapper = textwrap.TextWrapper()
requests_memo = {}  # Simple memoization, so that we don't fetch same data more than once
classname_pattern = re.compile(r"/(?:\d+|LATEST|EXPERIMENTAL)/(\w+)")


def _request_or_exit(endpoint):
    """
    Internal function: retrieve and return json data from the provided endpoint, or die with an error message if the
    URL cannot be retrieved.
    """
    if endpoint[0] == "/":
        endpoint = endpoint[1:]
    if endpoint in requests_memo:
        return requests_memo[endpoint]

    if not config["baseurl"]:
        print("Configuration not present. Did you forget to run init()?")
        sys.exit(8)
    url = config["baseurl"] + endpoint
    try:
        resp = requests.get(url)
    except requests.exceptions.InvalidURL:
        print("Invalid url address of an H2O server: " + config["baseurl"])
        sys.exit(2)
    except requests.ConnectionError:
        print("Cannot connect to the server " + config["baseurl"])
        print("Please check that you have an H2O instance running, and its address is passed in " +
              "the --usecloud argument.")
        sys.exit(3)
    except requests.Timeout:
        print("Request timeout when fetching " + url + ". Check your internet connection and try again.")
        sys.exit(4)
    if resp.status_code == 200:
        try:
            json = resp.json()
        except ValueError:
            print("Invalid JSON response from " + url + " :\n")
            print(resp.text)
            sys.exit(5)
        if "__meta" not in json or "schema_type" not in json["__meta"]:
            print("Unexpected JSON returned from " + url + ":")
            pp(json)
            sys.exit(6)
        if json["__meta"]["schema_type"] == "H2OError":
            print("Server returned an error message for %s:" % url)
            print(json["msg"])
            pp(json)
            sys.exit(7)
        requests_memo[endpoint] = json
        return json
    else:
        print("[HTTP %d] Cannot retrieve %s" % (resp.status_code, url))
        sys.exit(1)


@atexit.register
def _report_time():
    if config["start_time"]:
        print("done (in %.3fs)" % (time.time() - config["start_time"]))
