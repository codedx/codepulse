;(function(exports){

	exports.TreeData = TreeData

	/*
	Initializes a TreeData structure from the given `rawNodes`. The format of
	`rawNodes` should be an object with entries in the form of {<node.id>: <node>}.
	Each `node` should specify its [optional] parent by having a `parentId` field,
	whose value is the ID of the corresponding node in `rawNodes`.
	*/
	function TreeData(rawNodes, makeSelfNodes){
		var nodes = rawNodes,
			root = initializeNodes(nodes)

		this.__defineGetter__('nodes', function(){ return nodes })
		this.__defineGetter__('root', function(){ return root })

		if(makeSelfNodes) createSelfNodes(this, root)

		this.getNode = function(nodeId){ 
			if(nodeId == 'root') return root
			else return nodes[nodeId] 
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

	/*
	Adds `child` to the `children` array in the `node`.
	Creates `children` if needed.
	Does not act if `node` or `child` are not defined
	*/
	function nodeAddChild(node, child){
		if(!node) return

		if(child){
			var children = node.children || (node.children = [])
			children.push(child)
		}

		return node
	}

	/*
	Initialize the nodes' hierarchy by assigning `parent`
	and `children` properties to each node. Nodes with no
	specified parent become children of an auto-generated
	`root` node, which returned at the end of this method.
	*/
	function initializeNodes(nodes){
		var root = {
			id: 'root',
			name: 'root',
			kind: 'root'
		}

		// for{(id,node) <- nodes} ...
		for(var id in nodes){
			var node = nodes[id]

			// Assign the id to the node.
			node.id = id

			// Assign the parent to the node.
			// Assign the node as a child of its parent.
			// If no parent, use the root.
			var parent = nodes[node.parentId] || root
			node.parent = parent
			nodeAddChild(parent, node)
		}

		return root
	}

	function createSelfNodes(tree, node){
		if(isEligibleForSelfNode(node)){

			var selfNode = {
				id: node.id,
				name: '<self>',
				kind: 'package',
				parent: node,
				children: [],
				traced: node.traced,
				isSelfNode: true
			}
			node.id += '_original'
			if(node.hasOwnProperty('traced')) node.traced = undefined
			selfNode.parentId = node.id

			// update the tree.nodes mapping based on the id changes
			tree.nodes[node.id] = node
			tree.nodes[selfNode.id] = selfNode

			// get ready to reset the node/self children lists
			var originalChildren = node.children || [],
				selfChildren = selfNode.children
			node.children = [selfNode]

			// divvy the node's children between the self node and the original,
			// depending on the child's type.
			originalChildren.forEach(function(child){
				if(child.kind == 'package'){
					node.children.push(child)
					child.parentId = node.id

					// recurse, hopefully finding subpackages
					createSelfNodes(tree, child)
				} else {
					selfNode.children.push(child)
					child.parentId = selfNode.id
				}
			})
			
		} else {
			node.children && node.children.forEach(function(child){
				createSelfNodes(tree, child)
			})
		}
	}

	/*
	A node is eligible for a 'self' node if it is a
	'package' node that has at least one 'package' child
	and one non-package child (class or method).
	*/
	function isEligibleForSelfNode(node){
		if(node.kind != 'package') return false
		if(!node.children) return false

		var hasPackageChild = false,
			hasNonPackageChild = false

		for(var i in node.children){
			var kind = node.children[i].kind

			if(kind == 'package') hasPackageChild = true
			else hasNonPackageChild = true
		}
		return hasPackageChild && hasNonPackageChild
	}

})(this);