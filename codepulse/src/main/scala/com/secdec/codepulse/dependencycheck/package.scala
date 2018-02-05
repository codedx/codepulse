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

package com.secdec.codepulse

import akka.actor.{ ActorRef, ActorSystem, Props }
import com.secdec.codepulse.components.dependencycheck.Updates
import com.secdec.codepulse.events.GeneralEventBus
import com.secdec.codepulse.input.dependencycheck.DependencyCheckPostProcessor
import com.secdec.codepulse.processing.ProcessStatus

package object dependencycheck {

	class BootVar[T] {
		private var _value: Option[T] = None
		def apply() = _value getOrElse {
			throw new IllegalStateException("depCode Pulse has not booted yet")
		}
		private[dependencycheck] def set(value: T) = {
			_value = Some(value)
		}
	}

	val dependencyCheckActor = new BootVar[ActorRef]

	def boot(actorSystem: ActorSystem, eventBus: GeneralEventBus) {
		val dca = actorSystem actorOf Props[DependencyCheckActor]
		actorSystem.eventStream.subscribe(dca, classOf[ProcessStatus])
		dca ! DependencyCheckActor.Update
		dependencyCheckActor set dca
//		val dependencyCheckPostProcessor = actorSystem actorOf Props[DependencyCheckPostProcessor]
//		eventBus.subscribe(dependencyCheckPostProcessor, ProcessStatus.ProcessDataAvailable.getClass.getSimpleName)

		val updates = actorSystem actorOf Props[Updates]
		eventBus.subscribe(updates, "Queued")
		eventBus.subscribe(updates, "Running")
		eventBus.subscribe(updates, "Finished")
		eventBus.subscribe(updates, "Failed")
		eventBus.subscribe(updates, "NotRun")
		eventBus.subscribe(updates, "Unknown")
	}
}