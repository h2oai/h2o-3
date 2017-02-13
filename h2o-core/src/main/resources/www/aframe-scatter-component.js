AFRAME.registerComponent('graph', {
  schema: {
    csv: {
      type: 'string'
    },
    id: {
      type: 'int',
      default: '0'
    },
    width: {
      type: 'number',
      default: 1
    },
    height: {
      type: 'number',
      default: 1
    },
    depth: {
      type: 'number',
      default: 1
    },
    xLabelText: {
      type: 'string'
    },
    yLabelText: {
      type: 'string'
    },
    zLabelText: {
      type: 'string'
    },
    xLabelTextScale: {
      type: 'string',
      default: '1 1 1'
    },
    yLabelTextScale: {
      type: 'string',
      default: '1 1 1'
    },
    zLabelTextLineScale: {
      type: 'string',
      default: '1 1 1'
    },
    colorVariable: {
      type: 'string'
    },
    colorVariableDomain: {
      type: 'array'
    },
    colors: {
      type: 'array'
    },
    xVariable: {
      type: 'string'
    },
    yVariable: {
      type: 'string'
    },
    zVariable: {
      type: 'string'
    },
    xScaleType: {
      type: 'string',
      default: 'linear'
    },
    yScaleType: {
      type: 'string',
      default: 'linear'
    },
    zScaleType: {
      type: 'string',
      default: 'linear'
    },
    xScaleLogDomainMin: {
      type: 'string',
      default: '1e-1'
    },
    yScaleLogDomainMin: {
      type: 'string',
      default: '1e-1'
    },
    zScaleLogDomainMin: {
      type: 'string',
      default: '1e-1'
    },
    xScaleDomain: {
      type: 'array'
    },
    yScaleDomain: {
      type: 'array'
    },
    zScaleDomain: {
      type: 'array'
    },
    sphereRadius: {
      type: 'number',
      default: 0.03
    }
  },

  /**
   * Called once when component is attached. Generally for initial setup.
   */
  update() {
    // Entity data
    const el = this.el;
    const object3D = el.object3D;
    const options = this.data;

    if (options.csv) {
      /* Plot data from CSV */
      d3.csv(options.csv, data => {
        plotData(data, el, object3D, options);
      });
    }

    if (options.frameID) {
      getFrameDataFromh2o3(el, object3D, options)
    }
  }
});

