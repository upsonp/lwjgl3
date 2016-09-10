/*
 * Copyright LWJGL. All rights reserved.
 * License terms: https://www.lwjgl.org/license
 */
package org.lwjgl.demo.system.jawt;

import org.lwjgl.demo.opengl.AbstractGears;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import java.awt.*;
import java.util.concurrent.CountDownLatch;
import javax.swing.*;

import static org.lwjgl.demo.system.jawt.EmbeddedFrameUtil.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.*;

/** AWT integration demo using jawt. */
public final class EmbeddedFrameDemo {

	private EmbeddedFrameDemo() {
	}

	public static void main(String[] args) {
		GLFWErrorCallback.createPrint().set();
		if ( !glfwInit() )
			throw new IllegalStateException("Unable to initialize glfw");

		glfwDefaultWindowHints();
		glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
		glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
		glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, GLFW_TRUE);

		int WIDTH = 300;
		int HEIGHT = 300;

		long window = glfwCreateWindow(WIDTH, HEIGHT, "GLFW Gears Demo", NULL, NULL);
		if ( window == NULL )
			throw new RuntimeException("Failed to create the GLFW window");

		glfwMakeContextCurrent(window);
		GL.createCapabilities();

		AbstractGears gears = new AbstractGears();
		gears.initGLState();

		glfwSwapInterval(1);
		glfwShowWindow(window);

		Frame ef = embeddedFrameCreate(window);

		JPanel pane = new JPanel();
		pane.setLayout(new BorderLayout());
		pane.add(new JButton("JButton"), BorderLayout.CENTER);
		ef.add(pane);
		EventQueue.invokeLater(() -> embeddedFrameSetBounds(ef, 200, 268, 100, 32));

		glfwSetKeyCallback(window, (windowHnd, key, scancode, action, mods) -> {
			if ( action != GLFW_RELEASE )
				return;

			switch ( key ) {
				case GLFW_KEY_ESCAPE:
					glfwSetWindowShouldClose(windowHnd, true);
					break;
			}
		});

		glfwSetWindowSizeCallback(window, (windowHnd, width, height) -> {
			glViewport(0, 0, width, height);
			EventQueue.invokeLater(() -> embeddedFrameSetBounds(ef, width - 100, height - 32, 100, 32));
		});

		glfwSetWindowRefreshCallback(window, windowHnd -> {
			gears.renderLoop();
			glfwSwapBuffers(windowHnd);
		});

		glfwSetMouseButtonCallback(window, (windowHnd, button, action, mods) -> glfwFocusWindow(windowHnd));

		while ( !glfwWindowShouldClose(window) ) {
			glfwPollEvents();

			gears.renderLoop();

			glfwSwapBuffers(window);
		}

		CountDownLatch latch = new CountDownLatch(1);

		glfwFocusWindow(window); // deadlocks without this
		EventQueue.invokeLater(() -> {
			ef.dispose();
			latch.countDown();
		});

		try {
			latch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		glfwTerminate();
	}

}