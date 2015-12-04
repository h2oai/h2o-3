import sys, os
sys.path.insert(1, "../../")
import h2o

def test_name():   return sys.modules["tests.pybooklet_utils"].__test_name__
def results_dir(): return sys.modules["tests.pybooklet_utils"].__results_dir__

def check_code_examples_in_dir(approved_py_code_examples, directory):
    actual = []
    for f in os.listdir(directory):
        if f.endswith(".py"): actual.append(f)
    if len(approved_py_code_examples) > len(actual): return False
    for e in approved_py_code_examples:
        if not (e.split("/")[-1] in actual): return False
    return True

def check_story(story_name, paragraphs):
    h2o.remove_all()

    h2o.log_and_echo("------------------------------------------------------------")
    h2o.log_and_echo("")
    h2o.log_and_echo("CHECKING: {0}".format(story_name))
    h2o.log_and_echo("")
    h2o.log_and_echo("------------------------------------------------------------")

    # 1. Combine the related, individual code paragraphs into a single, coherent python story
    story = []
    for p in paragraphs:
        with open(p, "r") as f: story = story + f.readlines()

    # 2. Execute the story

    # first, remove any h2o.init calls
    remove_lines = []
    for idx, l in enumerate(story):
        if "h2o.init" in l: remove_lines.append(idx)
    story = [i for j, i in enumerate(story) if j not in remove_lines]

    # write the story that will be executed to the results directory for future reference
    story_file = os.path.join(results_dir(), test_name()+"."+story_name+".code")
    with open(story_file, 'w') as f: f.writelines(story)

    # run it
    with open(story_file, "r") as s: booklet = s.read()
    booklet_c = compile(booklet, '<string>', 'exec')
    p = {}
    exec booklet_c in p

def pybooklet_exec(test_name):
    pyunit = "import h2o\nfrom tests import pybooklet_utils\n"
    with open(test_name, "r") as t: pyunit = pyunit + t.read()
    pyunit_c = compile(pyunit, '<string>', 'exec')
    p = {}
    exec pyunit_c in p