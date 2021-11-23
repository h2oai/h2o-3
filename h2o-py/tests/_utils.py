import fnmatch
import importlib
import os
import re
import sys


def list_all_files(paths, filter_=None, recursive=True):
    """
    Need this function as Py2 doesn't provide any useful `glob` implementation.
    :param paths: the directories to look into.
    :param filter_: None, or a predicate function returning True iff the file should be listed.
    :param recursive: True if files should be listed recursively in sub folders.
    :return: the listed files
    """
    filter_ = filter_ or (lambda _: True)
    all_files = []
    paths = paths if isinstance(paths, list) else [paths]
    for path in paths:
        if os.path.isdir(path):
            for root_dir, sub_dirs, files in os.walk(path):
                if not recursive and root_dir != path:
                    break
                for name in files:
                    full_path = os.path.join(root_dir, name)
                    if filter_(full_path):
                        all_files.append(full_path)
        elif os.path.isfile(path):
            if filter_(path):
                all_files.append(path)
    return all_files


def _create_file_filter(filter_, default_value=True):
    matches = ((lambda _: default_value) if filter_ is None
               else filter_ if callable(filter_)
    else (lambda p: fnmatch.fnmatch(p, filter_)) if isinstance(filter_, str)
    else (lambda p: any(fnmatch.fnmatch(p, pat) for pat in filter_)) if isinstance(filter_, (list, tuple))
    else None)
    if matches is None:
        raise ValueError("filter should be None, a predicate function, a wildcard pattern or a list of those.")
    return matches


def file_filter(include=None, exclude=None):
    includes = _create_file_filter(include, True)
    excludes = _create_file_filter(exclude, False)
    return lambda p: includes(p) and not excludes(p)


def load_module(name, dir_path, no_conflict=True):
    dir_path = os.path.realpath(dir_path)
    if no_conflict and name in sys.modules:
        conflict = sys.modules[name]
        raise ImportError("name conflict: module `{name}` in `{dir}` conflicts with `{existing}`".format(
            name=name,
            dir=dir_path,
            existing=conflict.__file__
        ))
    try:  # Py3
        spec = importlib.util.spec_from_file_location(name, os.path.join(dir_path, name+".py"))
        module = importlib.util.module_from_spec(spec)
        sys.modules[name] = module
        spec.loader.exec_module(module)
        return module
    except AttributeError:  # Py2
        import imp
        spec = imp.find_module(name, [dir_path])
        return imp.load_module(name, *spec)


def load_utilities(test_file=None):
    utils_pat = re.compile(r".*/_\w+\.py$")
    ff = file_filter(include=(lambda p: utils_pat.match(p)), 
                     exclude="*/__init__.py")
    recursive = False
    folders = []
    if test_file is None:
        # loads every utility file under tests
        folders.append(os.path.dirname(__file__))
        recursive = True
    else:
        folder = os.path.dirname(test_file)
        while os.path.basename(folder) != "tests":
            folders.append(folder)
            folder = os.path.dirname(folder)
        folders.reverse()  # we the closest to load last in case of name conflict
    utilities = list_all_files(folders, filter_=ff, recursive=recursive)
    print("loading the following test utility modules: ", utilities)
    for u in utilities:
        d = os.path.dirname(u)
        f = os.path.basename(u)
        m, _ = os.path.splitext(f)
        load_module(m, d)
        

