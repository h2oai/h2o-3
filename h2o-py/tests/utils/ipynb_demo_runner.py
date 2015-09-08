import json
import os

def ipy_notebook_exec(path,save_and_norun=False):
    notebook = json.load(open(path))
    program = ''
    for block in ipy_code_blocks(notebook):
        for line in ipy_valid_lines(block):
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

def ipy_code_blocks(notebook):
    return [cell for cell in ipy_blocks(notebook) if cell['cell_type'] == 'code']

def ipy_lines(block):
    if 'source' in block.keys():
        return block['source']
    elif 'input' in block.keys():
        return block['input']
    else:
        raise NotImplementedError, "ipython notebook source/line json format not handled"

def ipy_valid_lines(block):
    # remove ipython magic functions
    lines = [line for line in ipy_lines(block) if not line.startswith('%')]

    # (clunky) matplotlib handling
    for line in lines:
        if "import matplotlib.pyplot as plt" in line:
            import matplotlib
            matplotlib.use('Agg', warn=False)
    return [line for line in lines if not "plt.show()" in line]

