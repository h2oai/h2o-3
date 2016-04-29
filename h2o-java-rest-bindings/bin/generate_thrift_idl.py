import sys, pprint, argparse, errno, re, string

# TODO: ugh:
sys.path.insert(1, '../../py')
import h2o
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
parser.add_argument('--dest', help='destination directory', default=(here + '/../build/src-gen/thrift'))
args = parser.parse_args()

h2o.H2O.verbose = True if args.verbose else False
pp = pprint.PrettyPrinter(indent=4)  # pretty printer for debugging

# Map our simple scalar types to Thrift types
type_mapping = {
    'boolean': 'bool',
    'byte': 'byte',
    'short': 'i16',
    'int': 'i32',
    'long': 'i64',
    'float': 'double',
    'double': 'double',
    'string': 'string',
}

# Thrift reserved words.  We can't use these as field names.  :-(
thrift_reserved_words = set(['from', 'type', 'exception', 'lambda', 'required'])

def optionally_to_list(simple_type, brackets):
    if '' == brackets:
        return simple_type
    elif '[]' == brackets:
        return 'list<' + simple_type + '>'
    elif '[][]' == brackets:
        return 'list<list<' + simple_type + '>>'
    elif brackets is not None and brackets.strip() is not '':
        raise Exception('Found an unexpected value for brackets for field ' + name + ': ' + brackets)


def cons_thrift_type(struct_name, name, h2o_type, schema_name):
    global type_mapping
    
    if h2o_type.startswith('Map'):
        generics = h2o_type[4:-1]

        if string.count(generics, '[') != 0:
            raise Exception("Can't yet handle maps of arrays: " + name)
            
        generics_array = string.split(generics, ',')
        if len(generics_array) != 2:
            raise Exception("Can't yet handle maps of maps: " + name)

        thrift_type = 'map'                                                                               + \
            '<'                                                                                           + \
            (type_mapping[generics_array[0]] if generics_array[0] in type_mapping else generics_array[0]) + \
            ','                                                                                           + \
            (type_mapping[generics_array[1]] if generics_array[1] in type_mapping else generics_array[1]) + \
            '>'
        return thrift_type

    if schema_name is None or h2o_type.startswith('enum'):
        simple_type = h2o_type.replace('[]', '')
    else:
        simple_type = schema_name

    idx = h2o_type.find('[]')
    brackets = '' if idx is -1 else h2o_type[idx:]

    if simple_type in type_mapping:
        return optionally_to_list(type_mapping[simple_type], brackets)
    
    if h2o_type.startswith('Key<'): # Key<Frame> is a schema of FrameKeyVx
        return optionally_to_list('string', brackets)

    if simple_type == 'enum':
        return optionally_to_list(schema_name, brackets)
    if schema_name is not None:
        return optionally_to_list(simple_type, brackets)

    # Polymorphic fields can either be a scalar, a Schema, or an array of either of these:
    if simple_type == 'Polymorphic':
        return 'PrimitiveUnion' # TODO: Polymorphic class?

    # IcedWrapper fields can either be a scalar or an array of scalars or other objects:
    if simple_type == 'IcedWrapper':
        return 'PrimitiveUnion' # TODO: Polymorphic class?

    raise Exception('Unexpectedly found a ' + simple_type + ' field: ' + name + ' in struct: ' + struct_name)


# generate a Schema Struct and find any Enums it uses
def generate_struct(schema, struct_name):
    global args
    global enums
    global thrift_reserved_words

    if args.verbose: print('Generating Struct: ', struct_name)

    struct = []
#    struct.append("namespace water.bindings.structs;")
#    struct.append("")

    superclass = schema['superclass']
    if 'Iced' == superclass:
        # top of the schema class hierarchy
        superclass = 'Object'  # TODO

#    struct.append("struct " + struct_name + " extends {superclass} ".format(superclass=superclass) + '{')
    struct.append("struct " + struct_name + ' {')

    first = True
    field_num = 1
    for field in schema['fields']:
        help = field['help']
        type = field['type'] if field['type'] != 'enum' else field['schema_name']
        name = field['name']
        schema_name = field['schema_name']

        if name == '__meta': continue

        # TODO: don't just drop. . .
        if name in thrift_reserved_words: continue

        if type == 'Iced': continue  # TODO

        thrift_type = cons_thrift_type(struct_name, name, type, schema_name)

        if not first:
            struct.append("")

        required = 'required' if field['required'] else 'optional'

        struct.append("    /** {help} */".format(help=help))
        struct.append("    {num}: {required} {type} {name};".format(num=field_num, required=required, type=thrift_type, name=name))
        first = False
        field_num += 1

    struct.append("}")
    return struct


