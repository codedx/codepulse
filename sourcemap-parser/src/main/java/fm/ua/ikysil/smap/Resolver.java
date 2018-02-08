/*
 *                 Sun Public License Notice
 * 
 * This file is subject to the Sun Public License Version 
 * 1.0 (the "License"); you may not use this file except in compliance with 
 * the License. A copy of the License is available at http://www.sun.com/
 * 
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 * 
 * The Original Code is sourcemap Library.
 * The Initial Developer of the Original Code is Illya Kysil.
 * Portions created by the Initial Developer are Copyright (C) 2004
 * the Initial Developer. All Rights Reserved.
 * 
 * Alternatively, the Library may be used under the terms of either
 * the Mozilla Public License Version 1.1 or later (the "MPL"),
 * the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * (the "Alternative License"), in which case the provisions
 * of the respective Alternative License are applicable instead of those above.
 * If you wish to allow use of your version of this Library only under
 * the terms of an Alternative License, and not to allow others to use your
 * version of this Library under the terms of the License, indicate your decision by
 * deleting the provisions above and replace them with the notice and other
 * provisions required by the Alternative License. If you do not delete
 * the provisions above, a recipient may use your version of this Library under
 * the terms of any one of the SPL, the MPL, the GPL or the LGPL.
 */
/*
 * Resolver.java
 *
 * Created on April 30, 2004, 2:23 PM
 */

package fm.ua.ikysil.smap;

import java.io.*;
import java.util.*;

/**
 *
 * @author  Illya Kysil
 */
public class Resolver {

    /** Creates a new instance of Resolver */
    public Resolver() {
    }

    public SourceMap resolve(SourceMap sourceMap) {
        SourceMap result = (SourceMap) sourceMap.clone();
        for (Iterator esIter = result.getEmbeddedStratumList().iterator(); esIter.hasNext();) {
            EmbeddedStratum stratum = (EmbeddedStratum) esIter.next();
            Stratum outerStratum = result.getStratum(stratum.getName());
            if (outerStratum == null) {
                continue;
            }
            for (Iterator smIter = stratum.getSourceMapList().iterator(); smIter.hasNext();) {
                SourceMap embeddedSourceMap = (SourceMap) smIter.next();
                SourceMap resolvedEmbeddedSourceMap = resolve(embeddedSourceMap);
                String outerFileName = resolvedEmbeddedSourceMap.getOutputFileName();
                for (Iterator sIter = resolvedEmbeddedSourceMap.getStratumList().iterator(); sIter.hasNext();) {
                    Stratum embeddedStratum = (Stratum) sIter.next();
                    Stratum resolvedStratum = result.getStratum(embeddedStratum.getName());
                    if (resolvedStratum == null) {
                        resolvedStratum = new Stratum(embeddedStratum.getName());
                        result.getStratumList().add(resolvedStratum);
                    }
                    resolve(new Context(result, outerStratum, outerFileName, resolvedStratum, embeddedStratum));
                }
            }
        }
        result.getEmbeddedStratumList().clear();
        return result;
    }

    private void resolve(Context context) {
        for (Iterator iter = context.embeddedStratum.getLineInfoList().iterator(); iter.hasNext();) {
            LineInfo eli = (LineInfo) iter.next();
            resolve(context, eli);
        }
    }

    private void resolve(Context context, LineInfo eli) {
        if (eli.getRepeatCount() > 0) {
            for (Iterator iter = context.outerStratum.getLineInfoList().iterator(); iter.hasNext();) {
                LineInfo oli = (LineInfo) iter.next();
                if (!oli.getFileInfo().getInputFileName().equals(context.outerFileName)) {
                    continue;
                }
                if ((oli.getInputStartLine() <= eli.getOutputStartLine())
                        && (eli.getOutputStartLine() < oli.getInputStartLine() + oli.getRepeatCount())) {
                    int N = eli.getOutputStartLine() - oli.getInputStartLine();
                    int available = oli.getRepeatCount() - N;
                    int completeCount = Math.min((int) Math.floor((double) available / eli.getOutputLineIncrement()),
                            eli.getRepeatCount());
                    FileInfo fileInfo = context.resolvedStratum.getFileInfoList().getByPath(eli.getFileInfo().getInputFilePath());
                    if (fileInfo == null) {
                        fileInfo = (FileInfo) eli.getFileInfo().clone();
                        context.resolvedStratum.getFileInfoList().add(fileInfo);
                    }
                    if (completeCount > 0) {
                        LineInfo rli = new LineInfo(fileInfo, eli.getInputStartLine(),
                                completeCount, oli.getOutputStartLine() + N * oli.getOutputLineIncrement(),
                                eli.getOutputLineIncrement() * oli.getOutputLineIncrement());
                        context.resolvedStratum.getLineInfoList().add(rli);
                        LineInfo neli = new LineInfo(fileInfo, eli.getInputStartLine() + completeCount,
                                eli.getRepeatCount() - completeCount,
                                eli.getOutputStartLine() + completeCount * eli.getOutputLineIncrement(),
                                eli.getOutputLineIncrement());
                        resolve(context, neli);
                    }
                    else {
                        LineInfo rli = new LineInfo(fileInfo, eli.getInputStartLine(), 1,
                                oli.getOutputStartLine() + N * oli.getOutputLineIncrement(),
                                available);
                        context.resolvedStratum.getLineInfoList().add(rli);
                        LineInfo neli = new LineInfo(fileInfo, eli.getInputStartLine(), 1,
                                eli.getOutputStartLine() + available,
                                eli.getOutputLineIncrement() - available);
                        resolve(context, neli);
                        neli = new LineInfo(fileInfo, eli.getInputStartLine() + 1,
                                eli.getRepeatCount() - 1,
                                eli.getOutputStartLine() + eli.getOutputLineIncrement(),
                                eli.getOutputLineIncrement());
                        resolve(context, neli);
                    }
                }
            }
        }
    }