function plotData(data, el, object3D, options) {
    const width = options.width;
    const height = options.height;
    const depth = options.depth;
    
    const xLabelText = options.xLabelText;
    const yLabelText = options.yLabelText;
    const zLabelText = options.zLabelText;

    const xLabelTextScale = options.xLabelTextScale;
    const yLabelTextScale = options.yLabelTextScale;
    const zLabelTextScale = options.zLabelTextScale;

    const xScaleType = options.xScaleType;
    const yScaleType = options.yScaleType;
    const zScaleType = options.zScaleType;

    const xScaleLogDomainMin = options.xScaleLogDomainMin;
    const yScaleLogDomainMin = options.yScaleLogDomainMin;
    const zScaleLogDomainMin = options.zScaleLogDomainMin;

    const colorVariable = options.colorVariable;
    const sphereRadius = options.sphereRadius;

    let colors;
    if (
      typeof options.colors !== 'undefined' &&
      options.colors.length > 0
      ) {
      colors = options.colors;
    } else {
      colors = d3.schemeCategory10;
    }
    console.log('colors', colors);

    const xVariable = options.xVariable;
    const yVariable = options.yVariable;
    const zVariable = options.zVariable;
    console.log('xVariable', xVariable);
    console.log('yVariable', yVariable);
    console.log('zVariable', zVariable);

    // These will be used to set the range of the axes' scales
    const xRange = [0, width];
    const yRange = [0, height];
    const zRange = [0, -depth];

    /**
     * Create origin point.
     * This gives a solid reference point for scaling data.
     * It is positioned at the vertex of the left grid and bottom grid (towards the front).
     */
    const originPointPosition = `${-width / 2} 0 ${depth / 2}`;
    const originPointID = `originPoint${options.id}`;

    d3.select(el).append('a-entity')
      .attr('id', originPointID)
      .attr('position', originPointPosition);

    // Create graphing area out of three textured planes
    const grid = gridMaker(width, height, depth);
    object3D.add(grid);

    // Label axes
    // TODO: add a text measuring function
    // then measure label text length
    // the use that length to
    // sprogrammatically position labels
    const xLabelPosition = `0.2 -0.1 0.1'`;
    const xLabelRotation = `-45 0 0`;
    d3.select(`#${originPointID}`)
      .append('a-entity')
      .attr('id', 'x')
      .attr('bmfont-text', `text: ${xLabelText}`)
      .attr('position', xLabelPosition)
      .attr('rotation', xLabelRotation)
      .attr('scale', xLabelTextScale);

    const yLabelPosition = `${width + 0.12} 0.2 ${-depth + 0.08}`;
    const yLabelRotation = `0 -30 90`;
    d3.select(`#${originPointID}`)
      .append('a-entity')
      .attr('id', 'y')
      .attr('bmfont-text', `text: ${yLabelText}`)
      .attr('position', yLabelPosition)
      .attr('rotation', yLabelRotation)
      .attr('scale', yLabelTextScale);

    const zLabelPosition = `${width + 0.03} 0.03 ${-depth + 0.27}`;
    const zLabelRotation = `-45 -90 0`;
    d3.select(`#${originPointID}`)
      .append('a-entity')
      .attr('id', 'z')
      .attr('bmfont-text', `text: ${zLabelText}`)
      .attr('position', zLabelPosition)
      .attr('rotation', zLabelRotation)
      .attr('scale', zLabelTextScale);
    
    const originPoint = d3.select(`#originPoint${options.id}`);

    // create color scale for points
    const colorScale = d3.scaleOrdinal()
      .range(colors);

  // allow user to specify colorVariableDomain
  // to control sort order of legend items
  let colorVariableDomain;
  if (
    typeof options.colorVariableDomain !== 'undefined' &&
    options.colorVariableDomain.length > 0
  ) {
    colorVariableDomain = options.colorVariableDomain;
  } else {
    colorVariableDomain = data.map(d => d[colorVariable]).filter(onlyUnique);
  } 
  colorScale.domain(colorVariableDomain);
  console.log('colorVariableDomain', colorVariableDomain);

  data.forEach(d => {
    if (xScaleType !== 'band') {
      d[xVariable] = Number(d[xVariable]);
    };
    if (yScaleType !== 'band') {
      d[yVariable] = Number(d[yVariable]);
    };
    if (zScaleType !== 'band') {
      d[zVariable] = Number(d[zVariable]);
    };
    d.color = colorScale(d[colorVariable]);
  });
  //
  // Scale x values
  //
  const xExtent = d3.extent(data, d => d[xVariable]);
  let xScale;
  switch (xScaleType) {
    case 'linear':
      xScale = d3.scaleLinear()
        .domain(xExtent)
        .range([xRange[0], xRange[1]])
        .clamp('true');
      break;
    case 'log':
      xScale = d3.scaleLog()
        .domain([Number(xScaleLogDomainMin), d3.max(data, d => d[xVariable])])
        .range([xRange[0], xRange[1]])
        .clamp('true');
      break;
    case 'band':
      let xScaleDomain;
      if (
        typeof options.xScaleDomain !== 'undefined' &&
        options.xScaleDomain.length > 0
      ) {
        xScaleDomain = options.xScaleDomain;
      } else {
        xScaleDomain = data.map(d => d[xVariable]).filter(onlyUnique);
      }
      console.log('xScaleDomain', xScaleDomain);
      xScale = d3.scaleBand()
        .domain(xScaleDomain)
        .range([xRange[0], xRange[1]]);
      break;
  }

  //
  // Scale y values
  //
  const yExtent = d3.extent(data, d => d[yVariable]);
  let yScale;
  switch (yScaleType) {
    case 'linear':
      yScale = d3.scaleLinear()
        .domain(yExtent)
        .range([yRange[0], yRange[1]])
        .clamp('true');
      break;
    case 'log':
      yScale = d3.scaleLog()
        .domain([Number(yScaleLogDomainMin), d3.max(data, d => d[yVariable])])
        .range([yRange[0], yRange[1]])
        .clamp('true');
      break;
    case 'band':
      let yScaleDomain;
      if (
        typeof options.yScaleDomain !== 'undefined' &&
        options.yScaleDomain.length > 0
      ) {
        yScaleDomain = options.yScaleDomain;
      } else {
        yScaleDomain = data.map(d => d[yVariable]).filter(onlyUnique);
      }
      console.log('yScaleDomain', yScaleDomain);
      yScale = d3.scaleBand()
        .domain(yScaleDomain)
        .range([yRange[0], yRange[1]]);
      break;
  }

  //
  // Scale z values
  //
  const zExtent = d3.extent(data, d => d[zVariable]);
  let zScale;
  switch (zScaleType) {
    case 'linear':
      zScale = d3.scaleLinear()
        .domain(zExtent)
        .range([zRange[0], zRange[1]])
        .clamp('true');
      break;
    case 'log':
      zScale = d3.scaleLog()
        .domain([Number(zScaleLogDomainMin), d3.max(data, d => d[zVariable])])
        .range([zRange[0], zRange[1]])
        .clamp('true');
      break;
    case 'band':
      let zScaleDomain;
      if (
        typeof options.zScaleDomain !== 'undefined' &&
        options.zScaleDomain.length > 0
      ) {
        zScaleDomain = options.zScaleDomain;
      } else {
        zScaleDomain = data.map(d => d[zVariable]).filter(onlyUnique);
      }
      console.log('zScaleDomain', zScaleDomain);
      zScale = d3.scaleBand()
        .domain(zScaleDomain)
        .range([zRange[0], zRange[1]]);
      break;
  }

  // TODO: trigger this mousenter event when a Vive controller
  // collides with a data point sphere
  // 
  // Append data to graph and attach event listeners
  originPoint.selectAll('a-sphere')
    .data(data)
    .enter()
    .append('a-sphere')
    .attr('radius', sphereRadius)
    .attr('color', d => d.color)
    .attr('position', d => `${xScale(d[xVariable])} ${yScale(d[yVariable])} ${zScale(d[zVariable])}`)
    .on('mouseenter', mouseEnter);

  /**
   * Event listener adds and removes data labels.
   * "this" refers to sphere element of a given data point.
   */
  function mouseEnter () {
    // Get data
    const data = this.__data__;

    // Get width of graphBox (needed to set label position)
    const graphBoxEl = this.parentElement.parentElement;
    const graphBoxData = graphBoxEl.components.graph.data;
    const graphBoxWidth = graphBoxData.width;

    // Look for an existing label
    const oldLabel = d3.select('#tempDataLabel');
    const oldLabelParent = oldLabel.select(function () { return this.parentNode; });

    // Look for an existing beam
    const oldBeam = d3.select('#tempDataBeam');
    
    // Look for an existing background
    const oldBackground = d3.select('#tempDataBackground');

    const labelMakerOptions = {
      xLabelText,
      yLabelText,
      zLabelText,
      xVariable,
      yVariable,
      zVariable
    }

    // If there is no existing label, make one
    if (oldLabel[0][0] === null) {
      labelMaker(this, graphBoxWidth, labelMakerOptions);
    } else {
      // Remove old label
      oldLabel.remove();
      // Remove beam
      oldBeam.remove();
      // Remove background
      oldBackground.remove();
      // Create new label
      labelMaker(this, graphBoxWidth, labelMakerOptions);
    }
  }

  const legendItemYOffset = 0.3;
  drawLegend(
    data,
    colors,
    colorVariable,
    colorVariableDomain,
    legendItemYOffset
  );

  const frameID = options.frameID;
  const titleOptions = {
    line0Text: 'h2o-3',
    line1Text: 'Frame',
    line2Text: frameID,
    line0Position: '-0.26 0.6 0',
    line1Position: '-0.27 0.4 0',
    line2Position: '-0.80 0.2 0'
  }
  drawTitle(titleOptions);          
} // end plotData()