def generate_enum(name, values):

    if args.verbose: print('Generating enum: ', name)

    struct = []
#    struct.append("namespace water.bindings.structs;")
#    struct.append("")
    struct.append("enum " + name + " {")

    for value in values:
        struct.append("    {value},".format(value=value))

    struct.append("}")
    return struct

# NOTE: not complete yet
def generate_thrift_services(endpoints_meta, all_schemas_map):
    '''
    Walk across all the endpoint metadata returning a map of classnames to service definitions.
    Thrift methods look like this:

    bool postTweet(1:Tweet tweet) throws (1:TwitterUnavailable unavailable)
    '''
    structs = {}
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
        struct = []
        #signatures = {}

        struct.append("package water.bindings.proxies.retrofit;")
        struct.append("")
        struct.append("import water.bindings.structs.*;")
        struct.append("import retrofit.*;")
        struct.append("import retrofit.http.*;")
        struct.append("")
        struct.append("public interface " + entity + " {")

        first = True
        for meta in endpoints_by_entity[entity]:
            path = meta['url_pattern']
            retrofit_path = var_pattern.sub(r'{\1}', path)
            retrofit_path = retrofit_path.replace('\\', '\\\\')
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
                        print('Failed to find parameter: ' + parm + ' for endpoint: ' + repr(meta))
                    field = fields[0]

                    # cons up the proper Java type:
                    parm_type = field['schema_name'] if field['is_schema'] else field['type']
                    if parm_type in thrift_type_map: parm_type = thrift_type_map[parm_type]

                    if not first_parm: parms += ', '
                    parms += parm_type
                    parms += ' '
                    parms += parm
                    first_parm = False

            # check for conflicts:
            #signature = '{method}({parms});'.format(method = method, parms = parms)
            #if signature in signatures:
            #    print 'ERROR: found a duplicate method signature in entity ' + entity + ': ' + signature
            #else:
            #    signatures[signature] = True

            if not first: struct.append('')
            if http_method == 'POST':
                struct.append('    @Headers("Content-Type: application/x-www-form-urlencoded; charset=UTF-8")')
            struct.append('    @{http_method}("{path}")'.format(http_method = http_method, path = retrofit_path))
            struct.append('    {output_schema_name} {method}({parms});'.format(output_schema_name = output_schema_name, method = method, parms = parms))

            first = False

        struct.append("}")
        structs[entity] = struct

    return structs



def add_schema_to_dependency_array(schema, known_schemas):
    '''
    Add a schema and (recursively) its fields to a list of the structs ordered by dependencies.
    Does this by traversing depth first and appending along the way.
    
    Enums are added as well.
    '''
    global all_schemas_map

    for field in schema['fields']:
        if None is field['schema_name']:
            continue

        field_schema_name = field['schema_name']
        if field_schema_name in known_schemas:
            continue

        # have we discovered an enum?
        if field['type'].startswith('enum'):
            enum_name = field['schema_name']
            if enum_name not in enums:
                # save it for later
                enums[enum_name] = field['values']
                all_schemas_map[field_schema_name] = field

        if field_schema_name not in all_schemas_map:
            raise Exception("Failed to find the schema for field: " + field_schema_name)
            
        field_schema = all_schemas_map[field_schema_name]

        if not field_schema['type'].startswith('enum'):
            # don't recurse into enums
            add_schema_to_dependency_array(field_schema, known_schemas)
        known_schemas.append(field_schema_name)


######
# MAIN:
######
if (len(args.usecloud) > 0):
    arr = args.usecloud.split(":")
    args.host = arr[0]
    args.port = int(arr[1])

h2o.H2O.verboseprint("connecting to: ", args.host, ":", args.port)

a_node = h2o.H2O(args.host, args.port)

print('creating the Thrift IDL in {}. . .'.format(args.dest))


save_full = args.dest + os.sep + 'water/bindings/structs/H2O.thrift'
save_dir = os.path.dirname(save_full)

# create dirs without race:
try:
    os.makedirs(save_dir)
except OSError as exception:
    if exception.errno != errno.EEXIST:
        raise

