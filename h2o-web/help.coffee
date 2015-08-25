argv = (require 'minimist') process.argv.slice 2
fs = require 'fs-extra'
path = require 'path'
mkdirp = (require 'mkdirp').sync
request = require 'request'
fiction = require 'fiction'
diecut = require 'diecut'
marked = require 'marked'
yaml = require 'js-yaml'
_ = require 'lodash'

EOL = "\n"
mustache = /\{\{\s*(.+?)\s*\}\}/g
words = (str) -> str.split /\s+/g
trimArray = (lines) -> lines.join(EOL).trim().split(EOL)
locate = (names...) -> path.join.apply null, [ __dirname, '../h2o-docs' ].concat names
read = (src) ->
  console.log "Reading #{src}"
  fs.readFileSync src, encoding: 'utf8'
write = (src, data) ->
  console.log "Writing #{src}"
  fs.outputFileSync src, data
rm = (src) -> 
  console.log "Removing #{src}"
  fs.removeSync src
cp = (src, dest) -> 
  console.log "Copying #{src} #{dest}"
  fs.copySync src, dest
cpn = (src, dest) ->
  cp src, dest unless fs.existsSync dest

[ div, h1, h2, h3, p, table, thead, tbody, tr, th, thr, td, td1, tdr, ul, li, code, bookmark, link, red ] = diecut 'div', 'h1', 'h2', 'h3', 'p', 'table', 'thead', 'tbody', 'tr', 'th', 'th.right', 'td', 'td.right width="1"', 'td.right', 'ul', 'li', 'code', 'a name="$1"', 'a href="$1"', 'span.red'

br = '<br/>'

typeOf = (obj) -> obj?.__meta?.schema_type

get = (host, route, go) ->
  opts =
    method: 'GET'
    url: "http://#{host}#{route}"
    json: yes

  request opts, (error, response, body) ->
    if not error and response.statusCode is 200
      go error, body
    else
      cause = if 'H2OError' is typeOf body
        body.exception_msg
      else if error?.message
        error.message
      else if _.isString error
        error
      else
        JSON.stringify error

      go new Error "Error calling #{opts.method} #{opts.url}: " + cause

linkTo = (bookmark) -> "##{bookmark}"

directionOf = (direction) ->
  switch direction
    when 'INPUT'
      'In'
    when 'INOUT'
      'In/Out'
    when 'OUTPUT'
      'Out'

contentOf = (a) -> a.slice 3, a.length - 5

printSchema = (schema) ->
  fields = _.sortBy schema.fields, (field) ->
    switch field.direction
      when 'INPUT'
        'A'
      when 'INOUT'
        'B'
      else
        'C'

  trs = for field in fields when field.name isnt '__meta'
    tr [
      td1 [
        code field.name
        br
        code if field.is_schema then link field.type, "#schema-#{field.schema_name}" else red field.type
      ]
      td contentOf marked field.help or '(No description available)'
      tdr directionOf field.direction
    ]

  [
    bookmark '', "schema-#{schema.name}"
    h2 schema.name
    if trs.length then table tbody trs else p "(No fields)"
  ].join EOL

printSchemaToC = (schemas) ->
  lis = for schema in schemas
    li link schema.name, "#schema-#{schema.name}"
  ul lis

printSchemas = (schemas) ->
  [
    bookmark '', 'schema-reference'
    h1 'REST API Schema Reference'
    printSchemaToC schemas
    (schemas.map printSchema).join EOL
  ].join EOL

printRoute = (route) ->
  trs = [
    tr [
      td1 'Input'
      td code link route.input_schema, "#schema-#{route.input_schema}"
    ]
    tr [
      td1 'Output'
      td code link route.output_schema, "#schema-#{route.output_schema}"
    ]
  ]
  [
    bookmark '', "route-#{encodeURIComponent route.url_pattern}"
    h2 "#{route.http_method} #{route.url_pattern}"
    p contentOf marked route.summary
    table tbody trs
  ].join EOL

printRouteToC = (routes) ->
  lis = for route in routes
    li link route.url_pattern, "#route-#{encodeURIComponent route.url_pattern}"
  ul lis

printRoutes = (routes) ->
  [
    bookmark '', 'route-reference'
    h1 'REST API Reference'
    printRouteToC routes
    (routes.map printRoute).join EOL
  ].join EOL

