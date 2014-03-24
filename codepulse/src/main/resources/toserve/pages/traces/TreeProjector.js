/*
 * Code Pulse: A real-time code coverage testing tool. For more information
 * see http://code-pulse.com
 *
 * Copyright (C) 2014 Applied Visions - http://securedecisions.avi.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

;(function(exports){

	exports.TreeProjector = TreeProjector

	function TreeProjector(rawNodes){
		this.rawNodes = rawNodes

		var self = this,
			projectionId = 0

		this.projectFullTree = function(makeSelfNodes){
			return addProjectionId(projectionId++, projectFullTree(self.rawNodes, makeSelfNodes))
		}

		this.projectPackageFilterTree = function(packageFilter, makeSelfNodes){
			return addProjectionId(projectionId++,
				projectPackageFilterTree(self.rawNodes, packageFilter, makeSelfNodes))
		}
	}

	// function addSelfNodes(rawNodes){
	// 	//
	// 	var childrenById = {}
	// 	for(var id in rawNodes){
	// 		var node = rawNodes[id]
	// 		var parentId = node.parentId
	// 		if(parentId != undefined){
	// 			var clist = childrenById[parentId] || (childrenById[parentId] = [])
	// 			clist.push(node)
	// 		}
	// 	}
	// }

	function addProjectionId(id, tree){
		tree.forEachNode(true, function(n){
			n.projectionId = id
		})
		return tree
	}

	// @param rawNodes - The tree data initialization object
	function projectFullTree(rawNodes, makeSelfNodes){
		var copyNodes = $.extend(true, {}, rawNodes)
		return new TreeData(copyNodes, makeSelfNodes)
	}

	// @param rawNodes - The tree data initialization object
	// @param packageFilter - The predicate function that determines if a node is 'selected'
	function projectPackageFilterTree(rawNodes, packageFilter, makeSelfNodes){
		var acceptedNodes = {}

		// use a 2 to signify a 'full' acceptance
		function markFiltered(id){ acceptedNodes[id] = 2 }

		// Mark all of the nodes ancestors as at least partially accepted.
		// Leave any existing acceptance state (e.g. 2) alone.
		function partialAcceptAncestors(node){
			var pid = node.parentId,
				parent = rawNodes[pid]

			if(parent){
				// Use a 1 to signify 'partial' acceptance due to it
				// being the ancestor of a fully-accepted node. If it
				// was already accepted, leave the current value (which
				// may be a 2)
				acceptedNodes[pid] = acceptedNodes[pid] || 1

				partialAcceptAncestors(parent)
			}
		}

		// First Pass through rawNodes - determine which ids are accepted
		for(var id in rawNodes){
			var node = rawNodes[id]

			// add `id` to the node in case the filter wants to use it
			node.id = id

			if(node.kind == 'package' && packageFilter(node)){
				markFiltered(id)
				partialAcceptAncestors(node)
			}
		}

		function nearestPackageId(nodeId){
			var node = rawNodes[nodeId]
			if(!node) return undefined

			if(node.kind == 'package') return nodeId
			return nearestPackageId(node.parentId)
		}

		// Second Pass through rawNodes - make a copy of rawNodes with only accepted nodes
		var nodesCopy = {}
		for(var id in rawNodes){
			var node = rawNodes[id]

			// - Package nodes are accepted if they were added to 
			//   the `acceptedNodes` set during the first step.
			// - Other nodes are accepted if their nearest package
			//   ancestor was 'fully' accepted (marked with a 2)
			if(
				(node.kind == 'package' && acceptedNodes[id]) ||
				(acceptedNodes[nearestPackageId(id)] == 2)
			){
				nodesCopy[id] = $.extend(true, {}, rawNodes[id])
			}
		}

		// Create the tree from the copied nodes
		return new TreeData(nodesCopy, makeSelfNodes)
	}

})(this);