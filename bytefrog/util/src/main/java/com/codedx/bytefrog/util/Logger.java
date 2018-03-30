package com.codedx.bytefrog.util;

import java.io.File;
import java.io.IOException;
import java.io.FileWriter;

import com.esotericsoftware.minlog.Log;

public class Logger extends Log.Logger {
	private FileWriter out;
	private final String lineSeperator = System.lineSeparator();

	public Logger(File file) {
		try {
			out = new FileWriter(file, true);
		} catch (IOException e) {
			System.err.println(String.format("[bytefrog] cannot open %s for logging:", file));
			e.printStackTrace();
			out = null;
		}
	}

	@Override protected void print(String message) {
		if (out != null) {
			try {
				synchronized(out) {
					out.write(message);
					out.write(lineSeperator);
					out.flush();
				}
			} catch (IOException e) {
				System.err.println("[bytefrog] failure writing to log file:");
				e.printStackTrace();
				out = null;
			}
		}
	}
}