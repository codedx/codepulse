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

function logTime(label, func) {
	var tStart = Date.now(),
		result = func(),
		tEnd = Date.now()
	console.log(label, 'took ', (tEnd-tStart), ' ms')
	return result
}

$(document).ready(function(){
	
	var treemapContainer = $('#treemap-container'),

		// initialize a treemap widget
		treemapWidget = new CodebaseTreemap('#treemap-container .widget-body').nodeSizing('line-count'),
		
		// REST endpoint that generates code coverage data for nodes in the treemap
		treemapDataUrl = document.location.href + '/coverage-treemap.json',

		/**
		 * Object that contains values about the trace. Used to share data
		 * between this script and the trace recording controls.
		 */
		traceInfo = new TraceInfo(),

		/**
		 * Map[node.id -> Set(recording ids that traced the node)]
		 * This map gets updated while a trace is running.
		 */
		coverageSets = {},

		/**
		 * Controller for the trace controls on the side of the page. Exposes events for various
		 * data updates that happen during a running trace.
		 */
		traceControls = new TraceRecordingControlsWidget(traceInfo),

		/*
		 * Create a large spinner (provided by spin.js) that appears in place
		 * of the treemap, before the treemap has loaded. Activate it before
		 * requesting the treemap data from the server.
		 */
		treemapSpinner = (function(){
			var spinner = new Spinner({lines: 17, length: 33, width: 10, radius: 24}),
				target = document.getElementById('treemap-container')

			spinner.spin(target)
			return spinner
		})()

	traceControls.legendData.onValue(function(legendData){
		console.log('legendData changed:', legendData)
		var coloringFunc = treemapColoring(legendData)
		treemapWidget.nodeColoring(coloringFunc)
	})

	/*
	 * Creates a coloring function that can be used by the treemapWidget,
	 * based on the given `legendData`. Calling this function with no argument
	 * will return the result of the last call to this function (or if it wasn't)
	 * called previously, it will return as if `legendData = {}`).
	 *
	 * Colors are chosen based on the `coverageSet` of each node (stored in `coverageSets`).
	 * If a node was covered by one 'recording', it uses that color. Special cases include
	 * package and root nodes, which are hardwired to grey, and nodes that were covered by
	 * multiple recordings, which are hardwired to purple.
	 */
	function treemapColoring(legendData){
		if(!arguments.length){
			var latest = treemapColoring.latest
			if(latest) return latest
			else legendData = {}
		}
		var ignoredKinds = d3.set(['package', 'root'])

		var coloringFunc = function(allNodes){

			return function(node){
				if(ignoredKinds.has(node.kind)) return 'grey'
				var coverage = coverageSets[node.id],
					numCovered = coverage ? coverage.size() : 0

				if(numCovered == 0) return 'lightgrey'
				if(numCovered > 1) return legendData['multi'].color

				var entryId = undefined
				coverage.forEach(function(id){ entryId = id})
				var entry = legendData[entryId]

				return entry? entry.color : 'yellow'
			}
		}

		treemapColoring.latest = coloringFunc
		return coloringFunc
	}

	// Create a boolean Property that tells whether the treemap 'drawer' is currently visible
	// var showTreemapClicks = $('#show-treemap-button').asEventStream('click').map(true),
	// 	hideTreemapClicks = $('#hide-treemap-button').asEventStream('click').map(false),
	// 	showTreemap = showTreemapClicks.merge(hideTreemapClicks)

	var showTreemap = $('#show-treemap-button').asEventStream('click').scan(false, function(b){ return !b })

	// update some container classes when the treemap drawer goes in and out of view
	showTreemap.onValue(function(show){
		$('#show-treemap-button').toggleClass('expanded', show)
		$('#treemap').toggleClass('in-view', show)
	})

	/*
	 * Request the treemap data from the server. Note that coverage lists
	 * are only specified for the most specific element; for the sake of 
	 * the UI, we "bubble up" the coverage data, so that a parent node is
	 * covered by any trace/segment/weakness that one of its children is
	 * covered by.
	 * 
	 * Once the data has loaded, stop the treemap spinner (mentioned above)
	 * and apply the data to the treemap widget.
	 */
	//d3.json(treemapDataUrl, function(d){
	TraceAPI.loadTreemap(function(d){
		
		var treeProjector = new TreeProjector(d),
			fullTree = treeProjector.projectFullTree(true /* generate self nodes for packages */),
			numMethods = 0

		// Assign a Set to each node to indicate what its 'trace coverage' is.
		// Also count the number of 'method' nodes in the tree, for the `traceInfo`
		fullTree.forEachNode(true, function(n){
			coverageSets[n.id] = d3.set()
			if(n.kind == 'method') numMethods++
		})
		traceInfo.totalNumMethods = numMethods

		var controller = new PackageController(fullTree, $('#packages'), $('#totals'), $('#clear-selections-button'))

		/**
		 * An Rx Property that represents the treemap's TreeData as it
		 * changes due to the PackageWidgets' selection state. 
		 *
		 * When nothing is selected, it uses the full tree; otherwise it 
		 * creates a filtered projection based on the selected packages.
		 */
		var treemapData = controller.selectedWidgets.map(function(sel){
			var hasSelection = (function(){
				for(var k in sel) if(sel[k]) return true
				return false
			})()

			treemapContainer.toggleClass('no-selection', !hasSelection)

			if(hasSelection) {
				return treeProjector.projectPackageFilterTree(function(n){ return sel[n.id] })
			} else {
				return treeProjector.projectEmptyTree()
			}
		})

		// Match the 'compactMode' with the 'showTreemap' state.
		showTreemap.onValue(function(show){
			controller.compactMode(show)
		})

		// Highlight the package widgets when trace data comes in
		traceControls.liveTraceData.onValue(controller.highlightPackages)

		// Update method coverage counts when the data is changed
		traceControls.coverageRecords.onValue(controller.applyMethodCoverage)

		treemapSpinner.stop()
		
		// Set the coloring function and give the treemap data
		treemapWidget
			.nodeColoring(treemapColoring())

		treemapData.onValue(function(tree){
			treemapWidget.data(tree).update()
		})

		traceControls.coverageRecords.onValue(setTreemapCoverage)
		traceControls.liveTraceData.onValue(treemapWidget.highlightNodesById)

		function setTreemapCoverage(recordData){
			function recurse(node){
				var s = coverageSets[node.id] = d3.set(),
					rd = recordData[node.id],
					kids = node.children
				if(rd && rd.length) rd.forEach(function(c){ s.add(c) })
				kids && kids.forEach(function(kid){
					recurse(kid)
					coverageSets[kid.id].forEach(function(c){ s.add(c) })
				})
			}
			recurse(fullTree.root)
			treemapWidget.nodeColoring(treemapColoring())
		}

		// Property that starts as `false`, but becomes `true` once 
		// the [dismiss] button is clicked in the performance warning
		var slowWarningDismissed = $('#performance-warning a')
			.asEventStream('click')
			.map(function(){ return true })
			.toProperty(false)

		// Add the 'in-view' class to the performance warning if
		// the treemap is running slowly and the alert hasn't been dismissed.
		treemapWidget.isRunningSlowly
			.and(slowWarningDismissed.not())
			.onValue(function(showWarning){
				$('#performance-warning').toggleClass('in-view', showWarning)
			})
	})

	var treemapTooltipContentProvider = {
		/*
		 * Use the node's name as the title in all cases.
		 */
		'calculateTitle': function(node){
			return node.name
		},

		/*
		 * Generates an html tree containing 'name' elements for the node, and all of its
		 * parents up to the nearest package. The <div>s are nested so that each subsequent
		 * level gets an 'indent' class. Each 'name' <div> also gets a '<type>-node' CSS class
		 * for coloring purposes. Package nodes are special-cased to have an empty content;
		 * they are represented only as a title.
		 */
		'calculateContent': (function(){
			var latestLegendData = {}
			traceControls.legendData.onValue(function(ld){ latestLegendData = ld })

			return function(node){
				if(node.kind == 'package'){
					return $('<div>')
				} else {

					// wrap the whole result in a <div class='content-padded'>
					var padDiv = $('<div>').addClass('content-padded')

					// Check if the node was encountered by any recordings;
					// if so, create an indication of which ones
					var recordings = coverageSets[node.id].values()
						.map(function(recId){ return latestLegendData[recId] })
						.filter(function(d){ return d })

					if(recordings.length){
						var container = $('<div>')
							.text('Traced by ')
							// .addClass('clearfix')
							.addClass('coverage-area')
						
						recordings.forEach(function(ld){ 
							var bg = ld.color,
								lightness = d3.hsl(bg).l,
								textColor = (lightness > 0.4) ? 'black' : 'white'

							$('<div>')
								.addClass('coverage-label')
								.text(ld.label)
								.css('background-color', bg)
								.css('color', textColor)
								.appendTo(container)
						})

						padDiv.append(container)
					}

					// calculate path as [node.firstAncestor, ... node.grandparent, node.parent, node]
					// stop traversing upwards once a 'package' node has been reached
					var path = [], i = node
					while(i){
						path.unshift(i)
						if(i.kind == 'package') i = null
						else i = i.parent
					}

					function recurse(i){
						if(i > path.length) return

						var container = $('<div>')

						// everything but the top node gets an indent
						if(i > 0) container.addClass('indent')

						// generate <div class='name'> for the node
						var label = $('<div>')
							.addClass('name')
							.addClass(path[i].kind + '-node')
							.text(path[i].name)
							.appendTo(container)

						// if there are more path elements
						// add the next one to the container
						if(i + 1 < path.length){
							recurse(i+1).appendTo(container)
						}

						return container
					}

					return padDiv.append(recurse(0))
				}
			}
		})()
	}

	treemapWidget.tooltipContentProvider(treemapTooltipContentProvider)

	// Allow the header title to be edited.
	// When it changes, send the change to the server to check for name conflicts
	$('h1.editable').editable().on('edited', function(e, newName){
		TraceAPI.renameTrace(newName, function(reply, error){
			if(!error){
				var hasNameConflict = (reply.warn == 'nameConflict')
				$('.nameConflict').toggleClass('hasConflict', hasNameConflict)
			}
		})
	})
})