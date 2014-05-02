;(function(exports){

	exports.TreeData = TreeData

	/*
	Initializes a TreeData structure from the given `rawTrees`. The format of
	`rawTrees` should be an array containing tree nodes in the form of 
	{id: <id>, label: <label>, children: [<nodes>]}.

	This constructor groups all of the rawTrees as children of a common root,
	assembles a map of [node.id -> node], and assigns parent references.
	*/
	function TreeData(rawTrees){
		var nodes = {}

		var root = {
			id: 'root',
			label: 'root',
			kind: 'root',
			children: rawTrees
		}

		//
		;(function initializeNode(node, parent){
			node.parent = parent

			// if the node was a "self node", add its parent
			// and parent id to the nodes map.
			if(node.label == '<self>'){
				node.isSelfNode = true
				parent.id = node.id + '_original'
				nodes[parent.id] = parent
			}

			// add the node to the nodes map, by its ID if it has one
			if(node.id != undefined) nodes[node.id] = node

			// ensure the node has a children array, even if it's empty
			node.children = node.children || []

			// recurse into each child
			node.children.forEach(function(child){
				initializeNode(child, node)
			})
		})(root, undefined)

		this.__defineGetter__('nodes', function(){ return nodes })
		this.__defineGetter__('root', function(){ return root })
		this.getNode = function(nodeId){
			return nodes[nodeId]
		}

		// Usage:
		//  treeData.forEachNode(includeRoot, f)
		//  treeData.forEachNode(f)
		this.forEachNode = function(){
			var includeRoot = false, f = undefined

			if(arguments.length == 2){
				includeRoot = arguments[0]
				f = arguments[1]
			} else if(arguments.length == 1){
				f = arguments[0]
			} else {
				throw 'invalid arguments'
			}

			if(includeRoot) f(root)
			for(var id in nodes){ f(nodes[id]) }
		}
	}

})(this);