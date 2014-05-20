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

	// The raw HTML for the color picker template is found in the recording controls,
	// and must be loaded when the document is ready.
	var colorPickerTemplate
	$(document).ready(function(){
		var templatesContainer = $('#recording-controls [data-role=templates-container]')
		colorPickerTemplate = templatesContainer.find('[data-role=colorpicker-template]')
	})

	/*
	 * Uses the 'Flexi' ColorPicker widget in conjunction with qTip2
	 * to install a color chooser dialog to the left of the given jquery
	 * selected `$elem`.
	 *
	 * @param $elem - the trigger element. Clicking it will activate the tooltip/chooser
	 * @param initialColorRaw - a css color string (it will be sanitized via d3.rgb)
	 * @param callbacksObject - an object that contains callback functions:
	 *   callbacksObject.progressCallback - called when the color chooser's cursors are moved
	 *   callbacksObject.finishedCallback - called when ok/cancel is pressed, or the tooltip
	 *                                      is hidden for other reasons. If OK, the argument is
	 *                                      the color that the user picked
	 *   callbackObjects.onOpen - called when the tooltip opens
	 *   callbackObjects.onClose - called when the tooltip closes
	 * @param positionOption - (optional) an object that can be used to override the default
	 *                         qtip position of `{my: 'right center', at: 'left center', viewport: $(window)}`
	 */
	function colorpickerTooltip($elem, initialColorRaw, callbacksObject, positionOption){
		var template = colorPickerTemplate.clone(),
			slider = template.find('.slider')[0],
			picker = template.find('.picker')[0],
			pickerCursor = template.find('.picker-indicator')[0],
			sliderCursor = template.find('.slider-indicator')[0],
			okButton = template.find('[name=ok]'),
			cancelButton = template.find('[name=cancel]'),
			initialColor = d3.rgb(initialColorRaw).toString(),
			currentColor = initialColor,
			qtipApi = undefined,
			qtipHide = undefined,
			qtipOpen = false,
			okClicked = false

		callbacksObject = callbacksObject || {}
		var noop = function(){},
			progressCallback = callbacksObject['progressCallback'] || noop,
			finishedCallback = callbacksObject['finishedCallback'] || noop,
			onOpen = callbacksObject['onOpen'] || noop,
			onClose = callbacksObject['onClose'] || noop

		// puts "pointerEvents: none;" on the cursors' styles
		ColorPicker.fixIndicators(pickerCursor, sliderCursor)

		function pickerCallback(hex, hsv, rgb, pickerCoord, sliderCoord){
			ColorPicker.positionIndicators(
				sliderCursor, pickerCursor,
				sliderCoord, pickerCoord)

			progressCallback(hex)
			currentColor = hex
		}

		function onOk(e){
			okClicked = true
			$elem.qtip('hide')
		}

		function onCancel(e){
			okClicked = false
			$elem.qtip('hide')
		}

		okButton.click(onOk)
		cancelButton.click(onCancel)

		var cp = new ColorPicker(slider, picker, pickerCallback)
		cp.setHex(currentColor)

		$elem.qtip({
			content: template,
			style: 'colorpicker-tooltip',
			position: {
				my: 'right center',
				at: 'left center',
				viewport: $(window)
			},
			show: {
				event: 'click',
				effect: function(){
					cp.setHex(initialColor)
					qtipOpen = true
					okClicked = false
					$(this).show()
					onOpen()
				},
				delay: 0
			},
			hide: {
				fixed: true,
				event: 'unfocus',
				effect: function(){
					if(okClicked){
						initialColor = currentColor
						finishedCallback(currentColor)
					} else {
						cp.setHex(initialColor)
						finishedCallback()
					}

					qtipOpen = false
					onClose()
				},
				delay: 0
			}
		})

		return {
			reset: function(color){
				var hexColor = d3.rgb(color).toString()
				cp.setHex(hexColor)
				initialColor = hexColor
			}
		}
	}

	exports.colorpickerTooltip = colorpickerTooltip

})(this)