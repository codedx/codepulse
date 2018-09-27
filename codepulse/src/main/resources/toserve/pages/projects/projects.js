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
		packagesContainer = $('#packages'),

		// initialize a treemap widget
		treemapWidget = new CodebaseTreemap('#treemap-container .widget-body').nodeSizing('line-count'),

		// initialize dependency check controller
		depCheckController = new DependencyCheckController($('#dependency-check-report')),

		surfaceDetectorController = new SurfaceDetectorController(),

		controller = null;

	let isAttackSurfaceOn = false

	// Set a UI state for the 'loading' and 'deleted' states.
	;(function(){

		// Cover the page in a 'loading' overlay while the project's
		// data is being analyzed (aka 'loading').
		Trace.isLoadingProp.onValue(function(loading){
			$('body').overlay(loading ? 'wait' : 'ready')
		})

		// Show a loading indicator over the Application Inventory between the
		// time that the main overlay ends and the time that the treemap data
		// becomes available.
		Trace.isLoadingProp.not().and(Trace.treeDataReadyProp.not())
			.skipDuplicates()
			.onValue(function(waiting){
				packagesContainer.overlay(waiting ? 'wait' : 'ready')
			})

		Trace.status.onValue(function(status){
			if(status.name == 'loading-failed'){
				let reason = "Unknown reason."
				if(status.information) {
					reason = status.information
				}
				alert('Processing data failed. Reason:\n' + reason + '\n\nYou will be redirected to the home screen.')
				window.location.href = '/'
			} else if(status.name == 'delete-pending'){
				// just redirect. There will be a notification waiting on the home page
				window.location.href = '/'
			} else {
				console.log("Project received unhandled trace status (this is not necessarily an error)", status)
			}
		})
	})()

    // Source view setup
    ;(function(){
        let $container = $('#treemap-container .widget-body')

        // grab a reference to the popout element
        let $popout = $('#source-popout')
        let $popoutHeader = $popout.find('header')
        let $popoutBody = $popout.find('article')
        let sourceView = new SourceView($popoutBody.find('.codemirror-parent')[0])
        window.sourceView = sourceView

        // monitor clicks on treemap nodes (the 'rect' selector)
        let treemapNodeClicks = $container.asEventStream('click', 'rect', function(e){
            return {
                elem: e.target,
                data: d3.select(e.target).datum()
            }
        }).log('tree node click')

        let popoutCloseClicks = $popout.asEventStream('click', '.close')

        // Manage the 'resetting' and 'active' classes to make a nice zoom-in transition from the clicked node
        function activatePopout(fromElem, callback){
            var containerOffset = $container.offset()
            var elemBounds = fromElem.getBoundingClientRect()

            var transitionStartBounds = {
                top: (elemBounds.top - containerOffset.top) + 'px',
                left: (elemBounds.left - containerOffset.left) + 'px',
                height: elemBounds.height + 'px',
                width: elemBounds.width + 'px'
            }

            // set the intro position
            $popout.addClass('resetting').css(transitionStartBounds)

            // wait for that to take effect, then transition to the 'active' state
            requestAnimationFrame(function(){
                $popout.removeClass('resetting').addClass('active').css({
                    // turn off the specific positioning so the stylesheet can kick in
                    top: '',
                    left: '',
                    height: '',
                    width: ''
                })

                // the css transition will take .3 seconds, then call the callback
                setTimeout(callback, 300)
            })
        }

        let liveDataSubscription = null
        let coverageUpdateSubscription = null
        let traceCoverageUpdateRequestsSubscription = null
        let toggleSurfaceMethodSubscription = null

        function deactivatePopout() {
        	if(controller) controller.enabled(true)
            $popoutHeader.empty()
            sourceView.setSourceView(null, '')
            $popout.removeClass('active')

            if (liveDataSubscription != null) {
                liveDataSubscription(); liveDataSubscription = null
            }
            if (coverageUpdateSubscription != null) {
                coverageUpdateSubscription(); coverageUpdateSubscription = null
            }
            if (traceCoverageUpdateRequestsSubscription != null) {
                traceCoverageUpdateRequestsSubscription(); traceCoverageUpdateRequestsSubscription = null
            }
            if (toggleSurfaceMethodSubscription != null) {
                toggleSurfaceMethodSubscription(); toggleSurfaceMethodSubscription = null
            }
        }

        let popoutHeaderTemplate = Handlebars.compile($('#source-popout-header-template').html())

        function bindSourceViewTemplate(selection) {
            var templateModel = _.extend({},selection.metadata, selection.nodeSourceInfo)
            var templateHtml = popoutHeaderTemplate(templateModel);
            $popoutHeader.html(templateHtml);
        }

        function updateTracedSourceLocationCount(selection, currentCount) {
            selection.nodeSourceInfo.tracedSourceLocationPercentage = "";
            selection.nodeSourceInfo.tracedSourceLocationCount = currentCount
            if (selection.nodeSourceInfo.sourceLocationCount != 0) {
                var percentage = selection.nodeSourceInfo.tracedSourceLocationCount / selection.nodeSourceInfo.sourceLocationCount * 100
                selection.nodeSourceInfo.tracedSourceLocationPercentage = "(" + (percentage % 1 === 0 ? percentage : percentage.toFixed(2)) + "%)";
            }
        }

        function setSurfaceIcon(isSurfaceMethod) {
            let $icon = $('#is-surface-method-button');
            $icon.toggleClass('im-surface-on', isSurfaceMethod);
            $icon.toggleClass('im-surface-off', !isSurfaceMethod);
            $icon.attr('title', isSurfaceMethod ? 'Remove application surface mark' : 'Mark as application surface method');
            $icon.attr('data-node-is-surface-method', isSurfaceMethod);
        }

        function toggleSurfaceMethod(event) {
            let nodeId = event.target.getAttribute('data-node-id');
            if (event.target.getAttribute('data-node-is-surface-method') === "true") {
                API.removeFromSurface(nodeId, function() { setSurfaceIcon(false); });
            } else {
                API.addToSurface(nodeId, function() { setSurfaceIcon(true); });
            }
        }

        function updateSourceLocations(coverage, activeRecordings, selection, sourceLocationDataNew) {

            if (sourceLocationDataNew != null) {
                selection.sourceLocationData = sourceLocationDataNew
            }

            if (selection.sourceLocationData == null) {
                return
            }

            let sourceLocations = selection.sourceLocationData.sourceLocations
            let sourceLocationCoverageData = selection.sourceLocationData.sourceLocationCoverage

            // determine whether the method with node ID selection.metadata.nodeId is filtered from the display
            let nodeFiltered = true;
            let coverageData = coverage[selection.metadata.nodeId]
            if (coverageData != null) {
                nodeFiltered = !coverageData.includes("0")
                if (nodeFiltered && activeRecordings.size() > 0) {
                    for (let i=0; i < coverageData.length && nodeFiltered; i++) {
                        nodeFiltered = !activeRecordings.has(coverageData[i])
                    }
                }
            }

            // add/remove highlighting for each source location based on current filtering data
            let visibleLocationCount = 0
            for (let i=0; i < sourceLocations.length; i++) {
                let sourceLocation = sourceLocations[i]
                if (nodeFiltered) {
                    sourceView.setSourceLocations([sourceLocation], false)
                    continue
                }

                let filterSourceLocation = true
                let sourceLocationCoverage = sourceLocationCoverageData[sourceLocation.id]
                if (sourceLocationCoverage != null) {
                    filterSourceLocation = !sourceLocationCoverage.includes("0")
                    if (filterSourceLocation && activeRecordings.size() > 0) {
                        for (let i=0; i < sourceLocationCoverage.length && filterSourceLocation; i++) {
                            filterSourceLocation = !activeRecordings.has(sourceLocationCoverage[i])
                        }
                    }
                }

                let isVisible = !filterSourceLocation
                if (isVisible) {
                    visibleLocationCount++
                }
                sourceView.setSourceLocations([sourceLocation], isVisible)
            }

            updateTracedSourceLocationCount(selection, visibleLocationCount)
            bindSourceViewTemplate(selection)
        }

        function showSourceViewError(err, selection) {
            if(typeof err == 'string') sourceView.setErrorView(err)
            else if(err.message) sourceView.setErrorView(err.message)
            else sourceView.setErrorView(err)

            bindSourceViewTemplate(selection)
        }

        // activate the popout from elements that are clicked in the treemap
        treemapNodeClicks
            .filter(function(click){
                var t = click.data.kind
                return t == 'method' || t == 'class'
            })
            .flatMapLatest(function(click){
                return Bacon.fromCallback(API.getNodeSourceMetadata, click.data.id).map(function(response){
                    return _.extend({},
						{ element: click.elem },
						{ metadata: response },
						{ dataProvider: new SourceDataProvider(response) },
						{ nodeSourceInfo: { sourceLocationCount: response.sourceLocationCount, tracedSourceLocationCount: 0, tracedSourceLocationPercentage: "" } })
                }).mapError(function(err){
                    console.log('treemap click event could not be associated with source due to lookup error', err)
                    return null
                })
            })
            .filter(_.identity) // stop the errors from getting this far
            .flatMapFirst(function(selection){ return Bacon.fromCallback(activatePopout, selection.element).map(selection) })
            .onValue(function(selection) {
				selection.dataProvider.loadSource().then(({ mode, source }) => {
					selection.refresh = true

					sourceView.setSourceView(mode, source)
					sourceView.scrollToLine(selection.metadata.methodStartLine)
					if(controller) controller.enabled(false)

					coverageUpdateSubscription = Bacon.onValues(Trace.coverageRecords, Trace.activeRecordingsProp, function(coverage, activeRecordings) {
						if (selection.refresh) {
							API.getNodeSourceMetadata(selection.metadata.nodeId, function(data) {
								selection.nodeSourceInfo.sourceLocationCount = data.sourceLocationCount
								selection.dataProvider.loadSourceLocations(Trace.getRecordsReqParams(), true).then((sourceLocationData) => {
									updateSourceLocations(coverage, activeRecordings, selection, sourceLocationData);
									setSurfaceIcon(selection.metadata.isSurfaceMethod);
									toggleSurfaceMethodSubscription = $('#is-surface-method-button').asEventStream('click').onValue(toggleSurfaceMethod);
								}, (err) => { showSourceViewError(err, selection) })
							})
							selection.refresh = false
						} else {
							updateSourceLocations(coverage, activeRecordings, selection, null)
						}
					})

					sourceLocationUpdateSubscription = Trace.liveTraceData.onValue((nodeIds) => {
						for (i=0; i < nodeIds.length && !selection.refresh; i++) {
                    		selection.refresh = (nodeIds[i] === selection.metadata.nodeId)
						}
					})

					traceCoverageUpdateRequestsSubscription = Trace.traceCoverageUpdateRequests.onValue((data) => {
						let isLiveTraceDataUpdate = data.constructor === Array && !data.some(isNaN)
						if (!isLiveTraceDataUpdate) {
							selection.refresh = true
						}
					})
					}, (err) => { showSourceViewError(err, selection)
				})
			})

        popoutCloseClicks.onValue(deactivatePopout)
    })()


    // Set up the tooltip for the Code Treemap's info icon
	$('#treemap-help-tooltip-trigger').qtip({
		content: {
			text: $('#treemap-help-tooltip-content')
		},
		style: {
			classes: 'qtip-dark'
		}
	})

	// Initial value for instrumentedPackages. This will be replaced
	// once the PackageController is initialized.
	Trace.instrumentedPackages = d3.set()

	// Check if a node is marked as instrumented. Walks up the tree,
	// since if a node's parent is marked for instrumentation, that
	// implies the node itself should be instrumented.
	Trace.isInstrumentedNode = function isInstrumented(node){
		if(!node) return false

		var flag = Trace.instrumentedPackages.has(node.id),
			isPackage = node.kind == 'package'

		// If the search reaches a package node, return without traversing
		// further up the tree. E.g. <self> nodes's classes and methods are
		// instrumented only if that node is instrumented. Subpackages and
		// their children are only instrumented if the subpackage itself is
		// instrumented.
		if(isPackage){
			return flag
		} else {
			// non-package nodes should check with their parent if the flag
			// isn't explicitly set.
			return flag || isInstrumented(node.parent)
		}
	}

	// When the `treemapColoringStateChanges` fires, use the current `legendData`
	// to generate a new treemap coloring function, and update the treemap's colors
	// with that.
	Trace.treemapColoringStateChanges.toProperty('init').onValue(function(){
		var colorLegend = Trace.getColorLegend()
		var coloringFunc = treemapColoring(colorLegend)
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
	function treemapColoring(colorLegend){
		if(!arguments.length){
			var latest = treemapColoring.latest
			if(latest) return latest
			else colorLegend = {}
		}
		var ignoredKinds = d3.set(['group', 'package', 'root'])

		var coloringFunc = function(allNodes){

			var activeRecordingIds = Trace.getActiveRecordingIds(),
				allActivityId = Trace.allActivityRecordingId,
				allActivityColor = colorLegend[allActivityId],
				allActivityColorFaded = d3.interpolate('lightgray', allActivityColor)(.2),
				instrumentedPackages = Trace.instrumentedPackages

			function countActiveCoverings(coverage){
				var count = 0
				coverage.forEach(function(id){
					if(activeRecordingIds.has(id)){
						count++
					}
				})
				return count
			}

			var base = function(node){
				if(ignoredKinds.has(node.kind)) return '#ccc'


				var coverage = Trace.getCoverageSet(node.id),
					numCovered = countActiveCoverings(coverage)

				if(numCovered == 0) {
					if(coverage.has(allActivityId)){
						// console.log('all activity coverage for', node, 'with coverage', coverage)
						if(activeRecordingIds.empty()) return allActivityColor
						else return allActivityColorFaded
					} else {
						return '#eee'
					}
				}
				if(numCovered > 1) return colorLegend['multi']

				var entryId = undefined
				coverage.forEach(function(id){ if(activeRecordingIds.has(id)) entryId = id })
				var entry = colorLegend[entryId]

				return entry? entry : 'yellow'
			}

			return function(node){
				var baseColor = base(node)
				let color = baseColor
				if(Trace.isInstrumentedNode(node)) {
					color = baseColor
				} else {
					color =  d3.interpolate('darkgray', baseColor)(.2)
				}

				node.fillColor = color
				return color
			}
		}

		treemapColoring.latest = coloringFunc
		return coloringFunc
	}

	// this Bus can have boolean values or the string "toggle" pushed to it, to control
	// the value of the `isTreemapDrawerOpen` Property.
	var treemapDrawerOpen = new Bacon.Bus(),
		isTreemapDrawerOpen = treemapDrawerOpen.scan(false, function(open, cmd){
			if(cmd == 'toggle') return !open
			else return !!cmd
		})


	// When the 'show-treemap-button' is clicked, toggle the treemap drawer
	$('#show-treemap-button').asEventStream('click').map('toggle').assign(treemapDrawerOpen, 'push')

	// update some container classes when the treemap drawer goes in and out of view
	isTreemapDrawerOpen.onValue(function(show){
		$('#show-treemap-button').toggleClass('expanded', show)
		$('#treemap').toggleClass('in-view', show)
	})

	// show the dependency check report, when applicable
	depCheckController.reportShown.onValue(function(shown) {
		$('#dependency-check-report').toggleClass('in-view', shown)
	})

	var treemapMaximizer = $('#treemap .maximizer')

	var treemapMaximized = treemapMaximizer.asEventStream('click').map('toggle')
		.merge(
			isTreemapDrawerOpen.changes().filter(function(show){ return !show }).map('hide')
		)
		.scan(false, function(state, command){
			if(command == 'toggle') return !state
			return false
		})

	treemapMaximized.onValue(function(maxed){
		$('#treemap').toggleClass('maximized', maxed)
		treemapMaximizer.attr('title', maxed ? 'collapse' : 'expand')
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
	Trace.onTreeDataReady(function(){
		var packageTree = Trace.packageTree

		controller = new PackageController(packageTree, depCheckController, surfaceDetectorController, packagesContainer, $('#totals'), $('#packages-controls-menu'))

		surfaceDetectorController.showSurface.onValue(function(isOn) {
			isAttackSurfaceOn = isOn;
			API.getAttackSurface(function(data) {
				controller.unselectAll();
				controller.selectWidgetsForNodes(data);
				controller.isSurfaceOn(isOn)
			});
		});

		surfaceDetectorController.surfaceDetectorUpdates.onValue(function(status) {
			if(isAttackSurfaceOn) {
				API.getAttackSurface(function(data) {
					controller.unselectAll();
					controller.selectWidgetsForNodes(data);
					controller.isSurfaceOn(true)
				})
			} else {
				let selectedWidgets = controller.widgetsSelected()
				controller.unselectAll()
				controller.selectWidgetsForNodes(selectedWidgets)
				controller.isSurfaceOn(false)
			}
		})

		// When the selection of "instrumented" packages changes, trigger a coloring update
		// on the treemap, since nodes get special treatment if they are uninstrumented.
		controller.instrumentedWidgets.onValue(function(set){
			Trace.instrumentedPackages = set
			Trace.treemapColoringStateChanges.push('Instrumented Packages Changed')
		})

		// When the selection of "instrumented" packages changes, compare the new state
		// to the previous state; for each package whose state changed, tell the backend
		// about the change.
		controller.instrumentedWidgets
			// since `instrumentedWidgets` simply modifies and re-fires the same set instance,
			// we need to keep a snapshot copy of its values in order to see any actual changes.
			.map(function(set){ return d3.set(set.values()) })
			// Watch the most recent 2 values for changes
			.slidingWindow(2,2).onValue(function(ab){
				var before = ab[0],
					after = ab[1],
					changes = {}

				// any ids in `before` but not `after` have been removed
				before.forEach(function(id){
					if(!after.has(id)) changes[id] = false
				})

				// any ids in `after` but not `before` have been added
				after.forEach(function(id){
					if(!before.has(id)) changes[id] = true
				})

				// send the change set to the backend
				API.updateTreeInstrumentation(changes)
			})

		// Watch the package selection state, exposing it as an array
		// containing the IDs of selected packages.
		var selectedNodeIds = controller.selectedWidgets.map(function(set){
			return set.values()
		})

		// If there are no selected packages, the treemap container gets a
		// 'no-selection' class.
		selectedNodeIds.onValue(function(arr){
			treemapContainer.toggleClass('no-selection', !arr.length)
		})

		// When a non-empty node selection is made, force-open the treemap drawer
		selectedNodeIds.onValue(function(selection){
			if(selection.length) treemapDrawerOpen.push(true)
		})

		// As the package selection changes, ask the backend for a new tree
		// based on the selected packages. This tree will be used to populate
		// the treemap viz.
		var treemapData = selectedNodeIds.flatMapLatest(function(selectedIds){
			return Bacon.fromCallback(API.projectTreeMap, selectedIds)
		})

		// Match the 'compactMode' with the 'isTreemapDrawerOpen' and 'reportShown' states.
		// var compactMode = isTreemapDrawerOpen.or(depCheckController.reportShown)
		isTreemapDrawerOpen.onValue(function(show){
			controller.compactMode(show)
			$('.packages-header').toggleClass('compact', show)
		})

		// Highlight the package widgets when trace data comes in
		Trace.liveTraceData.onValue(controller.highlightPackages)

		// Update method coverage counts when the data is changed (or when recording selections change)
		Bacon.onValues(Trace.coverageRecords, Trace.activeRecordingsProp, function(coverage, activeRecordings){
			controller.applyMethodCoverage(coverage, activeRecordings)
		})

		// Set the coloring function and give the treemap data
		treemapWidget
			.nodeColoring(treemapColoring())

		function filterTree(node){
			node.children = node.children.filter(n => {
				if(n.isSurfaceMethod) {
					return true
				}

				if(n.kind != "method") {
					if(n.kind == "class") {
						return n.children && n.children.filter(c => c.isSurfaceMethod).length > 0
					} else {
						return true
					}
				} else {
					return false
				}
			})
			node.children.forEach(filterTree)
		}

		treemapData.onValue(function(tree){
			treemapContainer.overlay('wait')
			// put the following in a callback so the overlay has a chance to trigger
			// (the treemap data update can be expensive, and since it modifies the DOM,
			// the overlay may never actually show up because the DOM is being blocked.)
			setTimeout(function(){
				if(isAttackSurfaceOn) filterTree(tree.root)
				treemapWidget.data(tree).update()
				treemapContainer.overlay('ready')
			}, 1)
		})

		Trace.coverageRecords.onValue(setTreemapCoverage)
		Trace.liveTraceData.onValue(treemapWidget.highlightNodesById)

		// Fire a coverage update request in order to get the initial coverage data.
		Trace.traceCoverageUpdateRequests.push('initial load')

		function setTreemapCoverage(recordData){
			Trace.setCoverageMap(recordData)
			treemapWidget.nodeColoring(treemapColoring())
		}

		// Show the 'slow-treemap' status if the treemap is running slowly
		treemapWidget.isRunningSlowly
			.assign($('#status-bar'), 'toggleClass', 'slow-treemap')

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

			return function(node){
				if(node.kind == 'package' || node.kind == 'group'){
					return $('<div>')
				} else {

					// wrap the whole result in a <div class='content-padded'>
					var padDiv = $('<div>').addClass('content-padded')

					// Check if the node was encountered by any recordings;
					// if so, create an indication of which ones
					var recordings = Trace.getCoverageSet(node.id).values()
						.map(function(recId){ return Trace.getRecording(recId) })
						.filter(function(d){ return d })

					if(recordings.length){
						var container = $('<div>')
							.text('Traced by ')
							// .addClass('clearfix')
							.addClass('coverage-area')

						recordings.forEach(function(ld){
							var bg = ld.getColor(),
								lightness = d3.hsl(bg).l,
								textColor = (lightness > 0.4) ? 'black' : 'white'

							$('<div>')
								.addClass('coverage-label')
								.text(ld.getLabel() || 'Untitled Recording')
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
						if(i.kind == 'package' || i.kind == 'group') i = null
						else i = i.parent
					}

					// take the last path node of its kind from the path.
					// e.g. [P1, P2, C1, C2, C3, M] becomes [P2, C3, M]
					var pathCondensed = []
					for(i = path.length-1; i>=0; i--){
						var newPath = path[i],
							prevPath = path[i+1],
							newKind = newPath.kind
							prevKind = prevPath && prevPath.kind
						if(newKind != prevKind){
							pathCondensed.unshift(newPath)
						}
					}
					// render the condensed path instead of the full path.
					path = pathCondensed

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

					var result = $('<div>')

					// added the padded div that holds the ancestors list
					result.append(padDiv.append(recurse(0)))

					// if the node isn't "instrumented", add a div that says so
					if(!Trace.isInstrumentedNode(node)){
						var noTraceBadge = $('<div>')
							.addClass('notrace-badge')
							.text('This method is not marked for tracing')
							.prepend('<i class="fa fa-times">')
						result.append(noTraceBadge)
					}

					return result
				}
			}
		})()
	}

	treemapWidget.tooltipContentProvider(treemapTooltipContentProvider)

	// Allow the header title to be edited.
	// When it changes, send the change to the server to check for name conflicts
	$('h1.editable').editable().on('editable.save.cancel', function(e, newName){
		var n = e.namespace
		if(!n) return

		var nc = $('.nameConflict')

		var shouldSave = false
		if(n == 'save') shouldSave = true
		else if(n == 'cancel'){
			if(nc.hasClass('noTraceName')){
				shouldSave = true
				newName = $(this).editable('getText') || 'Untitled'
			}
		}

		if(shouldSave){
			API.renameProject(newName, function(reply, error){
				if(!error){
					var hasNameConflict = (reply.warn == 'nameConflict')
					$('.nameConflict').toggleClass('hasConflict', hasNameConflict)
				}
			})
		}
	})

	// If the nameConflict container has the 'noTraceName' class, open up the name editor now
	$('.nameConflict.noTraceName').each(function(){
		$('h1.editable').editable('open')
	})

	function requestProject() {
        API.getProjectData(function(reply, error){
            if(!error && reply){
                $('.edit-content').text(reply.name)
            }
        })
	}

    $(document).on('projectUpdated', requestProject)
})
