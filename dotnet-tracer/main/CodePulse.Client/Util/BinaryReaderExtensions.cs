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
    public static class BinaryReaderExtensions
    {
        public static Guid ReadGuid(this BinaryReader reader)
        {
            if (reader == null)
            {
                throw new ArgumentNullException(nameof(reader), "Expected non-null BinaryReader");
            }
            return new Guid(reader.ReadBytes(16));
        }

        public static Guid ReadGuidBigEndian(this BinaryReader reader)
        {
            if (reader == null)
            {
                throw new ArgumentNullException(nameof(reader), "Expected non-null BinaryReader");
            }

            if (!BitConverter.IsLittleEndian)
            {
                return ReadGuid(reader);
            }

            var guidBytes = reader.ReadBytes(16);
            Array.Reverse(guidBytes, 0, 4); // big endian int (part a)
            Array.Reverse(guidBytes, 4, 2); // big endian short (part b)
            Array.Reverse(guidBytes, 6, 2); // big endian short (part c)
            return new Guid(guidBytes);
        }

        public static short ReadInt16BigEndian(this BinaryReader reader)
        {
            if (reader == null)
            {
                throw new ArgumentNullException(nameof(reader), "Expected non-null BinaryReader");
            }
            return !BitConverter.IsLittleEndian ? reader.ReadInt16() : BitConverter.ToInt16(ReadBytesBigEndian(reader, sizeof(short)), 0);
        }

        public static ushort ReadUInt16BigEndian(this BinaryReader reader)
        {
            if (reader == null)
            {
                throw new ArgumentNullException(nameof(reader), "Expected non-null BinaryReader");
            }
            return !BitConverter.IsLittleEndian ? reader.ReadUInt16() : BitConverter.ToUInt16(ReadBytesBigEndian(reader, sizeof(ushort)), 0);
        }

        public static int ReadInt32BigEndian(this BinaryReader reader)
        {
            if (reader == null)
            {
                throw new ArgumentNullException(nameof(reader), "Expected non-null BinaryReader");
            }
            return !BitConverter.IsLittleEndian ? reader.ReadInt32() : BitConverter.ToInt32(ReadBytesBigEndian(reader, sizeof(int)), 0);
        }

        public static string ReadUtfBigEndian(this BinaryReader reader)
        {
            if (reader == null)
            {
                throw new ArgumentNullException(nameof(reader), "Expected non-null BinaryReader");
            }

            var stringBytesLength = reader.ReadInt16BigEndian();
            var stringBytes = ReadBytesBigEndian(reader, stringBytesLength);

            Array.Reverse(stringBytes);
            return Encoding.UTF8.GetString(stringBytes);
        }

        private static byte[] ReadBytesBigEndian(BinaryReader reader, int byteCount)
        {
            var bytes = reader.ReadBytes(byteCount);
            Array.Reverse(bytes);
            return bytes;
        }
    }
}
