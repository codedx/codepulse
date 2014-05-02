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

package com.secdec.codepulse.data.model.slick

import scala.slick.driver.JdbcProfile
import scala.slick.jdbc.{ StaticQuery => Q }

/** Some helpers for the Slick DAOs.
  *
  * @author robertf
  */
private[slick] trait SlickHelpers {
	val driver: JdbcProfile
	import driver.simple._

	/** Wrap thunk in DB calls to speed up large imports.
	  * Ideas here are from http://www.h2database.com/html/performance.html#fast_import
	  */
	def fastImport[T](thunk: => T)(implicit session: Session): T = {
		try {
			(Q updateNA "SET LOG 0; SET UNDO_LOG 0;").execute
			thunk
		} finally {
			(Q updateNA "SET LOG 2; SET UNDO_LOG 1;").execute
		}
	}
}