//This code uses the d3.js library licensed under the BSD license.

createTree(40,120,20,120,1000,600,"treeData.json");
//createTree(40,120,20,120,1000,600,"tinyTreeData.json");

function createTree(mTop, mRight, mBottom, mLeft, totalWidth,totalHeight,fileName){

	var margin = {top: mTop, right: mRight, bottom: mBottom, left: mLeft},
	width = totalWidth - mRight - mLeft,
	height = totalHeight - mTop - mBottom;

	var addNode = 0;

	var tree = d3.layout.tree().size([width,height]);
	//svg = scalable vector graphics
	var diagonal = d3.svg.diagonal().projection(function(diag){ return [diag.x, diag.y]; });

	var svg = d3.select("body").append("svg")
	.attr("width", width + mRight + mLeft)
	.attr("height", height + mTop + mBottom)
	.append("g")
	.attr("transform", "translate(" + mLeft + "," + mTop + ")");

     d3.json(fileName, function(error, treeData){
            root = treeData[0];
            	update(root);
     });

	function update(source){
		var nodes = tree.nodes(root).reverse(),
		links = tree.links(nodes);

		nodes.forEach(function(d){d.y = d.depth * 100;});

		var node = svg.selectAll("g.node").data(nodes, function(d){ return d.id || (d.id = ++addNode); });

		node.attr("title", function(d){
			return d.name + "hi"
		});

		var nodeEnter = node.enter().append("g")
			.attr("class", "node")
			.attr("transform", function(d) {return "translate(" + d.x + "," + d.y + ")"; });

		nodeEnter.append("title").text(function(d){return "This is a tree"});


        nodeEnter.on("click",function(){
                d3.select(this).select("text").remove();
                 d3.select(this).append("text").text(function(d){return d.name + ": info on the decision"});
        });

        nodeEnter.on("mouseout",function(){
                d3.select(this).select("text").remove();

               d3.select(this).append("text")
			.attr("y", function(d){
				return d.children || d.children ? -20 : 20;})
				.attr("dy", ".35em")
				.attr("text-anchor", "middle")
				.text(function(d){return d.name;});


        });


		nodeEnter.append("circle")
			.attr("r", 10)
			.style("fill", "white");

		nodeEnter.append("text")
			.attr("y", function(d){
				return d.children || d.children ? -20 : 20;})
				.attr("dy", ".35em")
				.attr("text-anchor", "middle")
				.text(function(d){return d.name;});



		var link = svg.selectAll("path.link")
				.data(links, function(d){return d.target.id; });

		link.enter().insert("path", "g")
				.attr("class", "link")
				.attr("d", diagonal);
				//.attr("d",elbow) add to make it square branches


        link.append("text")
        .attr("transform",function(d){
        return "translate(" + ((d.source.y + d.target.y)/2) + "," + ((d.source.x + d.target.x)/2) + ")";

        });


	link.on("mouseover",function(d){
		var color = d3.scale.category20();
		link.style("stroke-width", 3);
		link.attr("d",diagonal).style("stroke",function(d){return color(d.source.depth);});



	link.on("mouseout",function(d){
		d.highlight = false;
		link.style("stroke","black");
		link.style("stroke-width", 1);
	});

        node.on("dbclick",function(d){
			return d.name + "hello"
		   });
		});



	}

}
