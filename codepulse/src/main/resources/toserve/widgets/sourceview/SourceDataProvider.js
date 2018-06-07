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

function SourceDataProvider(sourceFile) {
    let _sourcePromise = null

    return {
        loadSource: function(clearCache){
            if(clearCache) _sourcePromise = null

            if(!_sourcePromise){
                _sourcePromise = new Promise((resolve, reject) => {
                    let mode = CodeMirror.findModeByFileName(sourceFile.path)
                    mode = mode && mode.mime

                    API.getSource(sourceFile.id, (source, err) => {
                        if(err) reject(err)
                        else resolve({ mode, source })
                    })

                    // let sourceUrl = cdx.xapiUrl(`/projects/${this._projectId}/files/by-input/${this._inputPath.id}`)
                    // cdx.get(sourceUrl, (err, source) => {
                    //     if(err) reject(err)
                    //     else resolve({ mode, source })
                    // })
                })
            }

            return _sourcePromise
        }
    }
}