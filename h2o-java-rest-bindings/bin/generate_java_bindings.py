# TODO: ugh:
import sys, pprint, argparse, string, errno, sets, re

sys.path.insert(1, '../../py')
import h2o, h2o_util
import os

# print "ARGV is:", sys.argv

here=os.path.dirname(os.path.realpath(__file__))

parser = argparse.ArgumentParser(
    description='Attach to an H2O instance and call its REST API to generate the Java REST API bindings docs and write them to the filesystem.',
)
parser.add_argument('--verbose', '-v', help='verbose output', action='store_true')
parser.add_argument('--usecloud', help='ip:port to attach to', default='')
parser.add_argument('--host', help='hostname to attach to', default='localhost')
parser.add_argument('--port', help='port to attach to', type=int, default=54321)
parser.add_argument('--dest', help='destination directory', default=(here + '/../build/src-gen/main/java'))
args = parser.parse_args()

h2o.H2O.verbose = True if args.verbose else False
pp = pprint.PrettyPrinter(indent=4)  # pretty printer for debugging

def cons_java_type(pojo_name, name, h2o_type, schema_name):
    if schema_name is None or h2o_type.startswith('enum'):
        simple_type = string.replace(h2o_type, '[]', '')
        idx = string.find(h2o_type, '[]')
        brackets = '' if idx is -1 else h2o_type[idx:]
    else:
        simple_type = schema_name
        idx = string.find(h2o_type, '[]')
        brackets = '' if idx is -1 else h2o_type[idx:]

    if simple_type == 'string':
        return string.capitalize(simple_type) + brackets
    if h2o_type.startswith('Key<'): # Key<Frame> is a schema of FrameKeyVx
        return 'String' + brackets
    if simple_type in ['int', 'float', 'double', 'long', 'boolean', 'byte', 'short']:
        return simple_type + brackets
    if simple_type == 'enum':
        return schema_name + brackets
    if schema_name is not None:
        return simple_type + brackets


    # Polymorphic fields can either be a scalar, a Schema, or an array of either of these:
    if simple_type == 'Polymorphic':
        return 'Object' # TODO: Polymorphic class?

    # IcedWrapper fields can either be a scalar or an array of either of scalars:
    if simple_type == 'IcedWrapper':
        return 'Object' # TODO: Polymorphic class?

    raise Exception('Unexpectedly found a ' + simple_type + ' field: ' + name + ' in pojo: ' + pojo_name)


# generate a Schema POJO and find any Enums it uses
def generate_pojo(schema, pojo_name):
    global args
    global enums

    if args.verbose: print 'Generating POJO: ', pojo_name

    pojo = []
    pojo.append("package water.bindings.pojos;")
    pojo.append("")

    has_map = False
    for field in schema['fields']:
        if field['type'].startswith('Map'):
            has_map = True

    if has_map:
        pojo.append("import java.util.Map;")
        pojo.append("")

    superclass = schema['superclass']
    if 'Iced' == superclass:
        # top of the schema class hierarchy
        superclass = 'Object'

    pojo.append("public class " + pojo_name + " extends {superclass} ".format(superclass=superclass) + '{')

    first = True
    for field in schema['fields']:
        help = field['help']
        type = field['type']
        name = field['name']
        schema_name = field['schema_name']

        if name == '__meta': continue

        if type == 'Iced': continue  # TODO

        if type.startswith('Map'):
            java_type = type
        else:
            java_type = cons_java_type(pojo_name, name, type, schema_name)
        
        if type.startswith('enum'):
            enum_name = field['schema_name']
            if enum_name not in enums:
                # save it for later
                enums[enum_name] = field['values']

        if not first:
            pojo.append("")

        pojo.append("    /** {help} */".format(help=help))
        pojo.append("    public {type} {name};".format(type=java_type, name=name))
        first = False

    pojo.append("}")
    return pojo
    

def generate_enum(name, values):

    if args.verbose: print 'Generating enum: ', name

    pojo = []
    pojo.append("package water.bindings.pojos;")
    pojo.append("")
    pojo.append("public enum " + name + " {")

    for value in values:
        pojo.append("    {value},".format(value=value))

    pojo.append("}")
    return pojo
    

