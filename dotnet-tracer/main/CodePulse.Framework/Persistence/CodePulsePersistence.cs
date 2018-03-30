// Copyright 2018 Secure Decisions, a division of Applied Visions, Inc. 
// Permission is hereby granted, free of charge, to any person obtaining a copy of 
// this software and associated documentation files (the "Software"), to deal in the 
// Software without restriction, including without limitation the rights to use, copy, 
// modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, 
// and to permit persons to whom the Software is furnished to do so, subject to the 
// following conditions:
// 
// The above copyright notice and this permission notice shall be included in all copies 
// or substantial portions of the Software.
// 
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, 
// INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A 
// PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT 
// HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION 
// OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE 
// SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
//
// This material is based on research sponsored by the Department of Homeland
// Security (DHS) Science and Technology Directorate, Cyber Security Division
// (DHS S&T/CSD) via contract number HHSP233201600058C.

using System;
using System.Collections.Generic;
using System.Linq;
using CodePulse.Client.Agent;
using CodePulse.Client.Config;
using log4net;
using OpenCover.Framework;
using OpenCover.Framework.Communication;
using OpenCover.Framework.Model;
using OpenCover.Framework.Persistance;

namespace CodePulse.Framework.Persistence
{
    /// <summary>
    /// Persists data to Code Pulse application.
    /// </summary>
    public class CodePulsePersistence : BasePersistance
    {
        /// <summary>
        /// Logger used for this instance of BasePersistance.
        /// </summary>
        private readonly ILog _logger;

        /// <summary>
        /// Agent that connects to Code Pulse application.
        /// </summary>
        private ITraceAgent _agent;

        private DateTime _sendTimerExpiration;

        /// <inheritdoc />
        public CodePulsePersistence(ICommandLine commandLine, ILog logger)
            : base(commandLine, logger)
        {
            _logger = logger ?? throw new ArgumentNullException(nameof(logger));

            _sendTimerExpiration = commandLine.SendVisitPointsTimerInterval != 0 ?
                DateTime.UtcNow.AddMilliseconds(commandLine.SendVisitPointsTimerInterval) : DateTime.MaxValue;
        }

        /// <summary>
        /// Initializes Code Pulse agent using the specified configuration.
        /// </summary>
        /// <param name="configuration">Configuration details for agent initialization.</param>
        /// <returns>True if agent started and ready to send trace data to Code Pulse. False
        /// if the agent could not connect or prepare for communication with Code Pulse.
        /// </returns>
        public bool Initialize(StaticAgentConfiguration configuration)
        {
            if (configuration == null)
            {
                throw new ArgumentNullException(nameof(configuration));
            }

            return Initialize(new DefaultTraceAgent(configuration));
        }

        /// <summary>
        /// Initializes Code Pulse agent using the specified trace agent.
        /// </summary>
        /// <param name="traceAgent">Trace agent to use.</param>
        /// <returns>True if agent started and ready to send trace data to Code Pulse. False
        /// if the agent could not connect or prepare for communication with Code Pulse.
        /// </returns>
        public bool Initialize(ITraceAgent traceAgent)
        {
            if (_agent != null)
            {
                throw new InvalidOperationException("Agent is already initialized");
            }
            _agent = traceAgent;

            if (!_agent.Connect())
            {
                _logger.Error($"Cannot connect agent to Code Pulse at {_agent.StaticAgentConfiguration.HqHost} on port {_agent.StaticAgentConfiguration.HqPort}.");
                return false;
            }

            _agent.WaitForStart();

            if (!_agent.Prepare())
            {
                _logger.Error("Could not prepare to send data to Code Pulse");
                return false;
            }

            return true;
        }

        /// <summary>
        /// Blocks caller until shutdown occurs.
        /// </summary>
        public void WaitForShutdown()
        {
            if (_agent == null)
            {
                throw new InvalidOperationException("Agent has not been initialized.");
            }

            _agent.WaitForShutdown();
        }

        /// <inheritdoc />
        public override void Commit()
        {
            base.Commit();

            AddTraceData();

            _agent.Shutdown();
            _agent.WaitForShutdown();
        }

        /// <inheritdoc />
        public override void AdviseNoVisitData()
        {
            if (CommandLine.SendVisitPointsTimerInterval == 0)
            {
                return;
            }

            var now = DateTime.UtcNow;
            if (now < _sendTimerExpiration)
            {
                return;
            }
            _sendTimerExpiration = now.AddMilliseconds(CommandLine.SendVisitPointsTimerInterval);

            AddTraceData();
        }

        private void AddTraceData()
        {
            var contextIds = ContextSpidMap.Keys.ToArray();
            foreach (var contextId in contextIds)
            {
                if (!ContextSpidMap.TryGetValue(contextId, out HashSet<uint> relatedSpids))
                {
                    continue;
                }
                AddToTrace(relatedSpids);

                ContextSpidMap.Remove(contextId);
            }
        }

        /// <inheritdoc />
        protected override void OnContextEnd(Guid contextId, HashSet<uint> relatedSpids)
        {
            _logger.Debug($"Context {contextId} ended with a related spid count of {relatedSpids?.Count}.");

            AddToTrace(relatedSpids);
        }

        private void AddToTrace(IEnumerable<uint> relatedSpids)
        {
            foreach (var relatedSpid in relatedSpids)
            {
                if (relatedSpid == (uint)MSG_IdType.IT_VisitPointContextEnd)
                {
                    // ignore visit-point-context-end association with contextId
                    continue;
                }

                var startAndEndLineNumber = InstrumentationPoint.GetLineNumbers(relatedSpid);
                if (startAndEndLineNumber?.Item1 == null || startAndEndLineNumber.Item2 == null)
                {
                    continue;
                }

                var declaringMethod = InstrumentationPoint.GetDeclaringMethod(relatedSpid);
                if (declaringMethod == null)
                {
                    _logger.Error($"Cannot find declaring method for SPID {relatedSpid}.");
                    continue;
                }

                var methodContainingSpid = GetMethod(declaringMethod.DeclaringClass.DeclaringModule.ModulePath,
                    declaringMethod.MetadataToken, out var @class);
                if (methodContainingSpid == null)
                {
                    _logger.Error($"Cannot find method for SPID {relatedSpid} with token {declaringMethod.MetadataToken} in module {declaringMethod.DeclaringClass.DeclaringModule.ModulePath}.");
                    continue;
                }

                var filePath = "?";
                var firstFile = @class.Files.FirstOrDefault();
                if (firstFile != null)
                {
                    filePath = firstFile.FullPath;
                }

                var endLineNumber = startAndEndLineNumber.Item2.Value;

                _agent.TraceDataCollector.AddMethodVisit(@class.FullName, filePath,
                    declaringMethod.CallName,
                    declaringMethod.MethodSignature,
                    startAndEndLineNumber.Item1.Value,
                    endLineNumber);
            }
        }
    }
}