with open(save_full, 'w') as the_file:
    the_file.write('###########################################\n')
    the_file.write('# Thrift bindings for H2O Machine Learning.\n')
    the_file.write('# NOTE: This file is generated. DO NOT EDIT\n')
    the_file.write('###########################################\n')    
    the_file.write("namespace * water.bindings.structs\n")
    the_file.write("union PrimitiveUnion {")
    the_file.write("  1: bool bool_field\n")
    the_file.write("  2: byte byte_field\n")
    the_file.write("  3: i16 i16_field\n")
    the_file.write("  4: i32 i32_field\n")
    the_file.write("  5: i64 i64_field\n")
    the_file.write("  6: double double_field\n")
    the_file.write("  7: binary binary_field\n")
    the_file.write("  8: string string_field\n")
    the_file.write("}")


#####################
# Get all the schemas
#####################
all_schemas = a_node.schemas()['schemas']
all_schemas_map = {}      # map of schema names to schemas; includes enums
all_schemas_ordered = []  # sorted by dependency graph
enums = {}

for schema in all_schemas:
    if 'void' == schema['name']:
        continue

    schema_name = schema['name']
    all_schemas_map[schema_name] = schema

##########################################################################
# Generate an array of schema names that are ordered by the dependency DAG
##########################################################################
for schema in all_schemas:
    if 'void' == schema['name']:
        continue
    add_schema_to_dependency_array(schema, all_schemas_ordered)
    
#################################################################
# Get all the schemas and generate Structs or Enums as appropriate.
# Note the medium ugliness that the enums list is global. . .
#################################################################
for schema_name in all_schemas_ordered:
    if 'void' == schema_name:
        continue

    struct_name = schema_name
    schema = all_schemas_map[schema_name]

    if schema['type'].startswith('enum'):
        with open(save_full, 'a') as the_file:  # NOTE: single file, append
            for line in generate_enum(schema_name, all_schemas_map[schema_name]['values']):
                the_file.write("%s\n" % line)
    else:
        with open(save_full, 'a') as the_file:  # NOTE: single file, append
            for line in generate_struct(all_schemas_map[schema_name], struct_name):
                the_file.write("%s\n" % line)

#########################################################################
# Get the list of endpoints and generate Retrofit proxy methods for them.
#########################################################################
endpoints_result = a_node.endpoints()
endpoints = endpoints_result['routes']

if h2o.H2O.verbose:
    print('Endpoints: ')
    pp.pprint(endpoints)

# Collect all the endpoints:
endpoints_meta = []
for num in range(len(endpoints)):
    meta = a_node.endpoint_by_number(num)['routes'][0]
    endpoints_meta.append(meta)

## Generate source code for a class for each entity (e.g., ModelBuilders):
#retrofitProxies = generate_thrift_services(endpoints_meta, all_schemas_map)
#
## TODO: makedirs only once!
#
## Write them out:
#for entity, proxy in retrofitProxies.iteritems():
#    save_full = args.dest + os.sep + 'water/bindings/proxies/retrofit/' + entity + '.java'
#    save_dir = os.path.dirname(save_full)
#
#    # create dirs without race:
#    try:
#        os.makedirs(save_dir)
#    except OSError as exception:
#        if exception.errno != errno.EEXIST:
#            raise
#
#    with open(save_full, 'w') as the_file:
#        for line in proxy:
#            the_file.write("%s\n" % line)
#
#
######################################################
## Write out an example program that uses the proxies.
######################################################
#retrofit_example = '''
#package water.bindings.proxies.retrofit;
#
#import retrofit.*;
#import retrofit.http.*;
#import water.bindings.structs.*;
#
#public class Example {
#
#    public static void main (String[] args) {
#        RestAdapter restAdapter = new RestAdapter.Builder()
#            .setEndpoint("http://localhost:54321")
#            .build();
#
#        Frames framesService = restAdapter.create(Frames.class);
#        Models modelsService = restAdapter.create(Models.class);
#
#        FramesV3 all_frames = framesService.list();
#        ModelsV3 all_models = modelsService.list();
#    }
#}
#'''
#
#save_full = args.dest + os.sep + 'water/bindings/proxies/retrofit/' + 'Example' + '.java'
#save_dir = os.path.dirname(save_full)
#
## create dirs without race:
#try:
#    os.makedirs(save_dir)
#except OSError as exception:
#    if exception.errno != errno.EEXIST:
#        raise
#
#with open(save_full, 'w') as the_file:
#    the_file.write("%s\n" % retrofit_example)