/* HELPER FUNCTIONS */

/**
 * planeMaker() creates a plane given width and height (kind of).
 *  It is used by gridMaker().
 */
function planeMaker (horizontal, vertical) {
  // Controls texture repeat for U and V
  const uHorizontal = horizontal * 4;
  const vVertical = vertical * 4;

  // Load a texture, set wrap mode to repeat
  const texture = new THREE.TextureLoader()
    .load('https://cdn.rawgit.com/bryik/aframe-scatter-component/master/assets/grid.png');
    // .load('grid.png')
  texture.wrapS = THREE.RepeatWrapping;
  texture.wrapT = THREE.RepeatWrapping;
  texture.anisotropy = 16;
  texture.repeat.set(uHorizontal, vVertical);

  // Create material and geometry
  const material = new THREE.MeshBasicMaterial({
    map: texture,
    side: THREE.DoubleSide
  });
  const geometry = new THREE.PlaneGeometry(horizontal, vertical);

  return new THREE.Mesh(geometry, material);
}

/**
 * gridMaker() creates a graphing box given width, height, and depth.
 * The textures are also scaled to these dimensions.
 *
 * There are many ways this function could be improved or done differently
 * e.g. buffer geometry, merge geometry, better reuse of material/geometry.
 */
function gridMaker (width, height, depth) {
  const grid = new THREE.Object3D();

  // AKA bottom grid
  const xGrid = planeMaker(width, depth);
  xGrid.rotation.x = 90 * (Math.PI / 180);
  grid.add(xGrid);

  // AKA far grid
  const yPlane = planeMaker(width, height);
  yPlane.position.y = (0.5) * height;
  yPlane.position.z = (-0.5) * depth;
  grid.add(yPlane);

  // AKA side grid
  const zPlane = planeMaker(depth, height);
  zPlane.position.x = (-0.5) * width;
  zPlane.position.y = (0.5) * height;
  zPlane.rotation.y = 90 * (Math.PI / 180);
  grid.add(zPlane);

  return grid;
}

