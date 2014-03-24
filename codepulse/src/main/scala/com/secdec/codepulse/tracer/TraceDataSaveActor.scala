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

package com.secdec.codepulse.tracer

import akka.actor.Actor
import collection.mutable.{ Map => MutableMap }
import akka.actor.Cancellable
import concurrent.duration._
import akka.actor.ActorSystem
import java.io.File
import akka.actor.Props
import net.liftweb.common.Loggable
import scala.concurrent.Promise
import scala.concurrent.Future

trait TraceDataSaver {
	def requestSave(id: TraceId, data: TraceData): Unit
	def requestLoad(id: TraceId): Future[TraceData]
	def requestRawLoad(id: TraceId): Future[Array[Byte]]
}

object TraceDataSaveActor {

	/** Sending this message to a `TraceDataSaveActor` will notify that actor
	  * that the given `data` wants to be saved. The actor will decide an
	  * appropriate time to save (to avoid frequent repeated saves) by applying
	  * a throttle and maximum wait time.
	  *
	  * The `id` is used to distinguish `data` from each other. Using the same
	  * data with different identifiers will result in two different save files
	  * being eventually generated.
	  */
	case class RequestSave(id: TraceId, data: TraceData)

	private case class PerformSave(id: TraceId, data: TraceData)

	case class RequestLoad(id: TraceId, dataPromise: Promise[TraceData])
	case class RequestRawLoad(id: TraceId, dataPromise: Promise[Array[Byte]])

	def apply(actorSystem: ActorSystem, saveManager: TraceDataSaveManager): TraceDataSaver = {
		new TraceDataSaverImpl(actorSystem, saveManager)
	}

	private class TraceDataSaverImpl(actorSystem: ActorSystem, saveManager: TraceDataSaveManager) extends TraceDataSaver {
		val actor = actorSystem.actorOf(Props(classOf[TraceDataSaveActor], saveManager))
		def requestSave(id: TraceId, data: TraceData) = {
			actor ! RequestSave(id, data)
		}
		def requestLoad(id: TraceId): Future[TraceData] = {
			val promise = Promise[TraceData]
			actor ! RequestLoad(id, promise)
			promise.future
		}
		def requestRawLoad(id: TraceId): Future[Array[Byte]] = {
			val promise = Promise[Array[Byte]]
			actor ! RequestRawLoad(id, promise)
			promise.future
		}
	}

}

class TraceDataSaveActor(saveManager: TraceDataSaveManager) extends Actor with Loggable {
	import TraceDataSaveActor._
	import context.dispatcher

	val throttleTime = 500.millis
	val timeoutTime = 5.seconds
	val scheduler = context.system.scheduler

	private val throttleMap = MutableMap.empty[TraceId, Cancellable]
	private val timeoutMap = MutableMap.empty[TraceId, Cancellable]

	def receive = {

		case RequestLoad(id, dataPromise) =>
			saveManager.load(id) match {
				case None => dataPromise.failure { new NoSuchElementException }
				case Some(data) => dataPromise.success(data)
			}

		case RequestRawLoad(id, dataPromise) =>
			val bytes = saveManager.loadRaw(id)
			dataPromise.success(bytes)

		/** Tells this actor to actually do the work of saving the given data,
		  * associated with the given id. As a side effect, this message will
		  * clear the throttle and timeout for any requests with this id.
		  */
		case PerformSave(id, data) =>
			saveManager.save(id, data)
			logger.info(s"Saved trace '$id'")

			throttleMap.remove(id) foreach { _.cancel() }
			timeoutMap.remove(id) foreach { _.cancel() }

		/** Requests a save operation to be performed with the given data,
		  * and associated with the given id. The save will be performed
		  * at least {`throttleTime`} after the latest save request with this
		  * id, and at most {`timeoutTime`} after an initial save request.
		  * These time limiters will be reset when the save is actually performed.
		  */
		case RequestSave(id, data) =>

			// cancel any task that was waiting to save this id.
			throttleMap.remove(id) match {

				/*
				 * If there was a pending save request, this request
				 * resets the throttle delay. The pending request is
				 * canceled, and will be rescheduled after this match
				 * block.
				 */
				case Some(task) => task.cancel()

				/*
				 * If there was no pending save request, schedule a task
				 * that will trigger the save after a long period. This
				 * task will not be canceled; it is used to force the
				 * throttling time to end, in case throttling goes on too long.
				 */
				case None =>
					val timeoutTask = scheduler.scheduleOnce(timeoutTime) {
						self ! PerformSave(id, data)
					}
					timeoutMap.put(id, timeoutTask)
			}

			// Reschedule a new task to save this data. If a new
			// RequestSave comes in with the same id, this task
			// will also be canceled and rescheduled.
			val newTask = scheduler.scheduleOnce(throttleTime) {
				self ! PerformSave(id, data)
			}
			throttleMap.put(id, newTask)
	}
}