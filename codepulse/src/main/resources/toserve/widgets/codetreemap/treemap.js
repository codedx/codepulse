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
	
	var treemapSizingOptions = {
		'line-count': select('widgetLineCount'),
		'weakness-count': function(d){ return d.weaknessStats.count }
	}
	
	var severityColor = d3.scale.linear()
		.domain([0,1,2,3])
		.range(['white', 'yellow', 'orange', 'red'])
	
	// timing values that are considered "slow" for specific tasks
	var profileTimingSlowThresholds = {
		'runLayout': 400,
		'fullUpdateDisplay': 600,
		'highlightNodes': 100,
		'updateTreemapColors': 200
	}

	function timingEventIsSlow(event){
		var time = event.time,
			task = event.task,
			threshold = profileTimingSlowThresholds[task]

		if(isNaN(threshold) || time < threshold) return {fast: task}
		else return {slow: task}
	}

	var widget = exports.CodebaseTreemap = function(container){
		
		var self = this,
			$container = $(container),
			margin = {top: 5, right: 5, bottom: 5, left: 5},
			svg = d3.select(container).append('svg:svg'),
			nodeSizing = 'weakness-count',
			nodeColoring = 'weakness-count'
			
		/*
		 * Set up resize logic that monitors for resizes of the $container, and
		 * tells the widgetry to update itself when the size changes. Normally,
		 * we would just hook up to the window's resize event to wait for changes,
		 * but that doesn't trigger when a vertical scrollbar is introduced to the
		 * page, which is likely when the treemap svg is added. To deal with this,
		 * we add a listener that checks every 100ms to see if the container size
		 * has changed. If it has actually changed since the last check, the widget
		 * updates. For regular resize events (i.e. a user is drag-resizing the 
		 * window), the default check is silenced, and a spinner is introduced as
		 * UI feedback. Once the drag ends, the widget updates and the spinner goes
		 * away.
		 */
		!(function(){
			var lastWidth = 0,
				lastHeight = 0,
				resizeTimeout = undefined,
				resizing = false
			
			// a 'manual' resize, i.e. the window.resize event
			function onResizeStart() {
				if(!resizing){
					resizing = true
					$container.overlay('wait', {})
				}
				clearTimeout(resizeTimeout)
				resizeTimeout = setTimeout(onResizeEnd, 100)
			}
			
			// called 100 ms after the latest onResizeStart
			function onResizeEnd(){
				resizing = false
				resizeTimeout = undefined
				$container.overlay('ready')
				doResizeActions()
			}
			
			// check if the $container has changed sizes; if so,
			// update the svg and layout sizes and update the UI
			function doResizeActions(){
				// don't run this if a manual resize is going on
				if(resizing) return 
				
				var curWidth = $container.width(),
					curHeight = $container.height()
				if(curWidth != lastWidth || curHeight != lastHeight){
					/*
					 * At this point, the size change has been detected and acknowledged.
					 * Recompute the height and width of the svg and treemap layout, then
					 * update the UI.
					 */
					lastWidth = curWidth
					lastHeight = curHeight
					var tmHeight = Math.max(0, curHeight - margin.top - margin.bottom),
						tmWidth = Math.max(0, curWidth - margin.left - margin.right)
					
					svg.attr('width', curWidth).attr('height', curHeight)
					resetLayout(privateState, [tmWidth, tmHeight])
					self.update()
				}
			}
			
			$(window).resize(onResizeStart)
			setInterval(doResizeActions, 100)
		}())
		// end of big resize logic block
		
		var privateState = {
			treeData: null,
			layout: null, // updated by resetLayout
			nodeSizing: nodeSizing,
			nodeColoring: nodeColoring,
			svg: svg,
			margin: margin,
			eventHandler: new EventHandler(),
			minHeight: 0,
			timingEvents: new Bacon.Bus(),
			self: this
		}

		/**
		 * Rx Property that decides whether the widget is currently running 'slowly'.
		 * As various methods call profileTiming, they emit events to the `timingEvents`
		 * stream. If any one of those events takes longer than a predefined threshold,
		 * it causes this Property to become `true` for the next five seconds. Further
		 * 'slow' events will extend that duration.
		 */
		this.isRunningSlowly = privateState.timingEvents
			.map(timingEventIsSlow)
			.filter(select('slow'))
			.flatMapLatest(function(){
				// step function: starts at true, then becomes false 5 seconds later
				return Bacon.once(true).concat(Bacon.later(5000, false))
			})
			.toProperty(false)
			.skipDuplicates()
			//.log('timing slow?')
		
		resetLayout(privateState)

		privateState.hoverManager = new TooltipHoverManager(privateState)

		this.data = function(){
			if(arguments.length){
				updateData(privateState, arguments[0])
				return this
			} else {
				return privateState.treeData
			}
		}

		/** Updates the widget, passing in the given data.
		  * @param data an Array of objects, where each object represents 
		  * a single codebase node (e.g. a package or class).
		  * @param updateRoot an optional function that gets called on the
		  * root of the tree if the `data` tree gets calculated.
		  */
		this.update = function(){
			updateDisplay(privateState)
			return this
		}
		
		this.minHeight = function(mh){
			if(!arguments.length) return privateState.minHeight
			privateState.minHeight = mh
			updateDisplay(privateState)
			return this
		}
		
		/**
		 * Get or set the "node sizing" criteria.
		 * If `criteria` is specified, the sizing criteria will
		 * be set to that value. Otherwise, the function will just
		 * return the current node sizing criteria.
		 * 
		 * @param criteria A string that names one of the node sizing options.
		 * Possible values are: 'line-count' and 'weakness-count'.
		 */
		this.nodeSizing = function(criteria){
			if(!arguments.length) return privateState.nodeSizing
			
			privateState.nodeSizing = criteria
			resetLayout(privateState)
			updateDisplay(privateState)
			return this
		}
		
		/**
		 * Get or set the "node coloring" criteria.
		 * If `criteria` is specified, the coloring criteria will
		 * be set to that value. Otherwise, the function will just
		 * return the current node coloring criteria.
		 * 
		 * @param criteria [String|Function] Either a String that names one of the 
		 * node coloring options (Possible values are: 'max-severity', 'avg-severity', 
		 * and 'weakness-count') or a Function that returns a per-node coloring function
		 * when passed the current set of nodes.
		 * 
		 * e.g. `function(allNodes){ return function(node){ return fillForNode } }`
		 */
		this.nodeColoring = function(criteria){
			if(!arguments.length) return privateState.nodeColoring
			
			privateState.nodeColoring = criteria
			updateTreemapColors(privateState)
			return this
		}
		
		this.focusId = function(id){
			if(!arguments.length) return privateState.focusId
			
			// no need for updates unless the new id is different from the old one
			if(id != privateState.focusId) {
				privateState.focusId = id
				resetLayout(privateState)
				updateDisplay(privateState)
			}
			return this
		}
		
		/**
		 * Attach an event handler to this treemap. Key names the event type,
		 * and func should be a function that gets called when an event of
		 * that type gets triggered.
		 * 
		 * @param key The event type [String].
		 * @param func The callback [Function].
		 */
		this.on = function(key, func){
			privateState.eventHandler.addListener(key, func)
			return this
		}
		
		this.highlightNodesById = function(ids){
			highlightNodesById(privateState, ids)
		}

		this.tooltipContentProvider = function(){
			if(!arguments.length) {
				return privateState.tooltipContentProvider
			}

			var arg = arguments[0]
			if(typeof arg.calculateTitle != 'function' || typeof arg.calculateContent != 'function'){
				throw 'TooltipContentProvider must have both a `calculateTitle` and `calculateContent` function'
			}
			privateState.tooltipContentProvider = arg
			return this
		}
		
	}
	
	// Run `bodyFunc` and emit an event that shows how long it took
	function profileTiming(widgetState, label, bodyFunc){
		var startTime = Date.now(),
			result = bodyFunc(),
			endTime = Date.now(),
			event = {
				task: label,
				time: endTime - startTime
			}

		widgetState.timingEvents.push(event)
		return result
	}

	/**
	 * Wraps the sizing function specified at `widgetState.nodeSizing`,
	 * so that nodes that are not descendants of the current focus node
	 * will have a '0' value for layout purposes.
	 *
	 * @param widgetState The private state of the widget
	 * @return A function that returns a sizing value for a node, for use
	 * by the layout algorithm
	 */
	function innerNodeSizing(widgetState){
		return function(d){
			var f = treemapSizingOptions[widgetState.nodeSizing](d)
			var focus = widgetState.focusId
			if(!focus) return f
			else {
				while(d){
					if(d.id == focus) return f
					d = d.parent
				}
				return 0
			}
		}
	}

	/**
	 * Runs the layout algorithm on the `rootNode` ONLY IF the `widgetState`
	 * thinks it needs to run the layout (`needToRunLayout` flat). Once the
	 * layout is run, it assigns the resulting array of nodes to the `layoutNodes`
	 * field of the widgetState, and clears the `needToRunLayout` flag.
	 *
	 * @param widgetState THe private state of the widget
	 * @param rootNode The root of the tree to be laid out.
	 * @return The array of nodes
	 */
	function runLayout(widgetState, rootNode) {
		if(!widgetState.needToRunLayout && widgetState.layoutNodes) {
			return widgetState.layoutNodes
		}

		return profileTiming(widgetState, 'runLayout', function(){
			widgetState.layoutNodes = widgetState.layout.nodes(rootNode)
			widgetState.needToRunLayout = false
			return widgetState.layoutNodes
		})
	}

	/**
	 * Sets the `needToRunLayout` flag on the `widgetState`, so that the
	 * `runLayout` function will actually run the layout next time it is called.
	 *
	 * @param widgetState The private state of the widget
	 * @param overrideSize [Optional] Specify a [width,height] array to use as the size.
	 * If omitted, the new layout will try to use the size of the old layout.
	 */
	function resetLayout(widgetState, overrideSize) {
		// runLayout needs to do work next time it gets called
		widgetState.needToRunLayout = true

		// set up a new layout
		var newLayout = d3.layout.treemap()
			.value(innerNodeSizing(widgetState))
			.sticky(false)
			.padding(function(d){
				if(d.kind == 'package' || d.kind == 'group' || d.kind == 'root') return [12, 1, 1, 1]
				else return 0
			})

		// pick the newLayout's size based on the overrideSize or the old layout
		if(overrideSize && overrideSize.length){
			newLayout.size(overrideSize)
		} else if(widgetState.layout){
			newLayout.size(widgetState.layout.size())
		}

		// assign the newLayout to the widgetState
		widgetState.layout = newLayout
	}

	/**
	 * Add 'dx' and 'dy' properties that cannot be assigned to negative numbers.
	 * The underlying values are stored as '_dx' and '_dy'.
	 *
	 * @param d The object that receives the properties
	 * @return d (the first argument)
	 */
	function defineDXYProperties(d){
		// define getters+setters for dx and dy that prevent values less than 0
		d._dx = 0
		d.__defineGetter__('dx', function(){ return this._dx })
		d.__defineSetter__('dx', function(dx){ this._dx = Math.max(0, dx) })

		d._dy = 0
		d.__defineGetter__('dy', function(){ return this._dy })
		d.__defineSetter__('dy', function(dy){ this._dy = Math.max(0, dy) })

		return d
	}

	function updateData(widgetState, treeData) {
		if(!(treeData instanceof TreeData)) return

		profileTiming(widgetState, 'updateData', function(){
			// add the .dx and .dy properties to all the nodes
			defineDXYProperties(treeData.root)
			treeData.forEachNode(defineDXYProperties)

			// assign `widgetLineCount` from the root down
			computeLineCounts(treeData.root)

			// assign `antiDepth` from the root down
			computeAntiDepths(treeData.root)

			// invalidate the layout
			resetLayout(widgetState)

			// set the given treeData into the `widgetState`
			widgetState.treeData = treeData
		})
	}

	/*
	Class that deals with tooltips for the treemap's nodes. It uses the jquery 'qTip2' library
	to generate a single <div> that acts as the tooltip, and updates the contents and position
	of that <div> while the mouse is moving within the <svg> of the treemap.
	*/
	function TooltipHoverManager(widgetState){
		// access the actual <svg> element from the d3 selection saved in widgetState
		var svg = widgetState.svg[0][0]

		// initialize a qtip on an 'imaginary' <div> element: this tooltip will be
		// shared across all treemap nodes, and accessed as a 'qTip API' object
		var api = $('<div>').qtip({
			id: 'treemap-tooltip', // cosmetic: assigns id='treemap-tooltip' to the tooltip <div>
			content: '???', // this will be changed programatically later
			style: 'treemap-tooltip', // adds this CSS class to the tooltip <div>
			show: {
				effect: false, // no fade in or anything fancy
				delay: 0 // just show it immediately
			},
			hide: {
				effect: false, // no fade out or other snooty things
				delay: 0 // gtfo now
			},
			prerender: true, // adds the qTip <div> to the dom right now, instead of when we hover
			position: {
				my: 'top left', // default orientation is that the tooltip's top left corner is close to the mouse
				target: 'mouse', // follow the mouse, not the tooltip target
				viewport: $(svg), // constrains the tooltip to be inside the <svg>, hooray!
				adjust: { x:10, y:10 }, // mousePos + (10,10)
				effect: false // no tweens or animation
			}
		}).qtip('api')

		/*
		To be called when a treemap node gets a 'mouseover' event.
		*/
		this.enterNode = function(node){
			var provider = widgetState.tooltipContentProvider
			if(!provider) return

			try{
				// don't even show anything for the root node
				if(node.kind != 'root') api.show()

				api.set('content.title', provider.calculateTitle(node))
				api.set('content.text', provider.calculateContent(node))
			} catch(e){
				console.error("couldn't enterNode because of error:", e)
			}
		}

		/*
		To be called when a treemap node gets a 'mouseout' event.
		*/
		this.exitNode = function(node){
			try{
				api.hide()
			} catch(e){
				console.error("couldn't exitNode because of error:", e)
			}
		}

		// when moving the mouse in the <svg>, update the tooltip position as well
		widgetState.svg.on('mousemove', function(){
			try{
				api.reposition(d3.event)
			} catch(e){
				console.error('failed to update tooltip position:', e)
			}
		})
	}

	/**
	 * Updates the treemap visualization. The `newData` parameter is optional.
	 * If specified, the internal data map will be updated, adding new nodes where
	 * necessary, and updating the `weaknessStats` field of each node's entry.
	 * 
	 * @param widgetState The private state of the widget
	 * @param newData A map containing [node.id] -> [node] for all of the nodes
	 * to be displayed in the treemap. If not specified, the update function will
	 * assume that it should use the most recent data that *was* specified.
	 * @param updateRoot An optional function that will be called on the root of the
	 * tree when it gets computed
	 */
	function updateDisplay(widgetState) {
		profileTiming(widgetState, 'fullUpdateDisplay', function(){
			var treemapLayer = svgLayer(widgetState, 'treemap'),
				hoverLayer = svgLayer(widgetState, 'treemap-hover')

			var treeData = widgetState.treeData
			if(!treeData) return

			
			var treemapNodes = widgetState.dataArray = runLayout(widgetState, treeData.root)
				.filter(function(d){ return d.antiDepth >= widgetState.minHeight })
				
			// apply data to the treemap viz
			var allNodesSvg = treemapLayer.selectAll('g.node').data(treemapNodes, select('id'))
			//treemapLayer.selectAll('rect.node').data(treemapNodes, select('id'))
			
			allNodesSvg.exit().remove()
			
			var newNodesG = allNodesSvg.enter().append('svg:g')
				.attr('class', function(t){ return 'node node-' + t.kind })

			var groupProjectionIds = {}

			allNodesSvg
				.attr('transform', function(d){ return translate(d.x, d.y)})

			var newNodesSvg = newNodesG.append('svg:rect')
				.attr('class', function(t){ return 'node node-' + t.kind })
				.on('mouseover', function(d){ 
					setNodeHover(widgetState, d.id)
					widgetState.hoverManager.enterNode(d)
				})
				.on('mouseout', function(d){ 
					setNodeHover(widgetState, undefined)
					widgetState.hoverManager.exitNode(d)
				})
				.on('click', function(d){ 
					widgetState.self.focusId(d.id)
				})

			var rectProjectionIds = {}

			// Update the 'fill', 'ignored' class, and positioning of all nodes
			allNodesSvg.selectAll('rect.node').data(function(d){ return [d] })
				.style('fill', updateFillFunction(widgetState))
				.classed('ignored', function(n){ return n._ignored })
				.attr('width', select('dx'))
				.attr('height', select('dy'))

			updateLabels(widgetState, treemapNodes)
			updateHoverLayer(widgetState)
			updateHighlightLayer(widgetState)
		})
	}

	function svgLayer(widgetState, cssClass) {
		var selector = 'g.' + cssClass
		var layer = widgetState.svg.selectAll(selector).data([1])
		layer.enter().append('svg:g')
			.attr('class', cssClass)
			.attr('transform', translate(widgetState.margin.left, widgetState.margin.top))
		return layer
	}

	function updateTreemapColors(widgetState){
		profileTiming(widgetState, 'updateTreemapColors', function(){
			var treemapLayer = svgLayer(widgetState, 'treemap'),
				nodes = treemapLayer.selectAll('g.node rect.node'),
				fillFunc = updateFillFunction(widgetState)

			nodes.style('fill', fillFunc)
		})
	}
	
	/**
	 * Renders a "hover" effect on nodes that have the mouse pointed at them.
	 * Whatever objects are in the `widgetState.hoverData` array are expected to have
	 * an 'id' field set to the id of a corresponding node in `widgetState.data` object.
	 * This method adds <svg:rect> elements at the corresponding locations of those elements,
	 * but in the 'treemap-hover' <g> layer of the <svg>.
	 */
	function updateHoverLayer(widgetState) {
		var hoverLayer = svgLayer(widgetState, 'treemap-hover'),
			hoverData = widgetState.hoverData || (widgetState.hoverData = [])

		var selection = hoverLayer.selectAll('rect.node-outline').data(hoverData, select('id'))

		selection.exit().remove()

		selection.enter().append('svg:rect').attr('class', 'node-outline')

		// move the rectangles to be at the same location as their counterparts within `nodeData`
		selection.call(copycatPosition(widgetState.treeData))
	}

	function copycatPosition(treeData){
		return function(d){ this
			.attr('x', function(d){ return treeData.getNode(d.id).x })
			.attr('y', function(d){ return treeData.getNode(d.id).y })
			.attr('width', function(d){ return treeData.getNode(d.id).dx })
			.attr('height', function(d){ return treeData.getNode(d.id).dy })
		}
	}
	/**
	 * Sets the `widgetState.hoverData` array to contain the given
	 * node and any/all of its parent nodes. Nodes in the `hoverData`
	 * array are simply in the form of {'id': nodeId}, where nodeId
	 * is the same 'id' as a corresponding treemap node. Specifying
	 * a nodeId of `null` or `undefined` is equivalent to clearing
	 * the `hoverData` array.
	 */
	function setNodeHover(widgetState, nodeId) {
		var hoverData = widgetState.hoverData = [],
			node = widgetState.treeData.getNode(nodeId)

		while(node){
			hoverData.push({'id': node.id})
			node = node.parent
		}
		updateHoverLayer(widgetState)
	}

	function updateHighlightLayer(widgetState) {

		var treemapLayer = svgLayer(widgetState, 'treemap'),
			highlightData = widgetState.highlightData || (widgetState.highlightData = {}),
			highlights = d3.values(highlightData)

		var selection = treemapLayer
			.selectAll('g.node')
			.filter(function(d){ return d.highlightData })
			.selectAll('rect.node-highlight').data(function(d){
				return d.highlightData ? [d.highlightData] : []
			})

		selection.enter().append('svg:rect')
			.attr('class', 'node-highlight')

		selection.exit().remove()

		selection.filter(select('isNew'))
			.each(function(d){ d.isNew = false })
			.call(fadeMeOut)

		selection
			.attr('width', selectPath('node', 'dx'))
			.attr('height', selectPath('node', 'dy'))

		function fadeMeOut(){ 
			this
				.interrupt()
				.style('opacity', 1)
			.transition().duration(5000)
				.style('opacity', 0)
				.each('end', function(d){ delete d.node.highlightData })
				.remove()
		}
	}

	function highlightNodesById(widgetState, idsArray){
		profileTiming(widgetState, 'highlightNodes', function(){
			var treeData = widgetState.treeData
			if(!treeData) return

			// simply calls setHighlight after looking up the node by id
			function setHighlightById(id){ setHighlight(treeData.getNode(id)) }

			// sets the node's highlightData, and recurses into the node's parent
			// ignores root and package nodes
			function setHighlight(node){
				if(!node) return
				if(node.kind == 'package' || node.kind == 'group' || node.kind == 'root') return

				var hd = node.highlightData
				if(hd) hd.isNew = true
				else node.highlightData = {'node': node, 'isNew': true}

				setHighlight(node.parent)
			}

			// apply the highlight function to all of the IDs
			idsArray.forEach(setHighlightById)

			// trigger a UI update
			updateHighlightLayer(widgetState)
		})
	}

	/**
	 * Recursively set the `widgetLineCount` value of the node based on the `lineCount`
	 * for its child nodes. Since 'package' and 'root' nodes don't specify a `lineCount`, 
	 * they use the sum of their children's `widgetLineCount`s. Regular nodes use their 
	 * own `lineCount` as the `widgetLineCount`.
	 */
	function computeLineCounts(node){
		var kids = node.children || []
		kids.forEach(computeLineCounts)
		
		if(node.kind == 'package' || node.kind == 'group' || node.kind == 'root'){
			node.widgetLineCount = d3.sum(kids, select('widgetLineCount'))
		} else {
			node.widgetLineCount = node.lineCount || 1
		}
	}

	/**
	 * Recursively set the `antiDepth` for the `node` and all of its descendants.
	 * The `antiDepth` is the maximum ditance between a node and a leaf node; leaf
	 * nodes have an 'antiDepth' of 0. Parent and grandparent nodes will have anti-
	 * depths of 1 and 2 respectively.
	 *
	 * The `antiDepth` property is used to filter the treemap visualization; the
	 * 'display methods' vs 'display classes' options on the traces page set anti-
	 * depth minimums of 0 and 1 respectively.
	 */
	function computeAntiDepths(node){
		var kids = node.children || [],
			max = 0
		kids.forEach(function(d){
			var dd = computeAntiDepths(d) + 1
			if(dd > max) max = dd
		})
		return node.antiDepth = max
	}

	/**
	 * Returns the appropriate fill function for the treemap's
	 * nodes based on the current value of widgetState.nodeColoring.
	 * `nodeColoring` should be a function, which will be evaluated
	 * on the current `treemapNodes` in order to return the new fill
	 * function.
	 */
	function updateFillFunction(widgetState){
		var c = widgetState.nodeColoring,
			treemapNodes = widgetState.dataArray || []
		return widgetState.nodeFillFunc = c(treemapNodes)
	}
	
	function labelStyle(){ this
		.style('font-size', '10px')
		.style('fill', 'white')
		.style('pointer-events', 'none')
	}
	
	/**
	 * Given a name (i.e. a Java Package name), return
	 * an array of progressively-shorter strings that can
	 * be used to represent the name.
	 */
	function nameClippings(name, sep){
		var clips = [name]
		var parts = name.split(sep)
		for(var i=0; i<parts.length; i++) clips.push( parts.slice(i).join(sep) )
		clips.push("*")
		return clips
	}
	
	/**
	 * Creates an object with a `measure` method, to be used for measuring
	 * the width of a String when rendered in an svg `text` element.
	 * 
	 * @param widgetState The treemap's private vars, used to get the `svg` element.
	 * @param applyStyle A function to be applied to a D3 selection of "svg:text"
	 * elements, used to apply the appropriate style to the measurement text.
	 */
	function TextMeasurer(widgetState, applyStyle){
		// append a text element to the widget's svg.
		// apply the custom style, then make it invisible
		var d3m = widgetState.svg.append('svg:text')
			.attr('class', 'measurement')
			.call(applyStyle)
			.style('opacity', 0)
		
		// get `m` out of the d3 selection, and wrap it as a jQuery selection
		var m = null
		d3m.each(function(d){ m=$(this) })
		
		// store widths for a quick return later on
		var cache = {}
		
		/**
		 * Measure the given `txt`, returning its displayed width in pixels.
		 */
		this.measure = function(txt){
			if(cache[txt]) return cache[txt]
			else {
				d3m.text(txt)
				var w = m.width()
				cache[txt] = w
				return w
			}
		}
	}
	
	function updateLabels(widgetState, treemapNodes){
		console.log('updateLabels')

		var measure = (widgetState.textMeasurer || (widgetState.textMeasurer = new TextMeasurer(widgetState, labelStyle))).measure
		var labelsLayer = widgetState.svg.selectAll('g.treemap-labels').data([1])
		
		labelsLayer.enter().append('svg:g')
			.attr('class', 'treemap-labels')
			.attr('transform', translate(widgetState.margin.left, widgetState.margin.top))
		
		var labeledNodes = treemapNodes.filter(function(d){ return d.kind == 'package' || d.kind == 'group' })

		var labelSvg = labelsLayer.selectAll('text.treemap-label').data(labeledNodes, select('id'))
		labelSvg.exit().remove()
		labelSvg.enter().append('svg:text')
			.attr('class', 'treemap-label')
			.call(labelStyle)
			
		labelSvg
			.attr('x', function(d){ return d.x + 2 })
			.attr('y', function(d){ return d.y + 10 })
			.text(function(d){
				if(d.dy < 10) return ''
				var nameSep
				if (d.kind == 'package') nameSep = '.'
				else nameSep = ' / '
				var names = nameClippings(d.name, nameSep).sort(function(a,b){
						return measure(b) - measure(a)
					})
				for(var i=0; i<names.length; i++){
					var n = names[i]
					if(measure(n) < d.dx - 4) return n
				}
				return ''
			})
	}
	
	/* Convenience Functions below*/
	
	/**
	 * Creates a function that looks up the given key on the argument.
	 * 
	 * @param key The field to look up. For example, `select('field')`
	 * is semantically the same as the scala expression `_.field`. 
	 */
	function select(key){ return function(d){ return d[key] } }
	
	/**
	 * Returns an SVG "translate" string based off the given `x` and `y` values.
	 * @param x The x offset of the translation
	 * @param y The y offset of the translation
	 */
	function translate(x,y){ return 'translate(' + x + ',' + y + ')' }
	
	/**
	 * Creates a function that looks up a path of keys on the argument.
	 * For example: `selectPath('a', 'b', 'c')(obj)` is similar to the expression
	 * `obj['a']['b']['c']` except that it will avoid field lookups once it
	 * reaches an `undefined`.
	 */
	function selectPath(){
		var a = Array.prototype.slice.call(arguments)
		return function(d){
			var accum = d, i
			for(i=0; i<a.length && accum; accum = accum[a[i]], ++i){}
			return accum
		}
	}
	
	function EventHandler() {
		var events = {}
		this.addListener = function(key, func){
			var l = events[key] || (events[key] = [])
			l.push(func)
		}
		this.trigger = function(key, data){
			var l = events[key]
			l && l.forEach(function(f){ f(data) })
		}
	}
	
})(window);