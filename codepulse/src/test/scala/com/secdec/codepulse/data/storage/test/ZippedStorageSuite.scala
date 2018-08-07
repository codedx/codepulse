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
package com.secdec.codepulse.data.storage.test

import java.util.zip.{ ZipEntry, ZipFile }

import com.secdec.codepulse.data.storage.ZippedStorage
import org.scalatest.FunSpec
import org.scalatest._
import org.scalatest.Matchers._

class ZippedStorageSuite extends FunSpec with Matchers {
//	val LOCK_ARCHIVE = ""
	val SINGLE_LEVEL_ARCHIVE = getClass.getResource("single-level.zip").getPath
	val MULTI_LEVEL_ARCHIVE = getClass.getResource("multi-level.zip").getPath
	val NESTED_ARCHIVE = getClass.getResource("nested.zip").getPath

	describe("A ZippedStorage") {
//		it("should not maintain a lock on the archive file") {
//
//		}

		it("should be able to get all non-nested entries") {
			val expectedEntries = List("1.a", "2.b", "3.c")
			val zip = new ZipFile(SINGLE_LEVEL_ARCHIVE)
			val storage = new ZippedStorage(zip)

			val entries = storage.getEntries(_ => true)
			zip.close()

			val sameSize = expectedEntries.size == entries.size
			val sameContent = expectedEntries.forall(entries.contains)

			(sameSize && sameContent) should equal(true)
		}

		it("should be able to get all non-nested entries that match criteria") {
			val expectedEntries = List("1.a", "3.c")
			val zip = new ZipFile(SINGLE_LEVEL_ARCHIVE)
			val storage = new ZippedStorage(zip)
			val criteria = (entry: ZipEntry) => entry.getName != "2.b"

			val entries = storage.getEntries(criteria)
			zip.close()

			val sameSize = expectedEntries.size == entries.size
			val sameContent = expectedEntries.forall(entries.contains)

			(sameSize && sameContent) should equal(true)
		}

		it("should be able to load the contents of a contained file") {
			val path = "1.a"
			val expectedContent = "This is 1.a"
			val zip = new ZipFile(SINGLE_LEVEL_ARCHIVE)
			val storage = new ZippedStorage(zip)

			val content = storage.loadEntry(path)
			zip.close()

			val sameContent = content match {
				case Some(c) => c == expectedContent
				case None => false
			}

			sameContent should equal(true)
		}

		it("should be able to load the contents of a nested file") {
			val path = "egg.zip;5.e"
			val expectedContent = "This is 5.e"
			val zip = new ZipFile(NESTED_ARCHIVE)
			val storage = new ZippedStorage(zip)

			val content = storage.loadEntry(path)
			zip.close()

			val sameContent = content match {
				case Some(c) => c == expectedContent
				case None => false
			}

			sameContent should equal(true)
		}

		it("should be able to read an entry for a given path") {
			val path = "dir1/4.d"
			val zip = new ZipFile(MULTI_LEVEL_ARCHIVE)
			val storage = new ZippedStorage(zip)

			val read = storage.readEntry(path) { stream =>
				Some(true)
			}
			zip.close()

			val readResult = read match {
				case Some(res) => res
				case None => false
			}

			readResult should equal(true)
		}

		it("should be able to read all first level entries") {
			val expectedEntries = List("1.a", "2.b", "3.c")
			val zip = new ZipFile(SINGLE_LEVEL_ARCHIVE)
			val storage = new ZippedStorage(zip)

			var entries = List.empty[String]
			storage.readEntries(false) { (filename, entryPath, entry, stream) =>
				entries = entry.getName :: entries
			}

			zip.close()

			val sameSize = expectedEntries.size == entries.size
			val sameContent = expectedEntries.forall(entries.contains)

			(sameSize && sameContent) should equal(true)
		}

		it("should be able to read all first level and nested directory entries") {
			val expectedEntries = List("1.a", "2.b", "3.c", "dir1/4.d", "dir1/5.e")
			val zip = new ZipFile(MULTI_LEVEL_ARCHIVE)
			val storage = new ZippedStorage(zip)

			var entries = List.empty[String]
			storage.readEntries() { (filename, entryPath, entry, stream) =>
				entries = entry.getName :: entries
			}

			zip.close()

			val sameSize = expectedEntries.size == entries.size
			val sameContent = expectedEntries.forall(entries.contains)

			(sameSize && sameContent) should equal(true)
		}

		it("should be able to read all first level, nested directory, and nested archive entries") {
			val expectedEntries = List("1.a", "2.b", "3.c", "dir1/4.d", "dir1/5.e", "4.d", "5.e")
			val zip = new ZipFile(NESTED_ARCHIVE)
			val storage = new ZippedStorage(zip)

			var entries = List.empty[String]
			storage.readEntries() { (filename, entryPath, entry, stream) =>
				entries = entry.getName :: entries
			}

			zip.close()

			val sameSize = expectedEntries.size == entries.size
			val sameContent = expectedEntries.forall(entries.contains)

			(sameSize && sameContent) should equal(true)
		}

		it("should be able to find an entry that matches criteria in the first level of entries") {
			val expectedEntry = "3.c"
			val zip = new ZipFile(SINGLE_LEVEL_ARCHIVE)
			val storage = new ZippedStorage(zip)

			val foundEntry = storage.find(false) { (filename, entryPath, entry, stream) =>
				entry.getName == expectedEntry
			}

			zip.close()

			foundEntry should equal (true)
		}

		it("should be able to find an entry that matches criteria in all levels and nestings of entries") {
			val expectedEntry = "5.e"
			val zip = new ZipFile(NESTED_ARCHIVE)
			val storage = new ZippedStorage(zip)

			val foundEntry = storage.find() { (filename, entryPath, entry, stream) =>
				entry.getName == expectedEntry
			}

			zip.close()

			foundEntry should equal (true)
		}
	}
}
