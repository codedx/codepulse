using System;
using System.IO;

namespace CodePulse.Client.Queue
{
	public class NamedMemoryStream : MemoryStream
	{
		public NamedMemoryStream(int initialBufferCapacity)
			: base(initialBufferCapacity)
		{
		}

		public string Name { get; } = Guid.NewGuid().ToString();
	}
}
