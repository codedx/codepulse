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

package com.secdec.codepulse.components.notifications

import scala.xml.Elem
import scala.xml.MetaData
import scala.xml.NodeSeq
import scala.xml.NodeSeq.seqToNodeSeq
import scala.xml.Null
import scala.xml.TopScope
import scala.xml.UnprefixedAttribute
import com.secdec.codepulse.util.comet.PublicCometInit
import net.liftweb.http.CometActor
import net.liftweb.http.js.JE.JsFunc
import net.liftweb.http.js.JE.JsVar
import net.liftweb.http.js.JsCmd
import net.liftweb.http.js.JsCmds.jsExpToJsCmd
import net.liftweb.http.js.JsExp.jValueToJsExp
import net.liftweb.http.js.JsExp.strToJsExp
import net.liftweb.http.js.jquery.JqJE.Jq
import net.liftweb.json.JsonAST.JField
import net.liftweb.json.JsonAST.JObject
import net.liftweb.json.JsonAST.JString
import net.liftweb.http.DispatchSnippet
import com.codedx.codepulse.utility.Loggable

/** Entry point for sending notifications from the server to the front end
  * for use with the Notification.js script. The main public method to use
  * is `enqueueNotification`, which sends a new message and optionally saves
  * it for a limited time so that it can be seen on a newly-loaded page.
  */
object Notifications extends CometActor with PublicCometInit with DispatchSnippet with Loggable {

	case class NotificationId(num: Int) extends AnyVal
	private val notificationIdGenerator = Iterator.from(0).map { NotificationId(_) }

	private case class Notification(id: NotificationId,
		message: NotificationMessage,
		dismissable: Boolean,
		usesTransition: Boolean,
		autoDismissTime: Option[Long])

	/* don't modify this directly; use readQueue and modifyQueue */
	private type Q = List[Notification]
	private var queue: Q = List.empty[Notification]
	private val queueLock = new Object {}

	private def readQueue[A](f: Q => A): A = queueLock.synchronized {
		f(queue)
	}
	private def modifyQueue(f: Q => Q): Q = queueLock.synchronized {
		queue = f(queue)
		queue
	}

	def enqueueNotification(
		message: NotificationMessage,
		settings: NotificationSettings = NotificationSettings.default,
		persist: Boolean = false) = {

		// the optional dismiss time = 'now' + the optional delay time
		val autoDismissTime = settings.autoDismissDelay map { delayMs =>
			System.currentTimeMillis + delayMs
		}

		val noteId = notificationIdGenerator.next
		val note = Notification(noteId, message, settings.dismissable, settings.usesTransition, autoDismissTime)

		// add the new note to the queue if the `persist` argument was true
		if (persist) modifyQueue { q => q :+ note }

		// send the notification to the front end immediately
		sendNotification(note)
	}

	def dismissNotification(noteId: NotificationId): Unit = {
		modifyQueue { q =>
			logger.debug(s"Dismiss notification $noteId")
			q.filterNot { _.id == noteId }
		}
	}

	// CometActor.render
	// This is only called once per 'tab' by the lift framework, so the actual
	// rendering needs to go in the dispatch snippet.
	def render = Nil

	// DispatchSnippet.dispatch
	def dispatch = {
		case "persistentNotifications" => { (ignored: NodeSeq) =>
			Notifications.renderQueue
		}
	}

	def renderQueue: NodeSeq = {
		val q = pruneQueue()
		val elems = q map { renderNotificationHtml }
		elems
	}

	private def sendNotification(note: Notification) = {
		val json = renderNotificationJson(note)
		val cmd: JsCmd = Jq(JsVar("document")) ~> JsFunc("trigger", "new-notification", json)
		partialUpdate { cmd }
	}

	/** Modifies the queue, removing any notifications that should have been auto-dismissed.
	  * Returns the modified version.
	  */
	private def pruneQueue(): Q = modifyQueue { q =>
		val now = System.currentTimeMillis
		q filter { note =>
			// only keep notifications whose autoDismissTime is in the future or unspecified
			note.autoDismissTime match {
				case None => true
				case Some(time) => time > now
			}
		}
	}

	/** Builds a list of key->value pairs representing attributes of the given
	  * `note` that can be read by the front end. This list will be built into
	  * an HTML element for page-load rendering, and a JSON object for on-the-fly
	  * notifications.
	  */
	private def getNotificationAttributes(note: Notification): List[(String, String)] = {
		val attribs = List.newBuilder[(String, String)]

		// attributes for the message itself
		note.message match {
			case NotificationMessage.ProjectDeletion(projectName, undoHref) =>
				attribs += "type" -> "deletion"
				attribs += "projectName" -> projectName
				attribs += "undoHref" -> undoHref

			case NotificationMessage.ProjectUndeletion(projectName) =>
				attribs += "type" -> "undeletion"
				attribs += "projectName" -> projectName

			case NotificationMessage.AgentDisconnected =>
				attribs += "type" -> "agent-disconnected"
		}

		// the optional 'autoDismissDelay' attribute
		for (dismissTime <- note.autoDismissTime) {
			val now = System.currentTimeMillis
			val delay = dismissTime - now
			logger.debug(s"Getting autoDismissDelay. now=$now, dismissTime=$dismissTime, delay=$delay")
			if (delay > 0) {
				attribs += "autoDismissDelay" -> delay.toString
			}
		}

		// the 'usesTransition' attribute
		if (note.usesTransition) {
			attribs += "usesTransition" -> "usesTransition"
		}

		// the 'dismissable' attribute
		if (note.dismissable) {
			attribs += "dismissable" -> "dismissable"

			// add 'dismissHref' for manual dismissal
			import com.secdec.codepulse.tracer.apiServer
			val dismissHref = apiServer.Paths.DismissNotification.toHref(note.id)
			attribs += "dismissHref" -> dismissHref
		}

		attribs.result
	}

	/** Build an HTML representation of the given `note`, using attributes
	  * from `getNotificationAttributes`.
	  */
	private def renderNotificationHtml(note: Notification): Elem = {

		val attribs = getNotificationAttributes(note)

		// build the `attribs` buffer into a list of MetaDatas
		val metadata = attribs.foldRight[MetaData](Null) {
			case ((key, value), tail) => new UnprefixedAttribute(key, value, tail)
		}

		// create a <notification> element with the calculated attributes
		Elem(null, "notification", metadata, TopScope, false)
	}

	/** Build a JSON representation of the given `note`, using attributes
	  * from `getNotificationAttributes`.
	  */
	private def renderNotificationJson(note: Notification): JObject = {
		val fields = getNotificationAttributes(note) map {
			case (key, value) => JField(key, JString(value))
		}
		JObject(fields)
	}

}