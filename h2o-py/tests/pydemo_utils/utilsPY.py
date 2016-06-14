import json
import codecs

def ipy_notebook_exec(path, save_and_norun=None):
    notebook = json.load(codecs.open(path, "r", "utf-8"))
    program = ''
    for block in ipy_code_blocks(notebook):
        for line in ipy_valid_lines(block):
            if "h2o.init" not in line:
                program += line if '\n' in line else line + '\n'
    if not save_and_norun == None:
        with open(save_and_norun,"w") as f: f.write(program)
    else :
        d={}
        exec(program, d)  # safe, but horrible (exec is horrible)

def ipy_blocks(notebook):
    if 'worksheets' in list(notebook.keys()):
        return notebook['worksheets'][0]['cells']  # just take the first worksheet
    elif 'cells' in list(notebook.keys()):
        return notebook['cells']
    else:
        raise NotImplementedError("ipython notebook cell/block json format not handled")

def ipy_code_blocks(notebook):
    return [cell for cell in ipy_blocks(notebook) if cell['cell_type'] == 'code']

def ipy_lines(block):
    if 'source' in list(block.keys()):
        return block['source']
    elif 'input' in list(block.keys()):
        return block['input']
    else:
        raise NotImplementedError("ipython notebook source/line json format not handled")

def ipy_valid_lines(block):
    lines = ipy_lines(block)

    # matplotlib handling
    for line in lines:
        if "import matplotlib.pyplot as plt" in line or "%matplotlib inline" in line:
            import matplotlib
            matplotlib.use('Agg', warn=False)

    # remove ipython magic functions
    lines = [line for line in lines if not line.startswith('%')]

    # don't show any plots
    lines = [line for line in lines if not "plt.show()" in line]

    return lines

def pydemo_exec(test_name):
    with open (test_name, "r") as t: demo = t.read()
    program = ''
    for line in demo.split('\n'):
        if "h2o.init" not in line:
            program += line if '\n' in line else line + '\n'
    demo_c = compile(program, '<string>', 'exec')
    p = {}
    exec(demo_c, p)

