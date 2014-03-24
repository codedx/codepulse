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

/**
 * A "Set" collection class that provides some useful
 * functionality that JavaScript doesn't provide normally.
 * Only works for primitives like Number and String, so
 * no Objects can be added.
 */


!(function(exports){

	// function Set() {
	// 	this._innerMap = {}
	// 	this._size = 0

	// 	for(var i=0; i<arguments.length; ++i){
	// 		this.add(arguments[i])
	// 	}
	// }

	// Set.prototype.size = function(){ return this._size }

	// Set.prototype.add = function(v) {
	// 	if(!this._innerMap) console.log('wtf is this?', this)
	// 	if(v in this._innerMap) return false
	// 	else {
	// 		++this._size
	// 		this._innerMap[v] = true
	// 		return true
	// 	}
	// }
	
	// Set.prototype.remove = function(v) {
	// 	if(v in this._innerMap){
	// 		--this._size
	// 		delete this._innerMap[v]
	// 	}
	// }

	// Set.prototype.contains = function(v){ return v in this._innerMap }

	// Set.prototype.forEach = function(f){
	// 	for(var k in this._innerMap) f(k)
	// }

	// Set.prototype.toArray = function(){ return Object.keys(this._innerMap) }

	// Set.prototype.clear = function(){
	// 	this._innerMap = {}
	// 	this._size = 0
	// }

	Set.fromArray = function(arr){
		var s = new Set()
		arr.forEach(function(d){
			s.add(d)
		})
		return s
	}

	function Set(){
		var map = {}, size = 0, self = this

		this.forEach = function(f){
			for(var k in map) f(k)
		}

		this.contains = function(k){
			return k in map
		}

		this.add = function(k){
			if(k in map) return false
			else {
				map[k] = true
				++size
				return true
			}
		}

		this.remove = function(k){
			if(k in map) {
				delete map[k]
				--size
				return true
			}
			else return false
		}

		this.size = function(){ return size }

		this.toArray = function(){ return Object.keys(map) }

		this.clear = function(){
			map = {}
			size = 0
			return self
		}

		// add all constructor arguments to the set
		for(var i=0; i<arguments.length; ++i){
			this.add(arguments[i])
		}
	}
	
	// function Set_forEach(self){
	// 	return function(callback){
	// 		for(var k in self.map) callback(k)
	// 	}
	// }
	
	// function Set_contains(self){
	// 	return function(val){
	// 		return val in self.map
	// 	}
	// }
	
	// function Set_add(self){
	// 	return function(val){
	// 		if(val in self.map) return false
	// 		else {
	// 			self.map[val] = true
	// 			self.size++
	// 			return true
	// 		}
	// 	}
	// }
	
	// function Set_remove(self){
	// 	return function(val){
	// 		if(val in self.map) {
	// 			delete self.map[val]
	// 			self.size--
	// 			return true
	// 		}
	// 		else return false
	// 	}
	// }
	
	// function Set_size(self){
	// 	return function(){ return self.size }
	// }

	// function Set_toArray(self){
	// 	return function(){
	// 		return Object.keys(self.map)
	// 	}
	// }

	exports.Set = Set
	
}(this));