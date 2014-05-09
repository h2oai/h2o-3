function zip(x, y) {
    return $.map(x, function (el, idx) {
      return [[el, y[idx]]];
    }); 
}
/** Name of element to append SVG, names (y-axis), values (x-axis) */
function g_varimp(divid, names, varimp) { 
  // Create a dataset as an array of tuples
  var dataset = zip(names, varimp);
  // Setup size and axis
  var margin = {top: 30, right: 10, bottom: 10, left: 10},
      width = 640 - margin.left - margin.right,
      height = names.length*20 - margin.top - margin.bottom;

  var xScale = d3.scale.linear()
      .range([0, width])
      .domain(d3.extent(varimp)).nice();
        
  var yScale = d3.scale.ordinal()
      .rangeRoundBands([0, height], .2)
      .domain(names);

  var xAxis = d3.svg.axis()
      .scale(xScale)
      .orient("top");

  var svg = d3.select("#"+divid).append("svg")
    .attr("width", width + margin.left + margin.right)
    .attr("height", height + margin.top + margin.bottom)
  .append("g")
    .attr("transform", "translate(" + margin.left + "," + margin.top + ")");

  var tooltip = d3.select("body")
                  .append("div")
                  .attr("id", "d3tip")
                  .classed("hidden", true);

  svg.selectAll(".bar")
      .data(dataset)
    .enter().append("rect")
      .attr("class", function(d) { return d[1] < 0 ? "bar negative" : "bar positive"; })
      .attr("x", function(d) { return xScale(Math.min(0, d[1])); })
      .attr("y", function(d) { return yScale(d[0]); })
      .attr("width", function(d) { return Math.abs(xScale(d[1]) - xScale(0)); })
      .attr("height", yScale.rangeBand())
      .on("mouseover", function (d) {
        var xPosition = width  + document.getElementById(divid).offsetLeft;
        var yPosition = parseFloat(d3.select(this).attr("y")) + yScale.rangeBand() / 2 + document.getElementById(divid).offsetTop;
        tooltip.style("left", xPosition + "px")
                .style("top", yPosition + "px");
        tooltip.html("<p>" + d[0] + "<br/>" + d[1] + "</p>");
        tooltip.classed("hidden", false);
        })
      .on("mouseout", function(d) {
        tooltip.classed("hidden", true);
        });

  svg.append("g")
      .attr("class", "x axis")
      .call(xAxis);

  svg.append("g")
      .attr("class", "y axis")
    .append("line")
      .attr("x1", xScale(0))
      .attr("x2", xScale(0))
      .attr("y2", height);
}

