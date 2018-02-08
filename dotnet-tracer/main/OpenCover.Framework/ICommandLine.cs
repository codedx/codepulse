//
// OpenCover - S Wilde
//
// This source code is released under the MIT License; see the accompanying license file.
//
// Modified work Copyright 2017 Secure Decisions, a division of Applied Visions, Inc.
//

using System.Collections.Generic;
using OpenCover.Framework.Model;

namespace OpenCover.Framework
{
    /// <summary>
    /// properties exposed by the command line object for use in other entities
    /// </summary>
    public interface ICommandLine
    {
        /// <summary>
        /// the target directory
        /// </summary>
        string TargetDir { get; }

        /// <summary>
        /// Alternate locations where PDBs can be found
        /// </summary>
        string[] SearchDirs { get; }

        /// <summary>
        /// Assemblies loaded form these dirs will be excluded
        /// </summary>
        string[] ExcludeDirs { get; }


        /// <summary>
        /// If specified then results to be merged by matching hash 
        /// </summary>
        bool MergeByHash { get; }

        /// <summary>
        /// Show the unvisited classes/methods at the end of the coverage run
        /// </summary>
        bool ShowUnvisited { get; }

        /// <summary>
        /// Hide skipped methods from the report
        /// </summary>
        List<SkippedMethod> HideSkipped { get; }

        /// <summary>
        /// Set the threshold i.e. max visit count reporting
        /// </summary>
        ulong Threshold { get; }

        /// <summary>
        /// Set when tracing coverage by test has been enabled
        /// </summary>
        bool TraceByTest { get; }

        /// <summary>
        /// Set when we should not use thread based buffers. 
        /// May not be as performant in some circumstances but avoids data loss
        /// </summary>
        bool SafeMode { get; }

        /// <summary>
        /// The type of profiler registration
        /// </summary>
        Registration Registration { get; }

        /// <summary>
        /// Should auto implemented properties be skipped
        /// </summary>
        bool SkipAutoImplementedProperties { get; }

        /// <summary>
        /// Sets the 'short' timeout between profiler and host 
        /// </summary>
        int CommunicationTimeout { get; }

        /// <summary>
        /// The number of msec between sends of the visit points to the host regardless of the number that has accumulated
        /// </summary>
        uint SendVisitPointsTimerInterval { get; }

        /// <summary>
        /// Filters are to use regular expressions rather than wild cards
        /// </summary>
        bool RegExFilters { get; }

        /// <summary>
        /// If specified then the default filters should not be applied
        /// </summary>
        bool NoDefaultFilters { get; }

        /// <summary>
        /// A list of filters
        /// </summary>
        List<string> Filters { get; }

        /// <summary>
        /// A list of attribute exclusion filters
        /// </summary>
        List<string> AttributeExclusionFilters { get; }

        /// <summary>
        /// A list of file exclusion filters
        /// </summary>
        List<string> FileExclusionFilters { get; }

        /// <summary>
        /// A list of test file filters
        /// </summary>
        List<string> TestFilters { get; }
    }
}