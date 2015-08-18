/*
 * Copyright LWJGL. All rights reserved.
 * License terms: http://lwjgl.org/license.php
 */
package org.lwjgl.system;

import org.lwjgl.LWJGLUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.*;

import sun.misc.Unsafe;
import sun.nio.ch.DirectBuffer;

import static org.lwjgl.system.MemoryUtil.*;

/**
 * Provides 3 MemoryAccessor implementations. The fastest available will be used by MemoryUtil.
 * <p/>
 * On non-Oracle VMs, Unsafe should be the fastest implementation as well. In the absence of Unsafe, performance will depend on how reflection and JNI are
 * implemented. For now we'll go with what we see on the Oracle VM (that is, we'll prefer reflection over JNI).
 */
final class MemoryAccess {

	private MemoryAccess() {
	}

	static MemoryAccessor getInstance() {
		MemoryAccessor accessor;
		try {
			// Depends on java.nio.Buffer#address and sun.misc.Unsafe
			accessor = loadAccessor("org.lwjgl.system.MemoryAccess$MemoryAccessorUnsafe");
		} catch (Exception e0) {
			try {
				// Depends on java.nio.Buffer#address
				accessor = new MemoryAccessorReflect();
			} catch (Exception e1) {
				LWJGLUtil.log("Unsupported JVM detected, this will likely result in low performance. Please inform LWJGL developers.");
				accessor = new MemoryAccessorJNI();
			}
		}

		return accessor;
	}

	private static MemoryAccessor loadAccessor(String className) throws Exception {
		return (MemoryAccessor)Class.forName(className).newInstance();
	}

	abstract static class MemoryAccessor {

		int getPageSize() {
			// TODO: Can we do better?
			return 4096;
		}

		int getCacheLineSize() {
			// TODO: Can we do better?
			return 64;
		}

		abstract long getAddress(Buffer buffer);

		abstract ByteBuffer newByteBuffer(long address, int capacity);

		final ShortBuffer newShortBuffer(long address, int capacity) { return newByteBuffer(address, capacity << 1).asShortBuffer(); }

		final CharBuffer newCharBuffer(long address, int capacity) { return newByteBuffer(address, capacity << 1).asCharBuffer(); }

		final IntBuffer newIntBuffer(long address, int capacity) { return newByteBuffer(address, capacity << 2).asIntBuffer(); }

		final LongBuffer newLongBuffer(long address, int capacity) { return newByteBuffer(address, capacity << 3).asLongBuffer(); }

		final FloatBuffer newFloatBuffer(long address, int capacity) { return newByteBuffer(address, capacity << 2).asFloatBuffer(); }

		final DoubleBuffer newDoubleBuffer(long address, int capacity) { return newByteBuffer(address, capacity << 3).asDoubleBuffer(); }

		abstract ByteBuffer setupBuffer(ByteBuffer buffer, long address, int capacity);

		abstract ShortBuffer setupBuffer(ShortBuffer buffer, long address, int capacity);

		abstract CharBuffer setupBuffer(CharBuffer buffer, long address, int capacity);

		abstract IntBuffer setupBuffer(IntBuffer buffer, long address, int capacity);

		abstract LongBuffer setupBuffer(LongBuffer buffer, long address, int capacity);

		abstract FloatBuffer setupBuffer(FloatBuffer buffer, long address, int capacity);

		abstract DoubleBuffer setupBuffer(DoubleBuffer buffer, long address, int capacity);

		void memSet(long dst, int value, int bytes) { nMemSet(dst, value, bytes); }

		void memCopy(long src, long dst, int bytes) {
			nMemCopy(dst, src, bytes); // Note the swapped src & dst
		}

		byte memGetByte(long ptr) { return nMemGetByte(ptr); }

		short memGetShort(long ptr) { return nMemGetShort(ptr); }

		int memGetInt(long ptr) { return nMemGetInt(ptr); }

		long memGetLong(long ptr) { return nMemGetLong(ptr); }

