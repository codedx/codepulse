/*
 * Copyright 2018 Secure Decisions, a division of Applied Visions, Inc.
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
 *
 * This material is based on research sponsored by the Department of Homeland
 * Security (DHS) Science and Technology Directorate, Cyber Security Division
 * (DHS S&T/CSD) via contract number HHSP233201600058C.
 */

package com.secdec.codepulse.input

import akka.actor.{Actor, Stash}
import com.codedx.codepulse.utility.Loggable
import com.secdec.codepulse.data.storage.Storage
import com.secdec.codepulse.events.GeneralEventBus
import com.secdec.codepulse.input.bytecode.ByteCodeProcessor
import com.secdec.codepulse.input.dotnet.DotNETProcessor
import com.secdec.codepulse.processing.{ProcessEnvelope, ProcessStatus}
import com.secdec.codepulse.processing.ProcessStatus.{DataInputAvailable, ProcessDataAvailable}

class InputFileProcessor(eventBus: GeneralEventBus) extends Actor with Stash with Loggable {

  import com.secdec.codepulse.util.Actor._

  val languageProcessors: List[LanguageProcessor] = new ByteCodeProcessor :: new DotNETProcessor :: Nil

  override def receive = {
    case ProcessEnvelope(_, DataInputAvailable(identifier, storage, treeNodeData, sourceData, post)) => {
      try {
        val languageProcessor = languageProcessors.find(x => x.canProcess(storage))
        if (languageProcessor.isDefined) {
          languageProcessor.get.process(storage, treeNodeData, sourceData)
          post()
          eventBus.publish(ProcessDataAvailable(identifier, storage, treeNodeData, sourceData))
        }
      } catch {
        case exception: Exception => eventBus.publish(ProcessStatus.asEnvelope(ProcessStatus.Failed(identifier, "InputFileProcessor", Some(exception))))
      }
    }

    case CanProcessFile(file) => {
      try {
        Storage(file) match {
          case Some(storage) => {
            val languageProcessor = languageProcessors.find(x => x.canProcess(storage))
            sender ! languageProcessor.isDefined
          }
          case _ => sender ! false
        }
      } catch {
        case ex: Exception => {
          logAndSendFailure(logger, "Unable to complete can-process test for input file", sender, ex)
        }
      }
    }
  }
}