/**
 * labelMaker() creates a label for a given data point and graph height.
 * dataEl - A data point's element.
 * graphBoxWidth - The width of the graph.
 */
function labelMaker (dataEl, graphBoxWidth, options) {
  const dataElement = d3.select(dataEl);
  // Retrieve original data
  const dataValues = dataEl.__data__;

  const xVariable = options.xVariable;
  const yVariable = options.yVariable;
  const zVariable = options.zVariable;

  // Create individual x, y, and z labels using original data values
  // round to 1 decimal space (should use d3 format for consistency later)
  const xPointText = `${xLabelText}: ${dataValues[xVariable]}\n \n`;
  const yPointText = `${yLabelText}: ${dataValues[yVariable]}\n \n`;
  const zPointText = `${zLabelText}: ${dataValues[zVariable]}`;
  const pointText = `text: ${xPointText}${yPointText}${zPointText}`;

  // Position label right of graph
  const padding = 0.2;
  const sphereXPosition = dataEl.getAttribute('position').x;
  const labelXPosition = (graphBoxWidth + padding) - sphereXPosition;
  const labelPosition = `${labelXPosition} -0.43 0`;

  // Add pointer
  const beamWidth = labelXPosition;
  // The beam's pivot is in the center
  const beamPosition = `${labelXPosition - (beamWidth / 2)}0 0`;
  dataElement.append('a-box')
    .attr('id', 'tempDataBeam')
    .attr('height', '0.01')
    .attr('width', beamWidth)
    .attr('depth', '0.01')
    .attr('color', 'purple')
    .attr('position', beamPosition);

  // Add label
  dataElement.append('a-entity')
    .attr('id', 'tempDataLabel')
    .attr('bmfont-text', pointText)
    .attr('position', labelPosition);
  
  const backgroundPosition = `${labelXPosition + 1.15} 0.02 -0.1`;
  // Add background card
  dataElement.append('a-plane')
    .attr('id', 'tempDataBackground')
    .attr('width', '2.3')
    .attr('height', '1.3')
    .attr('color', '#ECECEC')
    .attr('position', backgroundPosition);
}

function drawLegend(data, colors, colorVariable, colorVariableDomain, legendItemYOffset) {
  const legendParent = d3.select('a-scene')
    .append('a-entity')
    .attr('position', '0 0 0');

  if (
    typeof colors == 'undefined' &&
    colors.length <= 0
    ) {
    colors = d3.schemeCategory10;
  }
  console.log('colors', colors);

  const colorScale = d3.scaleOrdinal()
    .range(colors);

  const legendEntity = legendParent
    .append('a-entity')
    .attr('class', 'legend')
    .attr('position', '1.4 0.5 -1.5')
    .attr('rotation', '0 -45 0')
    .attr('scale', '0.8 0.8 0.8');

    // allow user to specify colorVariableDomain
    // to control sort order of legend items
    if (
      typeof colorVariableDomain === 'undefined' &&
      colorVariableDomain.length <= 0
    ) {
      colorVariableDomain = data.map(d => d[colorVariable]).filter(onlyUnique);
    } 
    colorScale.domain(colorVariableDomain);

    // draw legend text and a legend icon
    // for all colorVariableDomain values
    legendEntity
      .selectAll('.legendItem')
      .data(colorVariableDomain)
      .enter().append('a-entity')
        .attr('position', (d, i) => `0 ${(colorVariableDomain.length - i - 1) * legendItemYOffset} 0`)
        .attr('bmfont-text', d => `text: ${d}; color: ${colorScale(d)}`)
        .append('a-sphere')
          .attr('radius', '0.03')
          .attr('position', '-0.1 0.05 0')
          .attr('color', d => colorScale(d));    
}