def generate_retrofit_proxies(endpoints_meta, all_schemas_map):
    '''
    Walk across all the endpoint metadata returning a map of classnames to interface definitions.
    Retrofit interfaces look like this:

    public interface GitHubService {
        @GET("/users/{user}/repos")
        List<Repo> listRepos(@Path("user") String user);
    }
    '''
    pojos = {}
    java_type_map = { 'string': 'String' }

    endpoints_by_entity = {}  # entity (e.g., Frames) maps to an array of endpoints

    # For each endpoint grab the endpoint prefix (the entity), e.g. ModelBuilders, for use as the classname:
    entity_pattern_str = r"/[0-9]+?/([^/]+)(/.*)?"  # Python raw string
    entity_pattern = re.compile(entity_pattern_str)

    for meta in endpoints_meta:
        h2o.H2O.verboseprint('finding entity for url_pattern: ' + meta['url_pattern'])
        m = entity_pattern.match(meta['url_pattern'])
        entity = m.group(1)

        # If the route contains a suffix like .bin strip it off.
        if '.' in entity:
            entity = entity.split('.')[0]

        h2o.H2O.verboseprint('found entity: ' + entity)

        if entity not in endpoints_by_entity:
            endpoints_by_entity[entity] = []
        endpoints_by_entity[entity].append(meta)


    # replace path vars like (?<schemaname>.*) with {schemaname} for Retrofit's annotation
    # TODO: fails for /3/Metadata/endpoints/(?<num>[0-9]+)
    var_pattern_str = r"\(\?<(.+?)>\.\*\)"  # Python raw string
    var_pattern = re.compile(var_pattern_str)

    # Walk across all the entities and generate a class with methods for all its endpoints:
    for entity in endpoints_by_entity:
        pojo = []
        signatures = {}

        pojo.append("package water.bindings.proxies.retrofit;")
        pojo.append("")
        pojo.append("import water.bindings.pojos.*;")
        pojo.append("import retrofit.*;")
        pojo.append("import retrofit.http.*;")
        pojo.append("")
        pojo.append("public interface " + entity + " {")

        first = True
        for meta in endpoints_by_entity[entity]:
            path = meta['url_pattern']
            retrofit_path = var_pattern.sub(r'{\1}', path)
            http_method = meta['http_method']
            input_schema_name  = meta['input_schema']
            output_schema_name = meta['output_schema']

            handler_method = meta['handler_method']

            method = handler_method

            # TODO: handle query parameters from RequestSchema
            if http_method == 'POST':
                parms = input_schema_name + ' ' + 'parms'
            else:
                parms = ""
                path_parm_names = meta['path_params']
                input_schema = all_schemas_map[input_schema_name]
                
                first_parm = True
                for parm in path_parm_names:
                    # find the metadata for the field from the input schema:
                    fields = [field for field in input_schema['fields'] if field['name'] == parm]
                    if len(fields) != 1:
                        print 'Failed to find parameter: ' + parm + ' for endpoint: ' + repr(meta)
                    field = fields[0]

                    # cons up the proper Java type:
                    parm_type = field['schema_name'] if field['is_schema'] else field['type']
                    if parm_type in java_type_map: parm_type = java_type_map[parm_type]
                    
                    if not first_parm: parms += ', '
                    parms += parm_type
                    parms += ' '
                    parms += parm
                    first_parm = False

            # check for conflicts:
            signature = '{method}({parms});'.format(method = method, parms = parms)
            if signature in signatures:
                print 'ERROR: found a duplicate method signature in entity ' + entity + ': ' + signature
            else:
                signatures[signature] = True

            if not first: pojo.append('')
            if http_method == 'POST':
                pojo.append('    @Headers("Content-Type: application/x-www-form-urlencoded; charset=UTF-8")')
            pojo.append('    @{http_method}("{path}")'.format(http_method = http_method, path = retrofit_path))
            pojo.append('    {output_schema_name} {method}({parms});'.format(output_schema_name = output_schema_name, method = method, parms = parms))
            
            first = False

        pojo.append("}")
        pojos[entity] = pojo

    return pojos