nameNodes = (nodes, prefix) ->
  for node in nodes
    name = (if prefix then prefix + '-' else '') + node.title
    node.name = encodeURIComponent name
    if node.children.length
      nameNodes node.children, name
  return

walkNodes = (nodes, depth, go) ->
  for node in nodes
    go node, depth
    if node.children.length
      walkNodes node.children, depth + 1, go
  return

printSource = (nodes) ->
  toc = []
  content = []
  nameNodes nodes, ''
  walkNodes nodes, 1, (node, depth) -> 
    content.push bookmark '', node.name
    content.push "<h#{depth}>#{node.title}</h#{depth}>"
    if depth is 1
      toc.push li link node.title, "##{node.name}"
    if node.children.length > 1
      lis = node.children.map (child) -> li link child.title, "##{child.name}"
      content.push ul lis
    content.push node.content
  [
    toc.join EOL
    content.join EOL
  ]

printSources = (sources) ->
  tocs = []
  contents = []
  for source in sources
    nodes = fiction read locate source
    [ toc, content ] = printSource nodes
    tocs.push toc
    contents.push content
  [
    tocs.join EOL
    contents.join EOL
  ]

print = (properties, _schemas, _routes) ->
  schemas = _.sortBy _schemas, (schema) -> schema.name
  routes = _.sortBy _routes, (route) -> route.url_pattern

  [ toc, contents ] = printSources properties.sources

  sidebar = [
    h2 'Contents'
    ul toc
    h2 'API Reference'
    ul [
      li link 'REST API Endpoints',  '#route-reference'
      li link 'REST API Schemas',    '#schema-reference'
      li link 'R API',               "http://h2o-release.s3.amazonaws.com/h2o/#{argv.branch_name}/#{argv.build_number}/docs-website/h2o-r/h2o_package.pdf"
      li link 'Python API',          "http://h2o-release.s3.amazonaws.com/h2o/#{argv.branch_name}/#{argv.build_number}/docs-website/h2o-py/docs/index.html"
      li link 'h2o-core Javadoc',    "http://h2o-release.s3.amazonaws.com/h2o/#{argv.branch_name}/#{argv.build_number}/docs-website/h2o-core/javadoc/index.html"
      li link 'h2o-algos Javadoc',   "http://h2o-release.s3.amazonaws.com/h2o/#{argv.branch_name}/#{argv.build_number}/docs-website/h2o-algos/javadoc/index.html"
      li link 'POJO Model Javadoc',  "http://h2o-release.s3.amazonaws.com/h2o/#{argv.branch_name}/#{argv.build_number}/docs-website/h2o-genmodel/javadoc/index.html"
      li link 'h2o-scala Scaladoc',  "http://h2o-release.s3.amazonaws.com/h2o/#{argv.branch_name}/#{argv.build_number}/docs-website/h2o-scala/scaladoc/index.html"
      li link 'Sparkling Water API', 'https://github.com/h2oai/sparkling-water/blob/master/DEVEL.md'
      li link 'Build page',          "http://h2o-release.s3.amazonaws.com/h2o/#{argv.branch_name}/#{argv.build_number}/index.html"
    ]
  ].join EOL

  body = [
    contents
    printRoutes routes
    printSchemas schemas
  ].join EOL

  body = body.replace mustache, (match, key) ->
    if value = argv[key]
      value
    else
      ''

  template = read locate 'template', 'index.html'
  html = template.replace mustache, (match, key) ->
    switch key
      when 'version'
        argv.project_version or ''
      when 'toc'
        sidebar
      when 'content'
        body

  write (locate 'web', 'index.html'), html
  cpn (locate 'template', 'javascripts', 'scale.fix.js'), (locate 'web', 'javascripts', 'scale.fix.js')
  cpn (locate 'template', 'stylesheets', 'swirl.png'), (locate 'web', 'stylesheets', 'swirl.png')
  cp (locate 'template', 'stylesheets', 'styles.css'), (locate 'web', 'stylesheets', 'styles.css')

  for asset in properties.assets
    fs.copySync (locate asset.source), (locate 'web', asset.target)
  return

main = (args, properties) ->
  schemas_json = locate 'schemas.json'
  routes_json = locate 'routes.json'
  if (fs.lstatSync schemas_json).isFile() and (fs.lstatSync routes_json).isFile()
    print properties, (JSON.parse read schemas_json).schemas, (JSON.parse read routes_json).routes
  else
    throw new Error 'Could not locate API JSON documents.'

main argv, yaml.safeLoad read locate 'index.yml'

