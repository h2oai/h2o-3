Steam.CloudView = (_) ->
  _cloud = node$ null
  _timestamp = node$ Date.now()
  format3f = d3.format '.3f' # precision = 3

  _sizes = ['B', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB'];
  prettyPrintBytes = (bytes) ->
    return '-' if bytes is 0
    i = Math.floor Math.log(bytes) / Math.log(1024)
    (bytes / Math.pow(1024, i)).toFixed(2) + ' ' + _sizes[i]

  formatThreads = (fjs) ->
    for max_lo in [ 120 ... 0 ]
      if fjs[max_lo - 1] isnt -1
        break
    s = '['
    for i in [ 0 ... max_lo ]
      s += Math.max fjs[i], 0
      s += '/'
    s += '.../'
    for i in [ 120 ... fjs.length - 1 ]
      s += fjs[i]
      s += '/'
    s += fjs[fjs.length - 1]
    s += ']'

    s
    
  createNode = (node) ->
    isUnhealthy: not node.healthy
    name: node.h2o.node
    ping: (moment new Date node.last_ping).fromNow()
    cores: node.num_cpus
    keys: node.num_keys
    tcps: node.tcps_active
    fds: if node.open_fds < 0 then '-' else node.open_fds
    loadProgress: "#{Math.ceil node.sys_load}%"
    load: format3f node.sys_load
    dataProgress: "#{Math.ceil node.mem_value_size / node.total_value_size * 100}%"
    data: "#{prettyPrintBytes node.mem_value_size} / #{prettyPrintBytes node.total_value_size}"
    cachedData: if node.total_value_size is 0 then '-' else " (#{Math.floor node.mem_value_size * 100 / node.total_value_size}%)"
    memoryProgress1: "#{Math.ceil (node.tot_mem - node.free_mem) / node.max_mem * 100}%"
    memoryProgress2: "#{Math.ceil node.free_mem / node.max_mem * 100}%"
    memory: "#{prettyPrintBytes node.free_mem} / #{prettyPrintBytes node.tot_mem} / #{prettyPrintBytes node.max_mem}"
    diskProgress: "#{Math.ceil (node.max_disk - node.free_disk) / node.max_disk * 100}%"
    disk: "#{prettyPrintBytes node.free_disk} / #{prettyPrintBytes node.max_disk}"
    freeDisk: if node.max_disk is 0 then '' else " (#{Math.floor node.free_disk * 100 / node.max_disk}%)"
    rpcs: node.rpcs_active
    threads: formatThreads node.fjthrds
    tasks: formatThreads node.fjqueue
    pid: node.pid

  createNodes = (nodes) ->
    map nodes, createNode

  createCloud = (result) ->
    name: result.cloud_name
    size: describeCount result.cloud_size, 'Node'
    canAcceptNodes: not result.locked
    isAddingNodes: not result.consensus
    badNodeMessage: "#{result.bad_nodes} nodes in this cloud are unhealthy." 
    hasBadNodes: result.bad_nodes > 0
    nodes: createNodes result.nodes

  refresh = ->
    _.requestCloud (error, result) ->
      if error
        #TODO
      else
        _cloud createCloud result
        _timestamp Date.now()

  refresh()

  cloud: _cloud
  timestamp: _timestamp
  template: 'cloud-view'
