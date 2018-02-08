package com.codedx.bytefrog.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;

import org.objectweb.asm.Type;
import com.esotericsoftware.minlog.Log;

/** Some helper utilities for dealing with class loader operations.
  *
  * @author robertf
  */
public class ClassLoaderUtil {
	// static
	private ClassLoaderUtil() {}

	/** Inject `type` into `classLoader` by defining it manually via reflection.
	  * @param classLoader the class loader to inject the class into
	  * @param type the typename to inject (should be reachable in the classpath)
	  * @returns true if the class was injected successfully
	  */
	public static boolean injectClass(ClassLoader classLoader, Type type) {
		byte[] clazz;

		if (Log.DEBUG) Log.debug("class injector", String.format("Attempting to inject '/%s.class' to %s", type.getInternalName(), classLoader));

		try (
			InputStream is = ClassLoaderUtil.class.getResourceAsStream(String.format("/%s.class", type.getInternalName()));
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
		) {
			byte[] buffer = new byte[0xFFFF];

			for (int len; (len = is.read(buffer)) != -1;)
				bos.write(buffer, 0, len);

			bos.flush();
			clazz = bos.toByteArray();
		} catch (IOException e) {
			Log.error("class injector", String.format("error loading %s; bailing", type.getClassName()), e);
			return false;
		}

		try {
			Method defineClass = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);
			defineClass.setAccessible(true);
			defineClass.invoke(classLoader, type.getClassName(), clazz, 0, clazz.length);
		} catch (NoSuchMethodException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			Log.error("class injector", "error injecting class to classloader; bailing", e);
			return false;
		}

		return true;
	}

	/** Checks if `className` is available in `classLoader`.
	  * @param classLoader the class loader to check
	  * @param className the class to check availability of
	  * @returns true if the class is reachable from the loader
	  */
	public static boolean isAvailable(ClassLoader classLoader, String className) {
		try {
			Class clazz = classLoader.loadClass(className);
			return true;
		} catch (ClassNotFoundException e) {
			if (Log.DEBUG) Log.debug("class checker", String.format("%s not available from %s", className, classLoader), e);
			return false;
		}
	}
}