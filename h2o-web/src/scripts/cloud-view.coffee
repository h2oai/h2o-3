Steam.CloudView = (_) ->
  _nodes = node$ null
  _timestamp = node$ Date.now()
  format3f = d3.format '.3f' # precision = 3

  _sizes = ['B', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB'];
  prettyPrintBytes = (bytes) ->
    return '0 B' if bytes is 0
    i = Math.floor Math.log(bytes) / Math.log(1024)
    (bytes / Math.pow(1024, i)).toFixed(2) + ' ' + _sizes[i]

  createNode = (node) ->
    attributes = []

    # Basic node health
    nodeName = node.h2o.node
    lastPing = (moment new Date node.last_ping).fromNow()
    systemLoad = format3f node.sys_load

    # Data footprint
    dataPerc = if node.total_value_size is 0 then '' else " (#{Math.floor node.mem_value_size * 100 / node.total_value_size}%)"
    dataFootprint = "#{prettyPrintBytes node.total_value_size}#{dataPerc}"

    numberOfKeys = node.num_keys

    # GC health
    freeMemory = prettyPrintBytes node.free_mem
    totalMemory = prettyPrintBytes node.tot_mem
    maxMemory = prettyPrintBytes node.max_mem

    # Disk health
    diskPerc = if node.max_disk is 0 then '' else " (#{Math.floor node.free_disk * 100 / node.max_disk}%)"
    diskHealth = "#{prettyPrintBytes node.max_disk}#{diskPerc}"

    # CPU Fork/Join Activity
    #ab.p("<td nowrap>").p(Integer.toString(rpcs)+fjq(fjthrds)+fjq(fjqueue)).p("</td>");

    # File Descripters and System
    tcpsAndFds = "#{node.tcps_active} / #{if node.open_fds < 0 then '-' else node.open_fds}"
    cores = node.num_cpus
    pid = node.pid

    attributes = [
      nodeName
      lastPing
      systemLoad
      dataFootprint
      numberOfKeys
      freeMemory
      totalMemory
      maxMemory
      diskHealth
      tcpsAndFds
      cores
      pid
    ]

    isUnhealthy: not node.healthy
    attributes: attributes

  createNodes = (nodes) ->
    map nodes, createNode

  refresh = ->
    _.requestCloud (error, result) ->
      if error
        #TODO
      else
        _nodes createNodes result.nodes
        _timestamp Date.now()

  refresh()

  nodes: _nodes
  timestamp: _timestamp
  template: 'cloud-view'
