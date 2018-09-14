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

	exports.PackageController = PackageController

	/*
	 * Utility class used by the PackageController.
	 *
	 * A SetProp instance maintains a `d3.set` of ids. By adding a
	 * `widget` (e.g. a PackageWidget instance) with a given `id`,
	 * it will use the `getCurrent` and `getChanges` to make sure
	 * the "state" of that widget is kept track of in thet set.
	 *
	 * This is used as an alternative to `Bacon.combineTemplate`,
	 * which seems to perform poorly when there are thousands of
	 * items in the template.
	 *
	 * The intended use case for this class is to create a property
	 * representing the set of all "selected" packages, and another
	 * property representing the set of all "instrumented" packages.
	 *
	 * @param getCurrent - a function(widget) that returns the current 
	 *   "state" of the widget, as a boolean
	 * @param getChanges - a function(widget) that returns a 
	 *   Bacon.Observable that pushes boolean events that represent
	 *   the widget's "state" as it changes.
	 */
	function SetProp(getCurrent, getChanges){

		var set = d3.set()
		var bus = new Bacon.Bus()

		this.prop = bus.toProperty(set)

		this.add = function(id, widget){
			var current = getCurrent(widget)
			if(current) set.add(id)

			getChanges(widget).onValue(function(bool){
				if(bool & !set.has(id)) {
					set.add(id)
					bus.push(set)
				} else if(!bool && set.has(id)){
					set.remove(id)
					bus.push(set)
				}
			})
		}
	}

	function PackageController(treeData, depCheckController, surfaceDetectorController, $container, $totalsContainer, $clearSelectionButton){

		// build this into a Map[packageId -> packageWidget.selectedProp]
		var selectedWidgetsSP = new SetProp(
			/*getCurrent*/ function(pw){ return pw.selected() },
			/*getChanges*/ function(pw){ return pw.selectedProp.changes() }
			)

		// build this into a Map[packageId -> packageWidget.instrumentationSelectedProp]
		var instrumentedWidgetsSP = new SetProp(
			/*getCurrent*/ function(pw){ return pw.instrumentationSelected() },
			/*getChanges*/ function(pw){ return pw.instrumentationSelectedProp.changes() }
			)

		var widgets = {}

		// build this into a Map[node.id -> packageNode]
		// Note: the package tree doesn't include all nodes; instead, it includes
		// an 'otherDescendantIds' array, to represent all of the 'direct' descendants
		// of each package|group.
		var nodePackageParents = {}

		// Get the partial selection state for the trace instrumentation flag, for nodes that
		// would exist in the package list. Note that <self> nodes will take their original
		// node's `id` and `traced` properties, and the original's `traced` will be set to
		// undefined. This function sets a `partialTraceSelection` value on nodes recursively,
		// with `1 = fully selected`, `0 = unselected`, and `undefined = partially selected`.
		;(function calculatePartialTraceSelections(node){
			var isFullSelected = true,
				isPartialSelected = false

			if(node.traced == 0) isFullSelected = false

			node.children.forEach(function(kid){
				var kidSelection = calculatePartialTraceSelections(kid)
				if(!kidSelection) isFullSelected = false
				if(kidSelection || kidSelection == undefined) isPartialSelected = true
			})

			var selectionValue = isFullSelected ? 1 : isPartialSelected ? undefined : 0
			node.partialTraceSelection = selectionValue
			return selectionValue
		})(treeData.root)

		// special case: the root is never selected
		treeData.root.partialTraceSelection = 0

		// walk the tree in order to calculate the `nodePackageParents` map.
		;(function calculateNodePackageParents(node, parent){
			if(parent) nodePackageParents[node.id] = parent

			node.children.forEach(function(child){
				calculateNodePackageParents(child, node)
			})

			if(node.otherDescendantIds) node.otherDescendantIds.forEach(function(childId){
				nodePackageParents[childId] = node
			})

		})(treeData.root, undefined)

		var widgetCount = 0

		// initialize the `stateTemplate` and `widgets` maps
		// based on the package nodes in `treeData`
		;(function setupTreeHierarchy(packageParentNode, node){

			var pw = new PackageWidget()
			widgets[node.id] = pw
			pw.associatedNode = node
			// pw.parentNode = packageParentNode
			widgetCount++

			pw.instrumentationSelected(node.partialTraceSelection)

			pw.collapseChildren(/* collapsed = */true, /* animate = */false)

			pw.uiParts.collapser.click(function(event){
				pw.collapseChildren('toggle', true)
				event.stopPropagation()
			})

			node.children
				.sort(function(a,b){
					// alphabetic sort by node.label
					var an = a.label.toUpperCase(),
						bn = b.label.toUpperCase()

					if(an < bn) return -1
					if(an > bn) return 1
					return 0
				})
				.forEach(function(kid){
					var nextParent = (node.kind == 'group' || node.kind == 'package')? node : packageParentNode
					setupTreeHierarchy(nextParent, kid)
				})

			// Figure out the full+abbreviated labels for the widget
			if(node.kind == 'root'){
				pw.abbreviatedLabel('Overall Coverage')
			} else if(node.isSelfNode){
				pw.fullLabel(node.label).abbreviatedLabel(node.label)
			} else {
				var abbrevName

				if (node.kind != 'group' && packageParentNode && packageParentNode.kind == 'group')
					abbrevName = node.label
				else {
					var parentName = packageParentNode ? packageParentNode.label : '',
						abbrevName = node.label.substr(parentName.length)
				}

				pw.fullLabel(node.label).abbreviatedLabel(abbrevName)
			}

			if(node.kind != 'root'){

				pw.methodCount(node.methodCount)

				if(packageParentNode){
					widgets[packageParentNode.id].children.add(pw)
				} else {
					pw.uiParts.main.appendTo($container)
				}

			}

		})(undefined, treeData.root)

		// wire up the dependency area to listen for dependency check status
		var depRoot = false
		treeData.root.children.forEach(function (subroot, idx) {
			if (subroot.kind == 'group' && (subroot.label == 'JARs' || idx == 0)) {
				depRoot = widgets[subroot.id]
			}
		})

		if (depRoot) {
			depCheckController.status.onValue(depRoot.addDependencyCheckBadge)
			depCheckController.vulnerableNodes.onValue(function(vulnNodes) {
				vulnNodes.forEach(function (nid) {
					// mark any vulnerable nodes as vulnerable.
					// this will NOT unmark any previously marked nodes, we're not
					// expecting nodes to become not-vulnerable
					var node = treeData.getNode(nid)
					if (node) {
						var pw = widgets[node.id]
						pw.addVulnerableBadge(true)

						;(function bubbleUp(n) {
							if (!n) return
							if (widgets[n.id]) widgets[n.id].addVulnerableBadge(false)
							bubbleUp(n.parent)
						})(node.parent)
					}
				})
			})
		}

		console.log('created', widgetCount, 'PackageWidgets')

		function forEachWidget(f){
			for(var id in widgets){
				var w = widgets[id], n = w.associatedNode
				f(w,n,id)
			}
		}

		forEachWidget(function(pw, node, id){
			selectedWidgetsSP.add(id, pw)
			instrumentedWidgetsSP.add(id, pw)
			pw.selectionClicks.onValue(function(){
				handleSelectionClick(node, pw)
			})
			pw.instrumentationSelectedClicks.onValue(function(){
				handleInstrumentationSelectionClick(node, pw)
			})
			pw.vulnerableBadgeClicks.onValue(function() {
				depCheckController.showReport(node, pw == depRoot)
			})
		})

		// Disable all of the widgets while the trace is running
		Trace.running.onValue(function(isRunning){
			forEachWidget(function(pw, node){
				if(node.kind != 'root'){
					pw.instrumentationSelectable(!isRunning)
				}
			})
		})

		// checkSelected = function(widget){ return <is widget selected> }
		// setSelected = function(widget, sel){ <set widget.selected to sel> }
		// Returns a function(node, widget):
		//   node is the starting point in the data tree
		//   widget is the corresponding widget for the node
		//
		//   The function applies partial selection logic as if the widget
		//   had been clicked.
		function bubblePartialSelection(checkSelected, setSelected){
			return function(node, widget){
				// selected may be one of [1, 0, undefined].
				// transition [1 -> 0], [0 -> 1], [undefined -> 1]
				var newSel = +!checkSelected(widget)

				// apply the new selection to this node and each of its children
				;(function bubbleDown(w){
					setSelected(w, newSel)
					w.children.forEach(bubbleDown)
				})(widget)

				// climb the tree, re-checking the full/partial selection state of node's ancestors
				;(function bubbleUp(n){
					if(!n || n.kind == 'root') return
					var w = widgets[n.id]
					if(!w) return

					var isFullSelected = true,
						isPartialSelected = false

					w.children.forEach(function(c){
						var s = checkSelected(c)
						if(!s) isFullSelected = false
						if(s == 1 || s == undefined) isPartialSelected = true
					})

					var s = isFullSelected ? 1 : isPartialSelected ? undefined : 0
					setSelected(w, s)

					bubbleUp(n.parent)

				})(node.parent)
				var p = node.parent, pw = p && widgets[p.id]
			}
		}

		// a function(node,widget) that toggles the tri-state 'selected' property,
		// starting from the given node+widget
		var handleSelectionClick = bubblePartialSelection(
			/*get*/ function(w){ return w.selected() },
			/*set*/ function(w,s){ return w.selected(s) }
			)

		// a function(node, widget) that toggles the tri-state 'instrumentationSelected'
		// property, starting from the given node+widget
		var handleInstrumentationSelectionClick = bubblePartialSelection(
			/*get*/ function(w){ return w.instrumentationSelected() },
			/*set*/ function(w,s){ return w.instrumentationSelected(s) }
			)

		/**
		 * Exposes the selection state of each of the widgets, as a
		 * Set containing the IDs of selected widgets.
		 */
		this.selectedWidgets = selectedWidgetsSP.prop.debounce(10).noLazy()

		/**
		 * Exposes the 'instrumented' state of each of the widgets, as
		 * a Set containing the IDs of widgets that are marked as instrumented.
		 */
		this.instrumentedWidgets = instrumentedWidgetsSP.prop.debounce(10).noLazy()

		// Decide whether or not to show the "clear all selections" button
		// depending on whether or not there are selected widgets
		this.selectedWidgets
			.map(function(selectedIds){
				return !selectedIds.size()
			})
			.assign($clearSelectionButton, 'toggleClass', 'hidden')

		// Set all (selectable) widgets to not be selected when the clear selection button is clicked
		$clearSelectionButton.click(function(){
			for(var k in widgets){
				var w = widgets[k]
				if(w.selectable()) w.selected(false)
			}
			surfaceDetectorController.cancelShowSurface()
		})

		/**
		 * Getter / Setter for "compact" mode, which causes the
		 * widgets to take on a compact look.
		 *
		 * controller.compactMode() - returns the current flag
		 * controller.compactMode(b) - sets the current flag to `b`
		 */
		var _compactMode = false
		this.compactMode = function(){
			if(arguments.length){
				_compactMode = arguments[0]
				$container.toggleClass('compact', _compactMode)
				return this
			} else {
				return _compactMode
			}
		}

		this.highlightPackages = function(methodIds){
			highlightPackages(treeData, widgets, methodIds, nodePackageParents)
		}

		this.applyMethodCoverage = function(coverageRecords, activeRecordings){
			applyMethodCoverage(treeData, widgets, coverageRecords, activeRecordings, nodePackageParents)
		}

		this.selectWidgetsForNodes = function(nodes){
            nodes.forEach(n => {
				function selectNodeAncestors(id, leaf) {
					if(widgets[id]) {
						if(leaf) {
							widgets[id].selected(true)
						} else {
							widgets[id].selected(undefined)
						}
					}

					let node = nodePackageParents[id]
					if(node && widgets[node.id]) {
						widgets[node.id].selected(undefined)
					}

					if(node && node.parent) {
						selectNodeAncestors(node.parent.id, false)
					}
				}

				selectNodeAncestors(n, true)
            })
		}

		this.unselectAll = function(){
			for(const key in widgets) {
				widgets[key].selected(false)
			}
		}
	}

	// Trigger a `flashHighlight` on the appropriate package widgets
	// 
	// @param treeData - the tree structure being managed
	// @param widgets - a Map[packageNode.id -> PackageWidget]
	// @param methodIds - an Array[method.id]
	function highlightPackages(treeData, widgets, methodIds, nodePackageParents){
		var toHighlight = {}


		function bubbleUp(n){
			if(!n) return

			if(widgets[n.id]) toHighlight[n.id] = 1

			/* Alternate Strategy:
			 * Only count methods that fall directly beneath each package;
			 * to do so, uncomment the following:
			 */
			// if(n.packageWidget) return

			bubbleUp(n.parent)
		}

		methodIds.forEach(function(id){
			var bubbleFrom = treeData.getNode(id) || nodePackageParents[id]
			// bubbleUp(treeData.getNode(id))
			bubbleUp(bubbleFrom)
		})

		for(var id in toHighlight){
			widgets[id].flashHighlight()
		}
	}

	/**
	 * Calculate and apply the method coverage counts for each of
	 * the PackageWidgets, based on the given `coverageRecords`.
	 *
	 * @param treeData - the tree structure being managed
	 * @param widgets - a Map[packageNode.id -> PackageWidget]
	 * @param coverageRecords - An object expected to be in the form:
	 *   as a Map[method.id -> Array[recording.id]], where `recording`
	 *   is from the trace recording controls, and its presence in the
	 *   array means that particular recording encountered the respective
	 *   method.
	 * @param activeRecordings - A set of recording IDs. Coverage only
	 *   counts if the recording that covered a method was selected.
	*/
	function applyMethodCoverage(treeData, widgets, coverageRecords, activeRecordings, nodePackageParents){

		// If anything is selected, use that selection to determine coverage.
		// If nothing is selected, all coverage data counts (i.e. 'all activity' is what's covered)
		var noSelectedRecordings = activeRecordings.empty()

		function isCoveredBySelectedRecording(nodeId){
			var records = coverageRecords[nodeId]
			if(!records) return false

			if(noSelectedRecordings){
				return records.length > 0
			} else {
				for(var i in records){
					var recordingId = records[i]
					if(activeRecordings.has(recordingId)) return true
				}
				return false
			}
		}

		function computeAndApplyCoverage(node){
			var coverage = 0

			// if there was a record for the current node, count that as 1 coverage point
			// if(coverageRecords[node.id] && coverageRecords[node.id].length){
			// 	coverage += 1
			// }
			coverage += isCoveredBySelectedRecording(node.id)

			if(node.otherDescendantIds) node.otherDescendantIds.forEach(function(id){
				coverage += isCoveredBySelectedRecording(id)
			})

			// also include the sum of the childrens' coverages (recursive)
			;(node.children || []).forEach(function(kid){
				coverage += computeAndApplyCoverage(kid)
			})

			// apply the `coverage` to the `totalCoverage` property
			if(widgets[node.id]) widgets[node.id].totalCoverage(coverage)

			// return the `coverage` for recursion's sake
			return coverage
		}

		computeAndApplyCoverage(treeData.root)
	}
	

})(this);