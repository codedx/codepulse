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

	function PieClock(parentElem, size){
		var svg = createSvg(parentElem, size)
		var arc = createArc(size)
		var path = createPath(svg, size, arc)

		function animateCountdown(duration){
			path.interrupt().transition().duration(duration).ease('linear')
				.attrTween('d', arcTween)
		}

		this.animateCountdown = animateCountdown
		// animateCountdown(10000)

		this.show = function(){ svg.style('display', null) }
		this.hide = function(){ svg.style('display', 'none') }
	}

	function createSvg(parentElem, size){
		return d3.select(parentElem).append('svg:svg')
			.attr('height', size)
			.attr('width', size)
	}

	function createArc(size){
		return d3.svg.arc()
			.innerRadius(0)
			.outerRadius(size / 2)
			.startAngle(0)
			.endAngle(function(ratio){
				return -Math.PI * 2 * ratio
			})
	}

	function updateArc(arc, ratio){
		return arc
			.startAngle(0)
			.endAngle(-Math.PI * 2 * ratio)
	}

	function createPath(svg, size, arc){
		return svg.selectAll('path')
			.data([arc])
		.enter().append('svg:path')
			.attr('transform', 'translate(' + (size/2) + ',' + (size/2) + ')')
			.attr('d', function(arc){ return arc(1) })
	}

	function updatePath(path, arc){
		return path.attr('d', arc)
	}

	function arcTween(arc){
		var i = d3.interpolate(1,0)
		return function(t){
			return arc(i(t))
		}
	}

	exports.PieClock = PieClock

})(this)