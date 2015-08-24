import json
import os

def ipy_notebook_exec(path,save_and_norun=False):
    notebook = json.load(open(path))
    program = ''
    for block in ipy_blocks(notebook):
        for line in ipy_lines(block):
            if "h2o.init" not in line:
                program += line if '\n' in line else line + '\n'
    if save_and_norun:
        with open(os.path.basename(path).split('ipynb')[0]+'py',"w") as f:
            f.write(program)
    else:
        d={}
        exec program in d  # safe, but horrible (exec is horrible)

def ipy_blocks(notebook):
    if 'worksheets' in notebook.keys():
        return notebook['worksheets'][0]['cells']  # just take the first worksheet
    elif 'cells' in notebook.keys():
        return notebook['cells']
    else:
        raise NotImplementedError, "ipython notebook cell/block json format not handled"

def ipy_lines(block):
    if 'source' in block.keys():
        return block['source']
    elif 'input' in block.keys():
        return block['input']
    else:
        raise NotImplementedError, "ipython notebook source/line json format not handled"