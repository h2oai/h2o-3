fs = require 'fs'
path = require 'path'
mkdirp = (require 'mkdirp').sync
htmlparser = require 'htmlparser2'
fiction = require 'fiction'

stripHtmlTags = (html) ->
  _text = ''
  parser = new htmlparser.Parser
    ontext: (text) -> _text += text
  parser.write html
  parser.end()
  _text

nameNodes = (nodes, prefix) ->
  for node, nodeIndex in nodes
    node.name = "#{if prefix then prefix + '_' else ''}#{nodeIndex + 1}"
    if node.children.length
      nameNodes node.children, node.name
  return

walkNodes = (nodes, go) ->
  for node in nodes
    go node
    if node.children.length
      walkNodes node.children, go
  return

createCatalog = (nodes, catalog) ->
  for node in nodes
    createCatalog node.children, children = []
    catalog.push
      name: node.name
      title: node.title
      # TODO uncomment when implementing in-browser fulltext search
      # text: stripHtmlTags node.content
      children: children
  return

processMarkdownFile = (markdownFilePath, outputDirectory) ->
  nodes = fiction fs.readFileSync markdownFilePath, encoding: 'utf8'
  nameNodes nodes, ''

  mkdirp outputDirectory

  walkNodes nodes, (node) ->
    fs.writeFileSync (path.join outputDirectory, "#{node.name}.html"), node.content

  createCatalog nodes, catalog = []
  fs.writeFileSync (path.join outputDirectory, 'catalog.json'), JSON.stringify catalog, null, 2

[ markdownFilePath, outputDirectory ] = process.argv.slice 2
processMarkdownFile markdownFilePath, outputDirectory
