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

package com.secdec.codepulse.util

import scala.concurrent.duration.FiniteDuration

import akka.actor.ActorSystem
import akka.actor.Cancellable
import akka.actor.Props

/** Handles scheduling a task to happen every <interval>. If the task is performed
  * manually, then the interval is reset.
  *
  * @author robertf
  */
class TaskScheduler(task: => Unit, interval: FiniteDuration, system: ActorSystem) {
	import TaskScheduler._

	private lazy val actor = system.actorOf(Props { new SchedulerActor(task, interval) })

	private var started = false

	def start() { if (!started) { actor ! Start; started = true } }
	def stop(trigger: Boolean = false) { if (started) { actor ! Stop(trigger); started = false } }

	def trigger() { actor ! FireManually }
	def resetInterval() { actor ! ResetInterval }
}

object TaskScheduler {
	def apply(system: ActorSystem, interval: FiniteDuration)(task: => Unit) = new TaskScheduler(task, interval, system)

	private case object Start
	private case object Fire
	private case object FireManually
	private case object ResetInterval
	private case class Stop(triggerOnStop: Boolean)

	private class SchedulerActor(task: => Unit, interval: FiniteDuration) extends akka.actor.Actor {
		import context.dispatcher
		val scheduler = context.system.scheduler

		private var running = false
		private var scheduled = None: Option[Cancellable]
		private def schedule() = if (running) scheduled = Some(scheduler.scheduleOnce(interval, self, Fire))

		def receive = {
			case Start =>
				running = true
				if (!scheduled.isDefined) schedule

			case Stop(trigger) =>
				running = false
				val cancelled = scheduled.map(_.cancel) getOrElse true
				if (cancelled && trigger) self ! Fire

			case Fire =>
				task
				schedule

			case FireManually =>
				val cancelled = scheduled.map(_.cancel) getOrElse true
				if (running && cancelled) self ! Fire

			case ResetInterval =>
				val cancelled = scheduled.map(_.cancel) getOrElse true
				if (cancelled) schedule
		}
	}
}