		float memGetFloat(long ptr) { return nMemGetFloat(ptr); }

		double memGetDouble(long ptr) { return nMemGetDouble(ptr); }

		long memGetAddress(long ptr) { return nMemGetAddress(ptr); }

		void memPutByte(long ptr, byte value) { nMemPutByte(ptr, value); }

		void memPutShort(long ptr, short value) { nMemPutShort(ptr, value); }

		void memPutInt(long ptr, int value) { nMemPutInt(ptr, value); }

		void memPutLong(long ptr, long value) { nMemPutLong(ptr, value); }

		void memPutFloat(long ptr, float value) { nMemPutFloat(ptr, value); }

		void memPutDouble(long ptr, double value) { nMemPutDouble(ptr, value); }

		void memPutAddress(long ptr, long value) { nMemPutAddress(ptr, value); }

	}

	/** Default implementation, using JNI. */
	private static final class MemoryAccessorJNI extends MemoryAccessor {

		@Override
		long getAddress(Buffer buffer) {
			return nGetAddress(buffer);
		}

		@Override
		ByteBuffer newByteBuffer(long address, int capacity) {
			return nNewBuffer(address, capacity).order(ByteOrder.nativeOrder());
		}

		@Override
		ByteBuffer setupBuffer(ByteBuffer buffer, long address, int capacity) { return newByteBuffer(address, capacity); }

		@Override
		ShortBuffer setupBuffer(ShortBuffer buffer, long address, int capacity) { return newShortBuffer(address, capacity); }

		@Override
		CharBuffer setupBuffer(CharBuffer buffer, long address, int capacity) { return newCharBuffer(address, capacity); }

		@Override
		IntBuffer setupBuffer(IntBuffer buffer, long address, int capacity) { return newIntBuffer(address, capacity); }

		@Override
		LongBuffer setupBuffer(LongBuffer buffer, long address, int capacity) { return newLongBuffer(address, capacity); }

		@Override
		FloatBuffer setupBuffer(FloatBuffer buffer, long address, int capacity) { return newFloatBuffer(address, capacity); }

		@Override
		DoubleBuffer setupBuffer(DoubleBuffer buffer, long address, int capacity) { return newDoubleBuffer(address, capacity); }

	}

	abstract static class MemoryAccessorJava extends MemoryAccessor {

		protected final ByteBuffer globalBuffer = ByteBuffer.allocateDirect(0);

		protected ByteBuffer newByteBuffer() {
			return globalBuffer.duplicate().order(ByteOrder.nativeOrder());
		}

	}

	/** Implementation using reflection. */
	private static final class MemoryAccessorReflect extends MemoryAccessorJava {

		private final Field address;
		private final Field capacity;

		private final Field cleaner;

		private final Field byteBufferParent;
		private final Field shortBufferParent;
		private final Field charBufferParent;
		private final Field intBufferParent;
		private final Field longBufferParent;
		private final Field floatBufferParent;
		private final Field doubleBufferParent;

		MemoryAccessorReflect() {
			try {
				address = getDeclaredField(Buffer.class, "address");
				capacity = getDeclaredField(Buffer.class, "capacity");

				// The byte order is important; it changes the subclass created by the asXXBuffer() methods.
				ByteBuffer buffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder());

				cleaner = getDeclaredField(buffer.getClass(), "cleaner");

				byteBufferParent = getField(buffer.slice(), buffer);
				shortBufferParent = getField(buffer.asShortBuffer(), buffer);
				charBufferParent = getField(buffer.asCharBuffer(), buffer);
				intBufferParent = getField(buffer.asIntBuffer(), buffer);
				longBufferParent = getField(buffer.asLongBuffer(), buffer);
				floatBufferParent = getField(buffer.asFloatBuffer(), buffer);
				doubleBufferParent = getField(buffer.asDoubleBuffer(), buffer);
			} catch (Exception e) {
				throw new UnsupportedOperationException(e);
			}
		}

