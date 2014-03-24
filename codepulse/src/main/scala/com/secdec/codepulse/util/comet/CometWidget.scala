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

package com.secdec.codepulse.util.comet

import scala.xml.NodeSeq
import scala.xml.NodeSeq.seqToNodeSeq
import net.liftweb.common.{ Box, Full }
import net.liftweb.http.CometActor
import net.liftweb.util.Helpers
import net.liftweb.util.Helpers.intToTimeSpanBuilder
import reactive.{ EventSource, EventStream }
import net.liftweb.common.Empty

trait CometWidget[D, W <: CometWidget[D, W]] extends CometActor with PublicCometInit { self: W =>
	def companion: CometWidgetCompanion[D, W]

	override def lifespan = {
		import Helpers._
		Full(3.seconds)
	}

	/* Uncomment to change the render timeout (ms) */
	//	override def cometRenderTimeout = 1000 * 120

	/* Uncomment to change what gets rendered when the comet render times out */
	//	override def cometRenderTimeoutHandler = Full(<div class="alert alert-error">This component is taking too long to render</div>)

	private var _data: Option[D] = None
	def data: Option[D] = _data

	override def localSetup() {
		_data = companion.register(self)
	}
	override def localShutdown() {
		//println("Comet Widget Shutdown: " + self)
		companion.unregister(self)
	}
}

trait CometWidgetCompanion[D, W <: CometWidget[D, W]] {
	/** The className of the widget must be specified in order
	  * to generate the comet template. Since you can't (?)
	  * get the name of the class from the generic `W` via
	  * reflection, it must be hard-coded.
	  */
	def className: String

	private val data = collection.mutable.Map[String, D]()
	private val actors = collection.mutable.Map[String, W]()
	private val callbacks = collection.mutable.Map[String, W => Unit]()

	def register(actor: W): Option[D] =
		for (name <- actor.name.toOption; d <- data.get(name)) yield {
			actors += name -> actor

			// fulfill any promises of actors via callbacks
			for (cb <- callbacks.get(name)) cb(actor)
			callbacks.clear()

			// return the data
			d

		}

	def unregister(actor: W) =
		for (name <- actor.name) {
			actors -= name
			data -= name
		}

	def create(d: D, template: NodeSeq = Nil) = {
		val name = Helpers.nextFuncName

		data += name -> d
		val cometType = Helpers.snakify(className)
		val xml = <lift:comet type={ cometType } name={ name }>{ template }</lift:comet>
		name -> xml
	}

	def getActor(name: String): Option[W] = actors.get(name)
	def getActor(name: String, callback: W => Unit): Unit = getActor(name) match {
		case Some(actor) => callback(actor)
		case None => callbacks += name -> callback
	}
}

trait SubscribableEvents[E] {
	protected val events = new EventSource[E]
	val eventStream: EventStream[E] = events
}

trait SubscribableCometWidgetCompanion[E, D, W <: CometWidget[D, W] with SubscribableEvents[E]] extends CometWidgetCompanion[D, W] {
	private val waitingSubscribers = collection.mutable.Map[String, List[EventStream[E] => Unit]]().withDefault(_ => Nil)

	def setupSubscription(actorName: String)(sub: EventStream[E] => Unit) = getActor(actorName) match {
		case None => waitingSubscribers += actorName -> (sub :: waitingSubscribers(actorName))
		case Some(actor) => sub(actor.eventStream)
	}

	override def register(actor: W) = {
		for {
			name <- actor.name
			sub <- waitingSubscribers(name)
		} sub(actor.eventStream)
		super.register(actor)
	}

	override def unregister(actor: W) = {
		for (name <- actor.name) waitingSubscribers -= name
		super.unregister(actor)
	}
}