function drawTitle(options) {
  // entity attributes
  const line0Text = options.line0Text;
  const line1Text = options.line1Text;
  const line2Text = options.line2Text;
  const line0Position = options.line0Position;
  const line1Position = options.line1Position;
  const line2Position = options.line2Position;

  const titleParent = d3.select('a-scene')
    .append('a-entity')
    .attr('position', '0 0 0');

  const titleEntity = titleParent
    .append('a-entity') 
    .attr('class', 'title')
    .attr('position', '0 2 -1.8')
    .attr('rotation', '35 0 0');

  // line0
  titleEntity
    .append('a-entity')
    .attr('position', line0Position)
    .append('a-entity')
      .attr('bmfont-text', `text: ${line0Text}`);

  // line1
  titleEntity
    .append('a-entity')
    .attr('position', line1Position)
    .append('a-entity')
      .attr('bmfont-text', `text: ${line1Text}`);

  // line2
  titleEntity
    .append('a-entity')
    .attr('position', line2Position)
    .append('a-entity')
      .attr('bmfont-text', `text: ${line2Text}`);
}

/**
 * onlyUnique() tests if values in an array are unique
 */
function onlyUnique(value, index, self) { 
  return self.indexOf(value) === index;
}

function getFrameDataFromh2o3(el, object3D, options) {
  const server = options.server;
  const port = options.port;
  const frameID = options.frameID;

  // get the number of rows in the aggregated residuals frame
  // ignore fields that are not the row count
  const getRowsFrameOptions = '?_exclude_fields=frames/__meta,frames/chunk_summary,frames/default_percentiles,frames/columns,frames/distribution_summary,__meta';

  // get the row counts for each aggregated residuals frame frameID
  const q0 = d3.queue();

  // console.log('options from getResidualsDataFromh2o3', options);

  const getRowsRequestURL = `${server}:${port}/3/Frames/${frameID}/summary${getRowsFrameOptions}`;
  console.log('getRowsRequestURL', getRowsRequestURL);
  q0.defer(d3.request, getRowsRequestURL);

  q0.await(getFrameDataRequest)

  // get the aggregated residuals data from h2o-3
  function getFrameDataRequest(error, response) {
    if (error) console.error(error);
    console.log('arguments from getFrameDataRequest', arguments);

    const parsedRowResponse = JSON.parse(response.response);
    const frame = {
      rowCount: parsedRowResponse.frames[0].rows,
      columnCount: parsedRowResponse.frames[0].column_count,
      frameID: parsedRowResponse.frames[0].frame_id.name
    }
    console.log('parsedRowRespone', parsedRowResponse);
    console.log('frame from getResidualsFrames', frame);

    const q1 = d3.queue();

    const rowCount = frame.rowCount;
    const columnCount = frame.columnCount;
    const frameID = frame.frameID;
    console.log('frame from getFrameDataRequest', frame);

    const frameOptions = `?column_offset=0&column_count=${columnCount}&row_count=${rowCount}`;
    const getDataRequestURL = `${server}:${port}/3/Frames/${frameID}${frameOptions}`;
    console.log('getDataRequestURL', getDataRequestURL);
    q1.defer(d3.request, getDataRequestURL)
    q1.await(handleResponse);
    
    function handleResponse(error, response) {
      if (error) console.error(error);
      console.log('handleResponse was called');
      console.log('arguments from handleResponse', arguments);
      console.log('response', response);
      const parsedResponse = parseResponse(response);
      plotData(parsedResponse, el, object3D, options);
    }
  }
}


function parseResponse(response) {
  const responseData = JSON.parse(response.response);
  const columnsData = responseData.frames[0].columns;
  const points = [];
  columnsData.forEach(d => {
    // console.log('d.label', d.label);
    // console.log('d from columnsData', d);
    if (Object.prototype.toString.call(d.data) === '[object Array]') {
      // if the current column is an enum or a category
      // recognize that the value in the data is acually a index for the
      // domain array
      // the value at that index position in the domain array
      // is actually the datum that we want
      d.data.forEach((e, j) => {
        if (typeof points[j] === 'undefined') points[j] = {};
          let value;
          if (d.type === "enum" && d.domain !== null) {
            value = d.domain[e];
          } else {
            value = e;
          }
          points[j][d.label] = value;
      });
    }
  });
  console.log('columnsData', columnsData);
  console.log('points', points);

  points.forEach((d, i) => {
    d.id = i;
  });

  const parsedData = points;

  // console.log('parsedData', parsedData);
  return parsedData;
}