#!/usr/bin/env python
# -*- encoding: utf-8 -*-
from __future__ import unicode_literals
import json
import bindings as bi

def main():
    bi.init("Docs Json", "../../../h2o-docs", clear_dir=False)

    bi.vprint("Writing schemas.json...")
    bi.write_to_file("schemas.json", json.dumps(bi.schemas(raw=True)))

    bi.vprint("Writing routes.json...")
    bi.write_to_file("routes.json", json.dumps(bi.endpoints(raw=True)))


if __name__ == "__main__":
    main()
