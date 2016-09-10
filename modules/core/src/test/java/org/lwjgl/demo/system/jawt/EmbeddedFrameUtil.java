/*
 * Copyright LWJGL. All rights reserved.
 * License terms: https://www.lwjgl.org/license
 */
package org.lwjgl.demo.system.jawt;

import org.lwjgl.BufferUtils;
import org.lwjgl.system.Platform;
import org.lwjgl.system.jawt.JAWT;
import org.lwjgl.system.macosx.ObjCRuntime;

import java.awt.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.lwjgl.glfw.GLFWNativeCocoa.*;
import static org.lwjgl.glfw.GLFWNativeWin32.*;
import static org.lwjgl.glfw.GLFWNativeX11.*;
import static org.lwjgl.system.JNI.*;
import static org.lwjgl.system.jawt.JAWTFunctions.*;
import static org.lwjgl.system.macosx.ObjCRuntime.*;

final class EmbeddedFrameUtil {

	private static final int JAVA_VERSION;

	private static final JAWT awt;

	static {
		Pattern p = Pattern.compile("^(?:1[.])?([1-9][0-9]*)[.]");
		Matcher m = p.matcher(System.getProperty("java.version"));

		if ( !m.find() )
			throw new IllegalStateException("Failed to parse java.version");

		JAVA_VERSION = Integer.parseInt(m.group(1));

		awt = JAWT.calloc();
		awt.version(JAVA_VERSION < 9 ? JAWT_VERSION_1_4 : JAWT_VERSION_9);
		if ( !JAWT_GetAWT(awt) )
			throw new RuntimeException("GetAWT failed");
	}

	private EmbeddedFrameUtil() {
	}

	static Frame embeddedFrameCreate(long window) {
		if ( JAVA_VERSION < 9 ) {
			String embeddedFrameImpl;
			switch ( Platform.get() ) {
				case LINUX:
					embeddedFrameImpl = "sun.awt.X11.XEmbeddedFrame";
					break;
				case MACOSX:
					embeddedFrameImpl = "sun.lwawt.macosx.CViewEmbeddedFrame";
					break;
				case WINDOWS:
					embeddedFrameImpl = "sun.awt.windows.WEmbeddedFrame";
					break;
				default:
					throw new IllegalStateException();
			}

			try {
				@SuppressWarnings("unchecked") Class<? extends Frame> EmdeddedFrame = (Class<? extends Frame>)Class.forName(embeddedFrameImpl);
				Constructor<? extends Frame> c = EmdeddedFrame.getConstructor(long.class);

				switch ( Platform.get() ) {
					case LINUX:
						return c.newInstance(glfwGetX11Window(window));
					case MACOSX:
						long cocoaWindow = glfwGetCocoaWindow(window);

						long objc_msgSend = ObjCRuntime.getLibrary().getFunctionAddress("objc_msgSend");
						long contentView = invokePPP(objc_msgSend, cocoaWindow, sel_getUid("contentView"));

						return c.newInstance(contentView);
					case WINDOWS:
						return c.newInstance(glfwGetWin32Window(window));
					default:
						throw new IllegalStateException();
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		} else {
			// TODO: implement
			return JAWT_CreateEmbeddedFrame(awt.CreateEmbeddedFrame(), BufferUtils.createByteBuffer(0));
		}
	}

	static void embeddedFrameSynthesizeWindowActivation(Frame embeddedFrame, boolean doActivate) {
		if ( JAVA_VERSION < 9 ) {
			try {
				embeddedFrame
					.getClass()
					.getMethod("synthesizeWindowActivation", boolean.class)
					.invoke(embeddedFrame, doActivate);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		} else {
			JAWT_SynthesizeWindowActivation(awt.SynthesizeWindowActivation(), embeddedFrame, doActivate);
		}
	}

	static void embeddedFrameSetBounds(Frame embeddedFrame, int x, int y, int width, int height) {
		if ( JAVA_VERSION < 9 ) {
			try {
				Method setLocationPrivate = embeddedFrame
					.getClass()
					.getSuperclass()
					.getDeclaredMethod("setBoundsPrivate", int.class, int.class, int.class, int.class);
				setLocationPrivate.setAccessible(true);
				setLocationPrivate.invoke(embeddedFrame, x, y, width, height);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		} else {
			JAWT_SetBounds(awt.SetBounds(), embeddedFrame, x, y, width, height);
		}
	}

}