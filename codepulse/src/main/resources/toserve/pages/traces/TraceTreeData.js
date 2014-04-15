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
		// treeProjector = undefined,
		// fullTree = undefined,
		packageTree = undefined,
		coverageSets = {},
		treeDataReady = false,
		treeDataReadyCallbacks = []

	// Load the treemap data once the trace's status is no longer 'loading'
	Trace.ready(function(){
		console.log('Trace finished loading. Time to load the treemap data')

		TraceAPI.loadPackageTree(function(pt){
			packageTree = pt

			treeDataReady = true

			packageTree.root.children.forEach(function(topNode){
				console.log('counting methods from ', topNode)
				totalNumMethods += topNode.methodCount
			})

			treeDataReadyCallbacks.forEach(function(callback){
				callback()
			})
			treeDataReadyCallbacks = []
		})

	})

	Trace.__defineGetter__('totalNumMethods', function(){
		return totalNumMethods
	})

	Trace.__defineGetter__('packageTree', function(){
		return packageTree
	})

	Trace.getCoverageSet = function(nodeId){
		return coverageSets[nodeId] || (coverageSets[nodeId] = d3.set())
	}

	Trace.setNodeCoverage = function(nodeId, coverageArray){
		return coverageSets[nodeId] = d3.set(coverageArray)
	}

	Trace.clearCoverageSet = function(nodeId){
		return coverageSets[nodeId] = d3.set()
	}

	Trace.__defineGetter__('treeDataReady', function(){
		return treeDataReady
	})

	Trace.onTreeDataReady = function(callback){
		if(treeDataReady) callback()
		else treeDataReadyCallbacks.push(callback)
	}

})(this.Trace || (this.Trace = {}))