		@Override
		public long getAddress(Buffer buffer) {
			try {
				return address.getLong(buffer);
			} catch (IllegalAccessException e) {
				throw new UnsupportedOperationException(e);
			}
		}

		@Override
		ByteBuffer newByteBuffer(long address, int capacity) {
			ByteBuffer buffer = newByteBuffer();

			try {
				this.address.setLong(buffer, address);
				this.capacity.setInt(buffer, capacity);

				// Optimization:
				// This method is similar to setup below, except we don't clear the parent field. Doing so requires an expensive volatile write. This is ok
				// because we don't need to ever release MemoryAccessorJava#globalBuffer.
			} catch (IllegalAccessException e) {
				throw new UnsupportedOperationException(e);
			}

			buffer.clear();
			return buffer;
		}

		private <T extends Buffer> T setup(T buffer, long address, int capacity, Field parentField) {
			try {
				this.address.setLong(buffer, address);
				this.capacity.setInt(buffer, capacity);

				parentField.set(buffer, null);
			} catch (IllegalAccessException e) {
				throw new UnsupportedOperationException(e);
			}

			buffer.clear();
			return buffer;
		}

		@Override
		ByteBuffer setupBuffer(ByteBuffer buffer, long address, int capacity) {
			if ( LWJGLUtil.DEBUG ) {
				try {
					// If we allowed this, the ByteBuffer's malloc'ed memory might never be freed.
					if ( cleaner.get(buffer) != null )
						throw new IllegalArgumentException("Instances created through ByteBuffer.allocateDirect cannot be modified.");
				} catch (IllegalAccessException e) {
					throw new UnsupportedOperationException(e);
				}
			}

			return setup(buffer, address, capacity, byteBufferParent);
		}

		@Override
		ShortBuffer setupBuffer(ShortBuffer buffer, long address, int capacity) {
			return setup(buffer, address, capacity, shortBufferParent);
		}

		@Override
		CharBuffer setupBuffer(CharBuffer buffer, long address, int capacity) {
			return setup(buffer, address, capacity, charBufferParent);
		}

		@Override
		IntBuffer setupBuffer(IntBuffer buffer, long address, int capacity) {
			return setup(buffer, address, capacity, intBufferParent);
		}

		@Override
		LongBuffer setupBuffer(LongBuffer buffer, long address, int capacity) {
			return setup(buffer, address, capacity, longBufferParent);
		}

		@Override
		FloatBuffer setupBuffer(FloatBuffer buffer, long address, int capacity) {
			return setup(buffer, address, capacity, floatBufferParent);
		}

