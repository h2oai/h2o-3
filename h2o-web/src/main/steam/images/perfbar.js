/**
 * Created by tomk on 9/27/14.
 */

(function() {
var PB_LINEOFTEXT_BACKGROUND_COLOR = "#fff";
var PB_PIXEL_WIDTH_BAR = 6;
var PB_PIXEL_WIDTH_BAR_SPACING = 1;
var PB_PIXEL_WIDTH_CANVAS_SPACING = 10;
var PB_PIXEL_HEIGHT = 100;
var PB_PIXEL_HEIGHT_SPACING = 3;
var PB_PIXEL_HEIGHT_LINEOFTEXT = 10;
var perfbars = null;
var timeouts = null;
var timeoutDelayMillis = 200;
var cloud_size = -1;
var acknowledged_cloud_size = 0;
var shutdownRequested = false;

function saturate0(value) {
if (value < 0) {
    return 0;
}

return value;
}

function appendBody(str) {
    var el = document.getElementById("perfbarContainer");

    var h = el.innerHTML;
    h = h + str;
    el.innerHTML = h;
    // console.log("document.body.innerHTML: " + h);
}

function PerfbarCore(coreIdx) {
    this.pbFirstUpdate = true;
    this.pbCoreIdx = coreIdx;
    this.pbUserTicks = 0;
    this.pbSystemTicks = 0;
    this.pbOtherTicks = 0;
    this.pbIdleTicks = 0;
    this.pbUserHeight = 0;
    this.pbSystemHeight = 0;
    this.pbOtherHeight = 0;
    this.pbX = this.pbCoreIdx * (PB_PIXEL_WIDTH_BAR + PB_PIXEL_WIDTH_BAR_SPACING);

    this.fill = function (ctx, fillStyle, x, y, x_width, y_height) {
        // console.log("fillRect:", fillStyle, x, y, x_width, y_height);
        ctx.fillStyle = fillStyle;
        ctx.fillRect(x, y, x_width, y_height);
    };

    this.initializeTicks = function (userTicks, systemTicks, otherTicks, idleTicks) {
        this.pbUserTicks = userTicks;
        this.pbSystemTicks = systemTicks;
        this.pbOtherTicks = otherTicks;
        this.pbIdleTicks = idleTicks;
    };

    this.updateTicks = function (ctx, userTicks, systemTicks, otherTicks, idleTicks) {
        // console.log("updateTics:", this.pbCoreIdx);

        var deltaUserTicks   = saturate0(userTicks - this.pbUserTicks);
        var deltaSystemTicks = saturate0(systemTicks - this.pbSystemTicks);
        var deltaOtherTicks  = saturate0(otherTicks - this.pbOtherTicks);
        var deltaIdleTicks   = saturate0(idleTicks - this.pbIdleTicks);
        var deltaTotalTicks  = deltaUserTicks + deltaSystemTicks + deltaOtherTicks + deltaIdleTicks;

        var userTicksPct;
        var systemTicksPct;
        var otherTicksPct;
        if (deltaTotalTicks > 0) {
            userTicksPct     = (deltaUserTicks / deltaTotalTicks);
            systemTicksPct   = (deltaSystemTicks / deltaTotalTicks);
            otherTicksPct    = (deltaOtherTicks / deltaTotalTicks);
        }
        else {
            userTicksPct     = 0;
            systemTicksPct   = 0;
            otherTicksPct    = 0;
        }

        var perfectUserHeight     = PB_PIXEL_HEIGHT * userTicksPct;
        var perfectSystemHeight   = PB_PIXEL_HEIGHT * systemTicksPct;
        var perfectOtherHeight    = PB_PIXEL_HEIGHT * otherTicksPct;

        // Blend previous and current height values to get some optical smoothing for the eye.
        // Take three parts old data and two parts new data.
        var blendedUserHeight;
        var blendedSystemHeight;
        var blendedOtherHeight;
        if (this.pbFirstUpdate) {
            // Don't blend, since there is nothing to smooth out.
            blendedUserHeight     = perfectUserHeight;
            blendedSystemHeight   = perfectSystemHeight;
            blendedOtherHeight    = perfectOtherHeight;
        }
        else {
            blendedUserHeight     = (this.pbUserHeight   + perfectUserHeight)   / 2;
            blendedSystemHeight   = (this.pbSystemHeight + perfectSystemHeight) / 2;
            blendedOtherHeight    = (this.pbOtherHeight  + perfectOtherHeight)  / 2;
        }
        var blendedIdleHeight     = saturate0(PB_PIXEL_HEIGHT - (blendedUserHeight + blendedSystemHeight + blendedOtherHeight));

        var x = this.pbX;
        var x_width = PB_PIXEL_WIDTH_BAR;
        var y;
        var y_height;

        // user (green)
        y = blendedIdleHeight + blendedOtherHeight + blendedSystemHeight;
        y_height = blendedUserHeight;
        this.fill(ctx, "#00FF00", x, y, x_width, y_height);

        // system (red)
        y = blendedIdleHeight + blendedOtherHeight;
        y_height = blendedSystemHeight;
        this.fill(ctx, "#FF0000", x, y, x_width, y_height);

        // other (white)
        y = blendedIdleHeight;
        y_height = blendedOtherHeight;
        this.fill(ctx, "#DDDDDD", x, y, x_width, y_height);

        // idle (blue)
        y = 0;
        y_height = blendedIdleHeight;
        this.fill(ctx, "#0000FF", x, y, x_width, y_height);

        this.pbUserTicks = userTicks;
        this.pbSystemTicks = systemTicks;
        this.pbOtherTicks = otherTicks;
        this.pbIdleTicks = idleTicks;

        this.pbUserHeight = blendedUserHeight;
        this.pbSystemHeight = blendedSystemHeight;
        this.pbOtherHeight = blendedOtherHeight;

        this.pbFirstUpdate = false;
    };
}

function Perfbar(nodeIdx, nodeName, nodePort, numCores) {
    this.pbFirstUpdate = true;
    this.pbCtx = null;
    this.pbWidth = -1;
    this.pbNodeIdx = nodeIdx;
    this.pbNodeName = nodeName;
    this.pbNodePort = nodePort;
    this.pbNumCores = numCores;
    this.pbCores = new Array(numCores);
    this.pbNodeId = "node" + nodeIdx;

    console.log("instantiating:", this.pbNodeIdx, this.pbNodeName, this.pbNodePort);

    for (var i = 0; i < numCores; i++) {
        this.pbCores[i] = new PerfbarCore (i);
    }

    this.docWriteCanvas = function() {
        var width;
        if (numCores == 0) {
            // Enough room for a "Not Linux" message.
            width = (8 * PB_PIXEL_WIDTH_BAR) + ((numCores - 1) * PB_PIXEL_WIDTH_BAR_SPACING) + PB_PIXEL_WIDTH_CANVAS_SPACING;
        }
        else {
            width = (numCores * PB_PIXEL_WIDTH_BAR) + ((numCores - 1) * PB_PIXEL_WIDTH_BAR_SPACING) + PB_PIXEL_WIDTH_CANVAS_SPACING;
        }

        // Minimum width for the chart labels to print properly.
        var min_width = 40;
        if (width < min_width) {
            width = min_width;
        }

        this.pbWidth = width;

        var height = (PB_PIXEL_HEIGHT + 2*PB_PIXEL_HEIGHT_SPACING + 2*PB_PIXEL_HEIGHT_LINEOFTEXT);

        var s = '' +
            '<can' + 'vas' +
            ' id="'     + this.pbNodeId + '"' +
            ' width="'  + width    + '"' +
            ' height="' + height   + '"' +
            '>' +
            ' </can' + 'vas>' +
            '\n';
            console.log("docWriteCanvas: ", s);
        appendBody(s);
    };

    this.docGetElement = function() {
        // console.log("docGetElement called");
        var c = document.getElementById(this.pbNodeId);
        this.pbCtx = c.getContext("2d");
        this.pbCtx.fontStyle = "10px sans-serif";
    };

    this.initializeTicks = function(cpuTicks) {
        if (cpuTicks.length != this.pbNumCores) {
            // This is really bad.
            console.log("ERROR: ", cpuTicks.length, "!=", this.pbNumCores);
        }

        for (var i = 0; i < this.pbNumCores; i++) {
            this.pbCores[i].initializeTicks(cpuTicks[i][0], cpuTicks[i][1], cpuTicks[i][2], cpuTicks[i][3]);
        }
    };

    this.updateTicks = function(cpuTicks) {
        if (this.pbFirstUpdate) {
            // Print warning notification if we're not running on Linux.
            console.log("calling fillText");
            this.pbCtx.fillStyle = "black";
            if (cpuTicks.length == 0) {
                this.pbCtx.fillText(
                    "[Not Linux]",
                    0,
                    PB_PIXEL_HEIGHT_LINEOFTEXT);
            }

            // Fill in background of node name.
            this.pbCtx.fillStyle = PB_LINEOFTEXT_BACKGROUND_COLOR;
            this.pbCtx.fillRect(
                0,
                PB_PIXEL_HEIGHT,
                this.pbWidth,
                2*PB_PIXEL_HEIGHT_SPACING + 2*PB_PIXEL_HEIGHT_LINEOFTEXT);

            // Fill in background of side-to-side canvas spacing.
            this.pbCtx.fillRect(
                this.pbWidth - PB_PIXEL_WIDTH_CANVAS_SPACING,
                0,
                PB_PIXEL_WIDTH_CANVAS_SPACING,
                PB_PIXEL_HEIGHT + 2*PB_PIXEL_HEIGHT_SPACING + 2*PB_PIXEL_HEIGHT_LINEOFTEXT);

            // Print node name.
            this.pbCtx.fillStyle = "black";
            this.pbCtx.fillText(
                this.pbNodeName,
                0,
                PB_PIXEL_HEIGHT + PB_PIXEL_HEIGHT_SPACING + PB_PIXEL_HEIGHT_LINEOFTEXT);

            // Print node port.
            this.pbCtx.fillStyle = "black";
            this.pbCtx.fillText(
                this.pbNodePort,
                0,
                PB_PIXEL_HEIGHT + 2*PB_PIXEL_HEIGHT_SPACING + 2*PB_PIXEL_HEIGHT_LINEOFTEXT);

            this.pbFirstUpdate = false;
        }

        if (cpuTicks.length != this.pbNumCores) {
            // This is really bad.
            console.log("ERROR: ", cpuTicks.length, "!=", this.pbNumCores);
        }

        for (var i = 0; i < this.pbNumCores; i++) {
            this.pbCores[i].updateTicks(this.pbCtx, cpuTicks[i][0], cpuTicks[i][1], cpuTicks[i][2], cpuTicks[i][3]);
        }
    };
}

function initializeCloud() {
    shutdownRequested = false;

    var xmlhttp = new XMLHttpRequest();
    xmlhttp.onreadystatechange = function() {
        if (shutdownRequested) {
            return
        }

        if (xmlhttp.readyState==4 && xmlhttp.status==200) {
            console.log(xmlhttp.responseText);
            var obj = JSON.parse(xmlhttp.responseText);
            cloud_size = obj.cloud_size;
            console.log("cloud size is " + cloud_size);
            perfbars = new Array(cloud_size);
            var nodes = obj.nodes;
            for (var i = 0; i < cloud_size; i = i + 1) {
                var node = nodes[i];
                var fullNodeName = node.h2o.node;
                var arr = fullNodeName.split(".");
                var lastOctetAndPort = arr[3];
                var arr2 = lastOctetAndPort.split(":");
                var nodeName = arr2[0];
                var nodePort = arr2[1];
                initializeNode(i, nodeName, nodePort);
            }
            timeouts = new Array(cloud_size);
        }
    };
    xmlhttp.open("GET","/2/Cloud.json",true);
    xmlhttp.send();
}

function initializeNode(nodeIdx, nodeName, nodePort) {
    var xmlhttp = new XMLHttpRequest();
    xmlhttp.onreadystatechange = function() {
        if (shutdownRequested) {
            return
        }

        if (xmlhttp.readyState==4 && xmlhttp.status==200) {
            console.log("initializeNode ", nodeIdx, nodeName, "response: ", xmlhttp.responseText);
            var obj = JSON.parse(xmlhttp.responseText);
            var cpuTicks = obj.cpu_ticks;
            console.log("ticks array has length " + cpuTicks.length);

            var numCpus = cpuTicks.length;
            var pb = new Perfbar(nodeIdx, nodeName, nodePort, numCpus);
            perfbars[nodeIdx] = pb;
            pb.initializeTicks(cpuTicks);

            // Only emit the doc elements once all nodes have checked in.
            // This way they are laid out synchronously and in order.
            acknowledged_cloud_size = acknowledged_cloud_size + 1;
            if (acknowledged_cloud_size == cloud_size) {
                var i;
                var pb2;

                // Append HTML elements to body.
                for (i = 0; i < cloud_size; i++) {
                    pb2 = perfbars[i];
                    pb2.docWriteCanvas();
                }

                var legend = "" +
                    "<div style='max-width: 300px;'>" +
                    "<br/>" +
                    "<h3>Legend</h3>" +
                    "<p>Each bar represents one CPU.</p>" +
                    "<table class='table'>" +
                    "  <tr><td>Blue:</td><td>idle time</td></tr>" +
                    "  <tr><td>Green:</td><td>user time</td></tr>" +
                    "  <tr><td>Red:</td><td>system time</td></tr>" +
                    "  <tr><td>White:</td><td>other time (e.g. i/o)</td></tr>" +
                    "</table>" +
                    "</d" + "iv>";
                appendBody(legend);

                // Get all 2d contexts for canvas elements.
                for (i = 0; i < cloud_size; i++) {
                    pb2 = perfbars[i];
                    pb2.docGetElement();
                }

                // Arm next update.
                for (i = 0; i < cloud_size; i++) {
                    timeouts[i] = setTimeout(function(idx) {
                        repaintAndArmTimeout(idx);
                    }, timeoutDelayMillis, i);
                }
            }
        }
    };
    xmlhttp.open("GET","/1/WaterMeterCpuTicks.json/" + nodeIdx, true);
    xmlhttp.send();
}

function repaintAndArmTimeout(nodeIdx) {
    var xmlhttp = new XMLHttpRequest();
    xmlhttp.onreadystatechange = function () {
        if (shutdownRequested) {
            return
        }

        if (xmlhttp.readyState == 4 && xmlhttp.status == 200) {
            console.log("repaintAndArmTimeout", nodeIdx, "response: ", xmlhttp.responseText);
            var obj = JSON.parse(xmlhttp.responseText);
            var cpuTicks = obj.cpu_ticks;
            perfbars[nodeIdx].updateTicks(cpuTicks);

            if (cpuTicks.length == 0) {
                // Don't need to keep repeating the query if there aren't any ticks to be found.
                // Windows, Mac, other non-Linux OS.
                return
            }

            // Fetch next tick update for this node.
            timeouts[nodeIdx] = setTimeout(function(idx) {
                repaintAndArmTimeout(idx);
            }, timeoutDelayMillis, nodeIdx);
        }
    };
    xmlhttp.open("GET", "/1/WaterMeterCpuTicks.json/" + nodeIdx, true);
    xmlhttp.send();
}

window.createPerfbar = initializeCloud;

window.disposePerfbar = function() {
    console.log("disposePerfbar called");
    shutdownRequested = true;
}
})
();
