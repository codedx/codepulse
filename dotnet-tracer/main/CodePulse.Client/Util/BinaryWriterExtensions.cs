// Copyright 2017 Secure Decisions, a division of Applied Visions, Inc. 
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
using System.IO;
using System.Text;

namespace CodePulse.Client.Util
{
    public static class BinaryWriterExtensions
    {
        public static void FlushAndLog(this BinaryWriter writer, string description)
        {
            writer.Flush();
        }

        public static void WriteBigEndian(this BinaryWriter writer, int value)
        {
            if (writer == null)
            {
                throw new ArgumentNullException(nameof(writer), "Expected non-null BinaryWriter");
            }

            if (!BitConverter.IsLittleEndian)
            {
                writer.Write(value);
                return;
            }
            WriteBytesBigEndian(writer, BitConverter.GetBytes(value));
        }

        public static void WriteBigEndian(this BinaryWriter writer, long value)
        {
            if (writer == null)
            {
                throw new ArgumentNullException(nameof(writer), "Expected non-null BinaryWriter");
            }

            if (!BitConverter.IsLittleEndian)
            {
                writer.Write(value);
                return;
            }
            WriteBytesBigEndian(writer, BitConverter.GetBytes(value));
        }

        public static void WriteUtfBigEndian(this BinaryWriter writer, string value)
        {
            if (writer == null)
            {
                throw new ArgumentNullException(nameof(writer), "Expected non-null BinaryWriter");
            }

            var bytes = Encoding.UTF8.GetBytes(value);

            writer.WriteBigEndian(Convert.ToInt16(bytes.Length));
            writer.Write(bytes);
        }

        public static void WriteBigEndian(this BinaryWriter writer, short value)
        {
            if (writer == null)
            {
                throw new ArgumentNullException(nameof(writer), "Expected non-null BinaryWriter");
            }

            if (!BitConverter.IsLittleEndian)
            {
                writer.Write(value);
                return;
            }
            WriteBytesBigEndian(writer, BitConverter.GetBytes(value));
        }

        public static void WriteBigEndian(this BinaryWriter writer, ushort value)
        {
            if (writer == null)
            {
                throw new ArgumentNullException(nameof(writer), "Expected non-null BinaryWriter");
            }

            if (!BitConverter.IsLittleEndian)
            {
                writer.Write(value);
                return;
            }
            WriteBytesBigEndian(writer, BitConverter.GetBytes(value));
        }

        private static void WriteBytesBigEndian(BinaryWriter writer, byte[] bytes)
        {
            Array.Reverse(bytes);
            writer.Write(bytes);
        }
    }
}
