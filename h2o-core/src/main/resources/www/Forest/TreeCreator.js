var topA = 40;
var right = 120;
var bottom = 20;
var left = 120;
var totalwidth = 1200;
var totalheight = 600;
var file = "treeData.json";
var cNode = '#000000';
var cStroke = '#ff3300';

//updateFunction recieves the user inputs
function updateFunction(){
    xTop = parseInt(document.getElementById("tTop").value, 10);
    xRight =parseInt(document.getElementById("tRight").value, 10);
    xBottom = parseInt(document.getElementById("tBottom").value, 10);
    xLeft = parseInt(document.getElementById("tLeft").value, 10);
    xTotalWidth = parseInt(document.getElementById("tTotalWidth").value, 10);
    xTotalHeight = parseInt(document.getElementById("tTotalHeight").value, 10);
    xC = String(document.getElementById("linkC").value);
    a = createTree(xTop, xRight,xBottom,xLeft,xTotalWidth, xTotalHeight, file, xC);
    a;
}

//this is the sidebar on the page
function sample1(){
    createTree(40,120,20,120,1200,600,"treeData.json", '#00ffff');
}

function sample2(){
    createTree(50, 100,20,100,800, 500, "treeData.json","#FFFFFF");
}

function display1(){
			document.getElementById("display").innerHTML = "<h2>Forest Info</h2> \
                                                                <p>Number of Trees: </p> \
                                                                <p>Model Type: </p> \
                                                                <h2>Tree Info</h2> \
                                                                <p>Tree Alignment: </p> \
                                                                <p>Number of nodes: </p> \
                                                                <p>Depth: </p> \
                                                                <p>Split Count: </p> \
                                                                <p>Number of Nodes: </p> \
                                                                <p>Regression Type: </p> \
                                                                <p>Number of Observations: </p> \
                                                                <p>Average: </p> \
                                                                " + '<p id = "changeName">Tree Name: ' + String(document.getElementById("nameTree").value) + '</p>';
}

function display2(){
	document.getElementById("display").innerHTML = "<h2>Decision Info</h2> \
													<p>Summary of Decision</p> \
													<p>Probability: </p> \
													";
}

function display3(){
 	document.getElementById("display").innerHTML = "<h2>Model Info</h2> \
 													 <p>Type of Algorithm: </p> \
 													 <p>Number of Nodes: </p> \
 													 <p>Size of Tree: </p> \
 													 <p>Depth: </p> \
 													";
 }

function display4(){
 	document.getElementById("display").innerHTML = "<h2>Help</h2> \
 													<p>See example trees</p> \
 													<p>Go to h<sub>2</sub>o website: </p> \
 													<p>Documentation: </p> \
 													<p>Ask?: </p> \
 													";
 }
