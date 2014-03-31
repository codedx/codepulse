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

;(function(Trace){

	var totalNumMethods = 0,
		treeProjector = undefined,
		fullTree = undefined,
		coverageSets = {},
		treeDataReady = false,
		treeDataReadyCallbacks = []

	TraceAPI.loadTreemap(function(d){

		treeProjector = new TreeProjector(d)

		fullTree = treeProjector.projectFullTree(true /* generate self nodes for packages */)

		fullTree.forEachNode(true, function(n){
			coverageSets[n.id] = d3.set()
			if(n.kind == 'method') totalNumMethods++
		})

		treeDataReady = true

		treeDataReadyCallbacks.forEach(function(callback){
			callback()
		})

	})

	Trace.__defineGetter__('totalNumMethods', function(){
		return totalNumMethods
	})

	Trace.__defineGetter__('treeProjector', function(){
		return treeProjector
	})

	Trace.__defineGetter__('fullTree', function(){
		return fullTree
	})

	Trace.__defineGetter__('coverageSets', function(){
		return coverageSets
	})

	Trace.__defineGetter__('treeDataReady', function(){
		return treeDataReady
	})

	Trace.onTreeDataReady = function(callback){
		if(treeDataReady) callback()
		else treeDataReadyCallbacks.push(callback)
	}

})(this.Trace || (this.Trace = {}))