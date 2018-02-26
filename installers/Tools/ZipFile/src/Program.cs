using System;
using System.Reflection;

namespace ZipFile
{
    /// <summary>
    /// This program wraps ZipFile.CreateFromDirectory with an app.config that
    /// opts out of using a backslash in the paths of zip file entries.
    /// </summary>
    internal class Program
    {
        private static void Main(string[] args)
        {
            if (args == null || args.Length != 2)
            {
                Console.WriteLine($"Usage: {Assembly.GetExecutingAssembly().GetName().Name} <directory> <zipOutputFile>");
                Environment.Exit(1);
            }

            System.IO.Compression.ZipFile.CreateFromDirectory(args[0], args[1]);
        }
    }
}