    private class Context {

        public SourceMap outerSourceMap;

        public Stratum outerStratum;

        public String outerFileName;

        public Stratum resolvedStratum;

        public Stratum embeddedStratum;

        public Context(SourceMap outerSourceMap, Stratum outerStratum, String outerFileName,
                Stratum resolvedStratum, Stratum embeddedStratum) {
            this.outerSourceMap = outerSourceMap;
            this.outerStratum = outerStratum;
            this.outerFileName = outerFileName;
            this.resolvedStratum = resolvedStratum;
            this.embeddedStratum = embeddedStratum;
        }

    }

    public Location resolve(SourceMap sourceMap, String stratumName, int lineNum) {
        SourceMap resolvedSourceMap = resolve(sourceMap);
        Stratum stratum = resolvedSourceMap.getStratum(stratumName);
        if (stratum == null) {
            return new Location(null, lineNum);
        }
        LineInfo bestFitLineInfo = null;
        int bestFitLineNum = lineNum;
        int bfOutputStartLine = Integer.MIN_VALUE;
        int bfOutputEndLine = Integer.MAX_VALUE;
        for (Iterator iter = stratum.getLineInfoList().iterator(); iter.hasNext();) {
            LineInfo lineInfo = (LineInfo) iter.next();
            for (int i = 0; i < lineInfo.getRepeatCount(); i++) {
                int outputStartLine = lineInfo.getOutputStartLine() + i * lineInfo.getOutputLineIncrement();
                int outputEndLine = Math.max(outputStartLine, outputStartLine + lineInfo.getOutputLineIncrement() - 1);
                if ((outputStartLine <= lineNum) && (lineNum <= outputEndLine)) {
                    if (lineInfo.getOutputLineIncrement() == 1) {
                        return new Location(lineInfo.getFileInfo(), lineInfo.getInputStartLine() + i);
                    }
                    else {
                        if ((bfOutputStartLine <= outputStartLine) && (outputEndLine <= bfOutputEndLine)) {
                            bestFitLineInfo = lineInfo;
                            bestFitLineNum = lineInfo.getInputStartLine() + i;
                            bfOutputStartLine = bestFitLineInfo.getOutputStartLine() + i * bestFitLineInfo.getOutputLineIncrement();
                            bfOutputEndLine = Math.max(bfOutputStartLine, bfOutputStartLine + bestFitLineInfo.getOutputLineIncrement() - 1);
                        }
                    }
                }
            }
        }
        if (bestFitLineInfo != null) {
            return new Location(bestFitLineInfo.getFileInfo(), bestFitLineNum);
        }
        return new Location(null, lineNum);
    }

    public static void main(String[] args) {
        String[] smapSrc = new String[] {
            "SMAP", "Hi.java", "Java",
            "*O Bar",
            "SMAP", "Hi.bar", "Java",
            "*S Foo",
            "*F", "1 Hi.foo",
            "*L", "1#1,5:1,2",
            "*E",
            "SMAP", "Incl.bar", "Java",
            "*S Foo",
            "*F", "1 Incl.foo",
            "*L", "1#1,2:1,2",
            "*E",
            "*C Bar",
            "*S Bar",
            "*F", "1 Hi.bar", "2 Incl.bar",
            "*L", "1#1:1", "1#2,4:2", "3#1,8:6",
            "*E"
        };
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < smapSrc.length; i++) {
            sb.append(smapSrc[i]).append("\n");
        }
        try {
            SourceMap[] sourceMaps = new Parser().parse(sb.toString());
            System.out.println("Original source map:");
            new Generator().generate(new OutputStreamWriter(System.out), sourceMaps[0]);
            System.out.println("");
            System.out.println("Resolved source map:");
            SourceMap resolvedMap = new Resolver().resolve(sourceMaps[0]);
            new Generator().generate(new OutputStreamWriter(System.out), resolvedMap);
        }
        catch (Exception e) {
            System.out.println("Exception: " + e);
            e.printStackTrace(System.out);
        }
    }

}
