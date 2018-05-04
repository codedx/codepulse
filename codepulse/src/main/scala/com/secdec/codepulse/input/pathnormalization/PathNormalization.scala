/*
 * Copyright 2017 Secure Decisions, a division of Applied Visions, Inc.
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
package com.secdec.codepulse.input.pathnormalization

case class FilePath(name: String, parent: Option[FilePath]) {
	override def toString() = {
		parent match {
			case None => name
			case Some(p) => p + "/" + name
		}
	}
}

object FilePath {
	def apply(path: String): Option[FilePath] = {
		path.split(raw"\\|\/").filter(!_.isEmpty).foldLeft[Option[FilePath]](None)((prev, cur) => Some(FilePath(cur, prev)))
	}

	def apply(path: Option[String]): Option[FilePath] = {
		path match {
			case Some(p) => FilePath(p)
			case None => None
		}
	}
}

object PathNormalization {
	def isLocalizedSameAsAuthority(authority: FilePath, localized: FilePath): Boolean = {
		if(authority.name == localized.name) {
			authority.parent match {
				case None => true
				case Some(_) => (for {
					authorityParent <- authority.parent
					localizedParent <- localized.parent
				} yield {
					isLocalizedSameAsAuthority(authorityParent, localizedParent)
				}) getOrElse(false)
			}
		} else {
			false
		}
	}

	def isLocalizedInAuthorityPath(authority: FilePath, localized: FilePath): Boolean = {
		if(authority.name == localized.name) {
			localized.parent match {
				case None => true
				case Some(_) => (for {
					authorityParent <- authority.parent
					localizedParent <- localized.parent
				} yield {
					isLocalizedInAuthorityPath(authorityParent, localizedParent)
				}) getOrElse(false)
			}
		} else {
			false
		}
	}
}
