// dotnet-symbol-service
//
// Copyright (C) 2017 Applied Visions - http://securedecisions.avi.com
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

using System;
using System.Collections.Generic;
using System.Linq;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using System.IO;
using System.Runtime.CompilerServices;
using Mono.Cecil;
using Mono.Cecil.Pdb;
using SymbolService.Model;

namespace SymbolService.Controllers
{
    [Produces("application/json")]
    [Route("api/Methods")]
    public class MethodsController : Controller
    {
        [HttpPost]
        public IActionResult Methods(IFormFile assemblyFile, IFormFile symbolsFile)
        {
            try
            {
                if (assemblyFile == null || symbolsFile == null)
                {
                    return new BadRequestObjectResult($"You must specify two files with keys named {nameof(assemblyFile)} and {nameof(symbolsFile)}");
                }

                var paths = GenerateFilePaths();
                var assemblyPath = paths.Item1;
                var symbolsPath = paths.Item2;

                WriteFile(assemblyPath, assemblyFile);
                WriteFile(symbolsPath, symbolsFile);

                using (var module = LoadModule(assemblyPath, symbolsPath))
                {
                    var methodDefinitions = GetMethodDefinitions(module, out var methodInfoMap);

                    var stateMachineAttributeTypes = new List<string>(new []
                    {
                        typeof(AsyncStateMachineAttribute).FullName,
                        typeof(IteratorStateMachineAttribute).FullName
                    });

                    var stateMachineSupportedMethods =
                        methodDefinitions.Where(x => !x.DebugInformation.HasSequencePoints &&
                                                      x.HasCustomAttributes && x.CustomAttributes.Any(y =>
                                                      stateMachineAttributeTypes.Contains(y.AttributeType.FullName))).ToArray();

                    foreach (var stateMachineSupportedMethod in stateMachineSupportedMethods)
                    {
                        var stateMachineTypeDefinition = (TypeDefinition) stateMachineSupportedMethod.CustomAttributes
                            .Single(x => stateMachineAttributeTypes.Contains(x.AttributeType.FullName))
                            .ConstructorArguments.Single().Value;

                        foreach (var stateMachineMethod in stateMachineTypeDefinition.Methods.Where(x => x.DebugInformation.HasSequencePoints))
                        {
                            if (!methodInfoMap.TryGetValue(stateMachineSupportedMethod.FullName, out var stateMachineSupportedMethodInfo))
                            {
                                stateMachineSupportedMethodInfo = GetMethodInfo(stateMachineSupportedMethod);
                                methodInfoMap[stateMachineSupportedMethod.FullName] = stateMachineSupportedMethodInfo;
                            }

                            methodInfoMap[stateMachineMethod.FullName].SurrogateFor = methodInfoMap[stateMachineSupportedMethod.FullName].Id;
                        }
                    }

                    return new JsonResult(methodInfoMap.Values);
                }
            }
            catch
            {
                return new StatusCodeResult(StatusCodes.Status500InternalServerError);
            }
        }

        private Tuple<string, string> GenerateFilePaths()
        {
            var tempFolder = Path.GetTempPath();
            var randomFile = Path.GetRandomFileName();
            var assemblyPath = $"assembly{randomFile}";
            var symbolsPath = $"symbols{randomFile}";

            return new Tuple<string, string>(Path.Combine(tempFolder, assemblyPath), Path.Combine(tempFolder, symbolsPath));
        }

        private void WriteFile(string filePath, IFormFile file)
        {
            using (var stream = new FileStream(filePath, FileMode.Create))
            {
                file.CopyTo(stream);
            }
        }

        private ModuleDefinition LoadModule(string assemblyPath, string symbolsPath)
        {
            using (var symbolsStream = new FileStream(symbolsPath, FileMode.Open))
            {
                var parameters = new ReaderParameters(ReadingMode.Deferred)
                {
                    ReadSymbols = true,
                    SymbolStream = symbolsStream,
                    SymbolReaderProvider = new PdbReaderProvider() // avoids NotSupportedException in 0.10.0-beta7
                };

                return ModuleDefinition.ReadModule(assemblyPath, parameters);
            }
        }

        private List<MethodDefinition> GetMethodDefinitions(ModuleDefinition module, out Dictionary<string, MethodInfo> methodInfoMap)
        {
            var allMethodDefinitions = new List<MethodDefinition>();
            methodInfoMap = new Dictionary<string, MethodInfo>();
            foreach (var type in module.Types)
            {
                allMethodDefinitions.AddRange(GetMethodDefinitions(type, ref methodInfoMap));
            }
            return allMethodDefinitions;
        }

        private IEnumerable<MethodDefinition> GetMethodDefinitions(TypeDefinition typeDefinition, ref Dictionary<string, MethodInfo> methodInfoMap)
        {
            var definitions = new List<MethodDefinition>();
            foreach (var type in typeDefinition.NestedTypes)
            {
                definitions.AddRange(GetMethodDefinitions(type, ref methodInfoMap));
            }

            foreach (var method in typeDefinition.Methods)
            {
                definitions.Add(method);

                if (method.DebugInformation.HasSequencePoints)
                {
                    methodInfoMap[method.FullName] = GetMethodInfo(method);
                }
            }

            return definitions;
        }

        private MethodInfo GetMethodInfo(MethodDefinition method)
        {
            return new MethodInfo
            {
                FullyQualifiedName = method.Name,
                ContainingClass = method.DeclaringType?.FullName,
                AccessModifiers = GetAccessModifiers(method),
                Parameters = GetParameters(method),
                ReturnType = method.ReturnType.FullName,
                Instructions = method.Body?.Instructions?.Count ?? 0
            };
        }

        static readonly Dictionary<Modifier, Func<MethodDefinition, Modifier>> AccessModiferQueries = new Dictionary<Modifier, Func<MethodDefinition, Modifier>>()
        {
            { Modifier.PUBLIC, (method) => method.IsPublic ? Modifier.PUBLIC : 0 },
            { Modifier.PRIVATE, (method) => method.IsPrivate ? Modifier.PRIVATE : 0 },
            { Modifier.ABSTRACT, (method) => method.IsAbstract ? Modifier.ABSTRACT : 0 },
            { Modifier.STATIC, (method) => method.IsStatic ? Modifier.STATIC : 0 },
            { Modifier.SYNCHRONIZED, (method) => method.IsSynchronized ? Modifier.SYNCHRONIZED : 0 },
            { Modifier.FINAL, (method) => method.IsFinal ? Modifier.FINAL : 0 },
            { Modifier.PROTECTED, (method) => method.IsFamily ? Modifier.PROTECTED : 0 }
        };

        private int GetAccessModifiers(MethodDefinition method)
        {
            return AccessModiferQueries.Values.Select(getModifier => getModifier(method))
                .Aggregate(0, (previous, next) => previous | (int)next);
        }

        private List<String> GetParameters(MethodDefinition method)
        {
            return method.Parameters.Select(parameter => parameter.ParameterType.FullName).ToList();
        }
    }
}