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

	$(document).ready(function(){

		function getTemplate(selector){
			return $('#NotificationsTemplates ' + selector).remove()
		}
		var $template = getTemplate('.notification'),
			$deletionMessageTemplate = getTemplate('.deletion-message'),
			$undeletionMessageTemplate = getTemplate('.undeletion-message')

		var $container = $('.notifications-visible')

		/*
		 * Notification class, for representing and managing instances of
		 * some predetermined messages that may be displayed at the top of
		 * the page when the server says so.
		 */
		function Notification(){
			var _this = this,
				$note = $template.clone(),
				$noteContent = $note.find('.notification-content'),
				$dismissal = $note.find('.notification-dismissal'),
				isShown = false,
				usesTransition = false,
				dismissTimeout = undefined,
				callbacks = {},
				dismissOpts = {manual: true},
				dismissHref = undefined,
				autoDismissWidget = undefined,
				autoDismissContainer = $note.find('.dismiss-countdown')[0]

			// clicking the dismissal button should clear the notification
			$dismissal.click(function(){
				onDismiss()
				exit()
			})

			function onDismiss(){
				if(dismissHref) $.ajax(dismissHref, {
					type: 'POST',
					error: function(){ console.log('dismiss failed on the server side') },
					success: function(){ console.log('dismissed server side notification') }
				})
			}

			/*
			 * Set `dismissable` to enable or disable the dissmissal button.
			 * Disabling the button will hide it completely.
			 */
			function setDismissable(dismissable){
				dismissOpts['manual'] = dismissable
				$dismissal.css('display', dismissable ? 'initial' : 'none')

				return _this
			}

			function setDismissHref(href){
				dismissHref = href
				return _this
			}

			/*
			 * Set an auto-dismiss delay
			 */
			function setAutoDismissDelay(delay){
				if(isNaN(delay)){
					delete dismissOpts['timeout']
					autoDismissWidget && autoDismissWidget.hide()
				} else {
					dismissOpts['timeout'] = delay
					if(!autoDismissWidget){
						autoDismissWidget = new PieClock(autoDismissContainer, 16)
					} else {
						autoDismissWidget.show()
					}
				}

				return _this
			}

			/*
			 * Calls the callback function that was registered via
			 * the `callbacks` object (constructor argument) with
			 * the given `name` field. If no callback was registered,
			 * it no-ops.
			 *
			 * The function will be called with a reference to this
			 * Notification as the first argument.
			 */
			function sendCallback(name){
				var cbs = callbacks[name] || []
				cbs.forEach(function(cb){ cb(_this) })
			}

			/*
			 * Add a callbck function (`f`) that will be called when an event
			 * with the corresponding `name` is sent.
			 */
			function addCallback(name, f){
				var list = callbacks[name] || (callbacks[name] = [])
				list.push(f)
				return _this
			}

			/*
			 * Turn transitions on or off.
			 */
			function setUsesTransition(uses){
				usesTransition = uses
				$note.toggleClass('slide', uses)
				return _this
			}

			/*
			 * Sets this Notification's message content as a deletion message,
			 * stating that the given project (by its `projectName`) has been deleted,
			 * providing an "undo" link that should be valid before the message
			 * is cleared.
			 * Sends an 'undo' event when the undo button is clicked, and an
			 * 'undone' event when the server acknowledges it.
			 */
			function setDeletionMessage(projectName, undoHref){
				var $msg = $deletionMessageTemplate.clone(),
					$projectName = $msg.find('.project-name'),
					$undoLink = $msg.find('.deletion-undo')

				$projectName.text(projectName)

				var undoClicked = false
				$undoLink.click(function(){
					if(undoClicked){
						return
					} else {
						undoClicked = true
					}
					sendCallback('undo')
					onDismiss()

					// Send a POST request to the given undoHref
					$.ajax(undoHref, {
						type: 'POST',
						success: function(){ console.log('undo worked') },
						error: function(){ console.log('undo did not work') },
						complete: function(){
							undoClicked = false
							sendCallback('undone')
							exit()
						}
					})
				})

				$noteContent.append($msg)

				return _this
			}

			function setUndeletionMessage(projectName){
				var $msg = $undeletionMessageTemplate.clone(),
					$projectName = $msg.find('.project-name')

				$projectName.text(projectName)
				$noteContent.append($msg)

				return _this
			}

			/*
			 * Adds the notification to the notification container.
			 * No effect if already added.
			 * Sends an 'enter' event.
			 */
			function enter(){
				if(isShown) return
				else isShown = true

				$note.prependTo($container)
				setTimeout(function(){
					$note.addClass('in')
					sendCallback('entered')
				}, 10)

				// if the 'timeout' dismissal option was set, set up
				// a timeout to auto-exit after the given delay time.
				clearTimeout(dismissTimeout)
				var timeoutTime = dismissOpts['timeout']
				if(!isNaN(timeoutTime)){
					dismissTimeout = setTimeout(exit, timeoutTime)
					autoDismissWidget && autoDismissWidget.animateCountdown(timeoutTime)
				}

				sendCallback('enter')

				return _this
			}

			/*
			 * Removes the notification from the notification container.
			 * No effect if already removed. If using transitions, the
			 * notification isn't actually removed until the transition
			 * finishes (300ms).
			 * Sends an 'exit' event.
			 */
			function exit(){
				if(!isShown) return

				$note.removeClass('in')
				if(usesTransition){
					setTimeout(function(){
						$note.remove()
						isShown = false
					}, 300)
				} else {
					$note.remove()
					isShown = false
				}

				// clear the dismissTimeout if there was one set
				clearTimeout(dismissTimeout)

				sendCallback('exit')

				return _this
			}

			/* Public API */
			_this.setUsesTransition = setUsesTransition
			_this.setDeletionMessage = setDeletionMessage
			_this.setUndeletionMessage = setUndeletionMessage
			_this.addCallback = addCallback
			_this.setDismissable = setDismissable
			_this.setAutoDismissDelay = setAutoDismissDelay
			_this.setDismissHref = setDismissHref
			_this.enter = enter
			_this.exit = exit
		}
		exports.Notification = Notification

		/*
		 * Defines a common point between <notification> elements that may
		 * be rendered to represent Notifications at page-load time, and
		 * objects that might be sent by the server at any time to represent
		 * incoming Notifications. Both types will specify parameters that
		 * will be used to construct the Notification instance.
		 *
		 * JQuery elements passed into this constructor will use the `attr`
		 * lookup method to get parameters. Regular objects will use regular
		 * field lookup notation (`obj[field]`) to get parameters.
		 */
		function NotificationParams(elemOrObject){
			if(elemOrObject.constructor == $){
				this.get = function(name){ return elemOrObject.attr(name) }
			} else {
				this.get = function(name){ return elemOrObject[name] }
			}
		}

		/*
		 * Creates a new Notification and opens it, based on the parameters specified in
		 * the given `NotificationParams` instance. An explanation of the parameters is
		 * as follows:
		 *
		 *  - `type` - Specifies the message type. Should be one of [deletion|undeletion] 
		 *    - if "deletion", the element should also have a "projectName" and "undoHref"
		 *      attribute set, specifying the same strings as with the `setDeletionMessage`
		 *      method.
		 *    - if "undeletion", the element should also have a "projectName" attribute
		 *      set, specifying the same string as with the `setUndeletionMessage` method.
		 *  - `dismissable` - Specifies if the notification can be manually dismissed by users.
		 *  - `usesTransition` - Specifies if the notification should use animations for its
		 *    enter and exit methods. Note that since the notification is being added at page
		 *    load time, the "enter" will not transition even if this attribute is set.
		 *  - `autoDismissDelay` - If set, specifies the number of milliseconds before the
		 *    notification is automatically dismissed.
		 */
		function createNewNotification(params, allowInitialTransition){
			var type = params.get('type'),
				dismissable = !!params.get('dismissable'),
				autoDismissDelay = parseInt(params.get('autoDismissDelay')),
				usesTransition = !!params.get('usesTransition')

			var note = new Notification()

			// Set the message content based on the `type` param
			switch(type){
			case 'deletion':
				var projectName = params.get('projectName'),
					undoHref = params.get('undoHref')
				note.setDeletionMessage(projectName, undoHref)
				break;
			case 'undeletion':
				var projectName = params.get('projectName')
				note.setUndeletionMessage(projectName)
				break;
			}

			// Set whether or not the notification can be manually dismissed
			note.setDismissable(dismissable)

			// Set the dismissal href
			var dismissHref = params.get('dismissHref')
			if(dismissHref) note.setDismissHref(dismissHref)

			// Set an optional auto-dismiss delay
			if(!isNaN(autoDismissDelay)){
				note.setAutoDismissDelay(autoDismissDelay)
			}

			// Set whether or not the notification transitions in and out.
			// Special case: if `allowInitialTransition` is false, we don't
			// actually set the property until after the notification is entered.
			if(allowInitialTransition){
				note.setUsesTransition(usesTransition)
			} else {
				note.addCallback('entered', function(n){
					n.setUsesTransition(usesTransition)
				})
			}

			// Tell the notification to pop up
			note.enter()

			return note
		}

		/*
		 * Find any <notification> elements that have been rendered with the page, 
		 * and use them to add the corresponding Notification widgets.
		 */
		$('#NotificationsQueued notification').each(function(){
			var params = new NotificationParams($(this).remove())
			createNewNotification(params, false)
		})

		/*
		 * Any time a `new-notification` event is triggered on the document,
		 * use the associated data to add a corresponding Notification widget.
		 */
		$(document).on('new-notification', function(event, data){
			var params = new NotificationParams(data)
			createNewNotification(params, true)
		})
	})

})(this)