######
# MAIN:
######
if (len(args.usecloud) > 0):
    arr = args.usecloud.split(":")
    args.host = arr[0]
    args.port = int(arr[1])

h2o.H2O.verboseprint("connecting to: ", args.host, ":", args.port)

a_node = h2o.H2O(args.host, args.port)

print 'creating the Java bindings in {}. . .'.format(args.dest)


#################################################################
# Get all the schemas and generate POJOs or Enums as appropriate.
# Note the medium ugliness that the enums list is global. . .
#################################################################
enums = {}

# write the schemas' POJOs, discovering enums on the way
all_schemas = a_node.schemas()['schemas']
all_schemas_map = {}  # save for later use

for schema in all_schemas:
    if 'void' == schema['name']: 
        continue;

    schema_name = schema['name']
    pojo_name = schema_name;

    all_schemas_map[schema_name] = schema

    save_full = args.dest + os.sep + 'water/bindings/pojos/' + pojo_name + '.java'
    save_dir = os.path.dirname(save_full)

    # create dirs without race:
    try:
        os.makedirs(save_dir)
    except OSError as exception:
        if exception.errno != errno.EEXIST:
            raise

    with open(save_full, 'w') as the_file:
        for line in generate_pojo(schema, pojo_name):
            the_file.write("%s\n" % line)

########################
# Generate Enum classes.
########################
for name, values in enums.iteritems():
    pojo_name = name;

    save_full = args.dest + os.sep + 'water/bindings/pojos/' + pojo_name + '.java'
    save_dir = os.path.dirname(save_full)

    # create dirs without race:
    try:
        os.makedirs(save_dir)
    except OSError as exception:
        if exception.errno != errno.EEXIST:
            raise

    with open(save_full, 'w') as the_file:
        for line in generate_enum(name, values):
            the_file.write("%s\n" % line)

#########################################################################
# Get the list of endpoints and generate Retrofit proxy methods for them.
#########################################################################
endpoints_result = a_node.endpoints()
endpoints = endpoints_result['routes']

if h2o.H2O.verbose:
    print 'Endpoints: '
    pp.pprint(endpoints)

# Collect all the endpoints:
endpoints_meta = []
for num in range(len(endpoints)):
    meta = a_node.endpoint_by_number(num)['routes'][0]
    endpoints_meta.append(meta)

# Generate source code for a class for each entity (e.g., ModelBuilders):
retrofitProxies = generate_retrofit_proxies(endpoints_meta, all_schemas_map)

# TODO: makedirs only once!

# Write them out:
for entity, proxy in retrofitProxies.iteritems():
    save_full = args.dest + os.sep + 'water/bindings/proxies/retrofit/' + entity + '.java'
    save_dir = os.path.dirname(save_full)

    # create dirs without race:
    try:
        os.makedirs(save_dir)
    except OSError as exception:
        if exception.errno != errno.EEXIST:
            raise

    with open(save_full, 'w') as the_file:
        for line in proxy:
            the_file.write("%s\n" % line)


#####################################################
# Write out an example program that uses the proxies.
#####################################################
retrofit_example = '''
package water.bindings.proxies.retrofit;

import retrofit.*;
import retrofit.http.*;
import water.bindings.pojos.*;

public class Example {

    public static void main (String[] args) {
        RestAdapter restAdapter = new RestAdapter.Builder()
            .setEndpoint("http://localhost:54321")
            .build();

        Frames framesService = restAdapter.create(Frames.class);
        Models modelsService = restAdapter.create(Models.class);

        FramesV3 all_frames = framesService.list();
        ModelsV3 all_models = modelsService.list();
    }
}
'''

save_full = args.dest + os.sep + 'water/bindings/proxies/retrofit/' + 'Example' + '.java'
save_dir = os.path.dirname(save_full)

# create dirs without race:
try:
    os.makedirs(save_dir)
except OSError as exception:
    if exception.errno != errno.EEXIST:
        raise

with open(save_full, 'w') as the_file:
    the_file.write("%s\n" % retrofit_example)
