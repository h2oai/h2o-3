_ = require 'lodash'
esprima = require 'esprima'
escodegen = require 'escodegen'

traverse = (parent, key, node, f) ->
  if _.isArray node
    i = node.length
    while i--
      child = node[i]
      if child isnt null and _.isObject child
        traverse node, i, child, f
        f node, i, child
  else 
    for own i, child of node
      if child isnt null and _.isObject child
        traverse node, i, child, f
        f node, i, child
  return

deleteNode = (parent, i) ->
  if _.isArray parent
    parent.splice i, 1
  else if isObject parent
    delete parent[i]

extractDependencies = (symbols, ast) ->
  objectLookup = {}
  for identifier, symbol of symbols
    objectLookup[symbol.object] = yes

  dependencies = []
  traverse null, null, ast, (parent, i, node) ->
    if node.type is 'ExpressionStatement'
      { expression } = node
      switch expression.type
        when 'CallExpression'
          if expression.callee.type is 'Identifier' and expression.callee.name is 'require'
            isResolved = no
            for arg in expression.arguments
              if arg.type is 'Literal'
                if objectLookup[arg.value]
                  dependencies.push arg.value
                  isResolved = yes
            deleteNode parent, i if isResolved
  dependencies

rewrite = (ast, namespace) ->
  traverse null, null, ast, (parent, i, node) ->
    # remove hoisted vars		
    if node.type is 'VariableDeclaration'		
      declarations = node.declarations.filter (declaration) ->		
        declaration.type is 'VariableDeclarator' and declaration.id.type is 'Identifier' and not namespace[declaration.id.name]		
      if declarations.length is 0		
        # purge this node so that escodegen doesn't fail		
        deleteNode parent, i		
      else		
        # replace with cleaned-up declarations		
        node.declarations = declarations

    # bail out if imported identifiers are used as function params
    else if node.type is 'FunctionExpression' or node.type is 'FunctionDeclaration'
      for param in node.params when param.type is 'MemberExpression'
        throw new Error "Function has a formal parameter name-clash with '#{param.object.name}.#{param.property.name}'. Correct this and try again." 

    # replace identifier with qualified name
    else if node.type is 'Identifier'
      return if parent.type is 'VariableDeclarator' and i is 'id' # ignore var declarations
      return if i is 'property' # ignore members
      if parent.type is 'CallExpression'
        argCount = parent.arguments.length
        if expression = namespace["#{node.name}$#{argCount}"]
        else if expression = namespace[node.name]
        else
          return
      else
        return unless expression = namespace[node.name]

      return unless expression.type is 'qualify'

      parent[i] =
        type: 'MemberExpression'
        computed: no
        object:
          type: 'Identifier'
          name: expression.object
        property:
          type: 'Identifier'
          name: expression.property or node.name

    # transform bare function calls to method invocations
    else if node.type is 'CallExpression'
      return unless node.callee.type is 'Identifier'
      return unless expression = namespace[node.callee.name]
      return unless expression.type is 'invoke'
      return unless node.arguments.length > 0
      [ first, rest... ] = node.arguments
      node.callee =
        type: 'MemberExpression'
        computed: no
        object: first
        property:
          type: 'Identifier'
          name: expression.property or node.callee.name

      node.arguments = rest
  return

compile = (symbols, js, options={}) ->
  ast = esprima.parse js
  #console.log '------- original -----------'
  #dump ast
  dependencies = (options.implicits or []).concat extractDependencies symbols, ast

  if dependencies.length
    lookup = {}
    for dependency in dependencies
      lookup[dependency] = yes

    namespace = {}
    for symbol, expression of symbols
      switch expression.type
        when 'qualify'
          if lookup[expression.object]
            namespace[symbol] = expression
        when 'invoke'
          namespace[symbol] = expression

  rewrite ast, namespace or {}

  #console.log '------- rewritten ----------'
  #dump ast
  try
    escodegen.generate ast
  catch error
    #console.log '------- faulty -----------'
    #dump ast
    throw error

module.exports = compile