		@Override
		DoubleBuffer setupBuffer(DoubleBuffer buffer, long address, int capacity) {
			return setup(buffer, address, capacity, doubleBufferParent);
		}

	}

	/** Implementation using sun.misc.Unsafe. */
	private static final class MemoryAccessorUnsafe extends MemoryAccessorJava {

		/**
		 * Limits the number of bytes to affect per call to Unsafe's bulk memory operations (copy & set). A limit is imposed to allow for safepoint polling
		 * during a large operation. This limit is equivalent to {@link java.nio.Bits#UNSAFE_COPY_THRESHOLD}.
		 */
		private static final long BULK_OP_THRESHOLD = 0x100000; // 1 MB

		private final Unsafe unsafe;

		private final long address;
		private final long capacity;

		private final long cleaner;

		private final long byteBufferParent;
		private final long shortBufferParent;
		private final long charBufferParent;
		private final long intBufferParent;
		private final long longBufferParent;
		private final long floatBufferParent;
		private final long doubleBufferParent;

		MemoryAccessorUnsafe() {
			try {
				unsafe = getUnsafeInstance();

				address = unsafe.objectFieldOffset(getDeclaredField(Buffer.class, "address"));
				capacity = unsafe.objectFieldOffset(getDeclaredField(Buffer.class, "capacity"));

				ByteBuffer buffer = globalBuffer;

				cleaner = unsafe.objectFieldOffset(getDeclaredField(buffer.getClass(), "cleaner"));

				byteBufferParent = unsafe.objectFieldOffset(getField(buffer.slice(), buffer));
				shortBufferParent = unsafe.objectFieldOffset(getField(buffer.asShortBuffer(), buffer));
				charBufferParent = unsafe.objectFieldOffset(getField(buffer.asCharBuffer(), buffer));
				intBufferParent = unsafe.objectFieldOffset(getField(buffer.asIntBuffer(), buffer));
				longBufferParent = unsafe.objectFieldOffset(getField(buffer.asLongBuffer(), buffer));
				floatBufferParent = unsafe.objectFieldOffset(getField(buffer.asFloatBuffer(), buffer));
				doubleBufferParent = unsafe.objectFieldOffset(getField(buffer.asDoubleBuffer(), buffer));
			} catch (Exception e) {
				throw new UnsupportedOperationException(e);
			}
		}

		@Override
		int getPageSize() {
			return unsafe.pageSize();
		}

		@Override
		public long getAddress(Buffer buffer) {
			return ((DirectBuffer)buffer).address();
		}

		@Override
		ByteBuffer newByteBuffer(long address, int capacity) {
			ByteBuffer buffer = newByteBuffer();

			unsafe.putLong(buffer, this.address, address);
			unsafe.putInt(buffer, this.capacity, capacity);

			// Optimization:
			// This method is similar to setup below, except we don't clear the parent field. This is ok because we don't need to ever release
			// MemoryAccessorJava#globalBuffer.

			buffer.clear();
			return buffer;
		}

		private <T extends Buffer> T setup(T buffer, long address, int capacity, long parentField) {
			unsafe.putLong(buffer, this.address, address);
			unsafe.putInt(buffer, this.capacity, capacity);

			unsafe.putObject(buffer, parentField, null);

			buffer.clear();
			return buffer;
		}

		@Override
		public ByteBuffer setupBuffer(ByteBuffer buffer, long address, int capacity) {
			// If we allowed this, the ByteBuffer's malloc'ed memory might never be freed.
			if ( LWJGLUtil.DEBUG && unsafe.getObject(buffer, cleaner) != null )
				throw new IllegalArgumentException("Instances created through ByteBuffer.allocateDirect cannot be modified.");

			return setup(buffer, address, capacity, byteBufferParent);
		}

		@Override
		ShortBuffer setupBuffer(ShortBuffer buffer, long address, int capacity) {
			return setup(buffer, address, capacity, shortBufferParent);
		}

		@Override
		CharBuffer setupBuffer(CharBuffer buffer, long address, int capacity) {
			return setup(buffer, address, capacity, charBufferParent);
		}

		@Override
		IntBuffer setupBuffer(IntBuffer buffer, long address, int capacity) {
			return setup(buffer, address, capacity, intBufferParent);
		}

		@Override
		LongBuffer setupBuffer(LongBuffer buffer, long address, int capacity) {
			return setup(buffer, address, capacity, longBufferParent);
		}

		@Override
		FloatBuffer setupBuffer(FloatBuffer buffer, long address, int capacity) {
			return setup(buffer, address, capacity, floatBufferParent);
		}

		@Override
		DoubleBuffer setupBuffer(DoubleBuffer buffer, long address, int capacity) {
			return setup(buffer, address, capacity, doubleBufferParent);
		}

		@Override
		void memSet(long dst, int value, int bytes) {
			// Do the memset in BULK_OP_THRESHOLD sized batches to keep TTSP low.
			while ( true ) {
				long batchSize = BULK_OP_THRESHOLD < bytes ? BULK_OP_THRESHOLD : bytes;
				unsafe.setMemory(dst, batchSize, (byte)(value & 0xFF));

				bytes -= BULK_OP_THRESHOLD;
				if ( bytes < 0 )
					break;

				dst += BULK_OP_THRESHOLD;
			}
		}

		@Override
		void memCopy(long src, long dst, int bytes) {
			// Do the memcpy in BULK_OP_THRESHOLD sized batches to keep TTSP low.

			while ( true ) {
				long batchSize = BULK_OP_THRESHOLD < bytes ? BULK_OP_THRESHOLD : bytes;
				unsafe.copyMemory(src, dst, batchSize);

				bytes -= BULK_OP_THRESHOLD;
				if ( bytes < 0 )
					break;

				src += BULK_OP_THRESHOLD;
				dst += BULK_OP_THRESHOLD;
			}
		}

		@Override
		byte memGetByte(long ptr) {
			return unsafe.getByte(ptr);
		}

		@Override
		short memGetShort(long ptr) {
			return unsafe.getShort(ptr);
		}

		@Override
		int memGetInt(long ptr) {
			return unsafe.getInt(ptr);
		}

		@Override
		long memGetLong(long ptr) {
			return unsafe.getLong(ptr);
		}

		@Override
		float memGetFloat(long ptr) {
			return unsafe.getFloat(ptr);
		}

		@Override
		double memGetDouble(long ptr) {
			return unsafe.getDouble(ptr);
		}

		@Override
		long memGetAddress(long ptr) {
			return unsafe.getAddress(ptr);
		}

		@Override
		void memPutByte(long ptr, byte value) {
			unsafe.putByte(ptr, value);
		}

		@Override
		void memPutShort(long ptr, short value) {
			unsafe.putShort(ptr, value);
		}

		@Override
		void memPutInt(long ptr, int value) {
			unsafe.putInt(ptr, value);
		}

		@Override
		void memPutLong(long ptr, long value) {
			unsafe.putLong(ptr, value);
		}

		@Override
		void memPutFloat(long ptr, float value) {
			unsafe.putFloat(ptr, value);
		}

		@Override
		void memPutDouble(long ptr, double value) {
			unsafe.putDouble(ptr, value);
		}

		@Override
		void memPutAddress(long ptr, long value) {
			unsafe.putAddress(ptr, value);
		}

		private static Unsafe getUnsafeInstance() {
			Field[] fields = Unsafe.class.getDeclaredFields();

			/*
			Different runtimes use different names for the Unsafe singleton,
			so we cannot use .getDeclaredField and we scan instead. For example:

			Oracle: theUnsafe
			PERC : m_unsafe_instance
			Android: THE_ONE
			*/
			for ( Field field : fields ) {
				if ( !field.getType().equals(Unsafe.class) )
					continue;

				int modifiers = field.getModifiers();
				if ( !(Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)) )
					continue;

				field.setAccessible(true);
				try {
					return (Unsafe)field.get(null);
				} catch (IllegalAccessException e) {
					// ignore
				}
				break;
			}

			throw new UnsupportedOperationException();
		}
	}

	static Field getDeclaredField(Class<?> root, String fieldName) throws NoSuchFieldException {
		Class<?> type = root;

		do {
			try {
				Field field = type.getDeclaredField(fieldName);
				field.setAccessible(true);
				return field;
			} catch (NoSuchFieldException e) {
				type = type.getSuperclass();
			}
		} while ( type != null );

		throw new NoSuchFieldException(fieldName + " does not exist in " + root.getSimpleName() + " or any of its superclasses.");
	}

	static Field getField(Buffer buffer, Object value) throws NoSuchFieldException {
		Class<?> type = buffer.getClass();

		do {
			for ( Field field : type.getDeclaredFields() ) {
				if ( Modifier.isStatic(field.getModifiers()) )
					continue;

				if ( !field.getType().isAssignableFrom(value.getClass()) )
					continue;

				field.setAccessible(true);
				try {
					Object fieldValue = field.get(buffer);
					if ( fieldValue == value )
						return field;
				} catch (IllegalAccessException e) {
					// ignore
				}
			}

			type = type.getSuperclass();
		} while ( type != null );

		throw new NoSuchFieldException(String.format(
			"The specified value does not exist as a field in %s or any of its superclasses.",
			buffer.getClass().getSimpleName()
		));
	}

}