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

// type LegendEntry:
// {
//  shortName: String,
//  longName: String,
//  color: D3_Color
// }

!(function(exports){

	function ColorLegend(container){
		var $events = $('<div>')
		
		var privates = {
			'legendDiv': d3.select(container).append('div').attr('class', 'color-legend'),
			'entryCounts': {},
			'legendData': undefined,
			'trigger': $events.trigger.bind($events)
		}
		this.legendData = legendData(this, privates)
		this.updateWidgetry = updateWidgetry(this, privates)
		
		/**
		 * this.on('select', function(event, entry){ entry was selected by a click })
		 */
		this.on = function(){
			$events.on.apply($events, arguments)
			return this
		}
	}
	
	exports.ColorLegend = ColorLegend
	
	function legendData(widget, privates){
		return function(/* one optional argument */){
			if(arguments.length){
				updateActiveEntries(privates.legendData, arguments[0])
				privates.legendData = arguments[0]
				return widget
			} else {
				return privates.legendData
			}
		}
	}
	
	/**
	 * Propagates any 'active' entry state from oldData to newData.
	 * @param oldData the old legendData value, from which to take the active states
	 * @param newData the new legendData value, into which the active states should be copied
	 */
	function updateActiveEntries(oldData, newData){
		if(!oldData || !newData) return

		// For each entry, if the oldData had an entry assigned as `active`, 
		// set the corresponding value in the newData as `active` too.
		for(var key in oldData.entries){
			var oldEntry = oldData.entries[key],
				newEntry = newData.entries[key]
			if(newEntry){
				newEntry.active = oldEntry.active
			}
		}
	}

	function arrayIt(d){ return [d] }
	
	function updateWidgetry(widget, privates){
		
		// To avoid closing over one version of entryData for one update, then a
		// different version for the next update, `entryData` is declared outside
		// of the following function, and simply updated when the function is called.
		var entryData

		return function(){
			entryData = d3.entries(privates.legendData.entries)

			function activateEntry(){
				return function (e){
					var oldActive = e.value.active
					entryData.forEach(function(e){
						e.value.active = false
					})
					e.value.active = !oldActive
					widget.updateWidgetry()
					privates.trigger('select', e)
				}
			}
			
			var entries = privates.legendDiv.selectAll('div.legend-entry')
				.data(entryData, function(e){ return e.key })
			
			entries.enter().append('div')
				.attr('class', 'legend-entry')
				.on('click', activateEntry())
			entries.exit().remove()
			entries
				.attr('title', function(e){ return e.value.longName })
				.classed('active', function(e){ return e.value.active })
				
			var swatches = entries.selectAll('div.color-swatch').data(arrayIt)
			
			swatches.enter().append('div').attr('class', 'color-swatch')
			swatches.style('background-color', function(e){ return e.value.color })
				
			var counters = swatches.selectAll('span.counter').data(arrayIt)
			counters.enter().append('span').attr('class', 'counter')
			counters.text(function(e){ return e.value.counter })
				
			var texts = entries.selectAll('span.legend-text').data(arrayIt)
			texts.enter().append('span').attr('class', 'legend-text')
			texts.text(function(e){ return e.value.shortName })
			
			return widget
		}
	}
	

}(this))
