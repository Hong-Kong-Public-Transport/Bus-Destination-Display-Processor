package org.transport.entity;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import lombok.Getter;
import org.apache.commons.imaging.common.PackBits;
import org.transport.Application;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;

public abstract class Display {

	@Getter
	private final ObjectArrayList<String> groups;
	@Getter
	private final int width;
	@Getter
	private final int height;
	@Getter
	private final String fileName;
	@Getter
	private final ObjectImmutableList<ImageFrame> frames;
	private final DisplayType displayType;

	public Display(ObjectArrayList<String> groups, int width, int height, String fileName, ObjectImmutableList<ImageFrame> frames, DisplayType displayType) {
		this.groups = groups;
		this.width = width;
		this.height = height;
		this.fileName = fileName;
		this.frames = frames;
		this.displayType = displayType;
	}

	@Override
	public final boolean equals(Object object) {
		if (object instanceof Display display) {
			return display == this || width == display.width && height == display.height && frames.equals(display.frames);
		} else {
			return false;
		}
	}

	@Override
	public final int hashCode() {
		return Objects.hash(width, height, frames);
	}

	public final byte[] getBinaryBytes(int initialOffset) {
		try (final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
			byteArrayOutputStream.write(displayType.ordinal());
			writeBinaryBytes(initialOffset + 1, byteArrayOutputStream);
			return byteArrayOutputStream.toByteArray();
		} catch (IOException e) {
			System.err.println(e.getMessage());
			return new byte[0];
		}
	}

	protected abstract void writeBinaryBytes(int initialOffset, ByteArrayOutputStream byteArrayOutputStream);

	protected static byte[] getImageBytes(boolean[] pixels) {
		try (final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
			for (int i = 0; i < pixels.length; i += Application.BITS_PER_BYTE) {
				int data = 0;
				for (int j = 0; j < Application.BITS_PER_BYTE; j++) {
					if (i + j < pixels.length) {
						data |= (pixels[i + j] ? 1 : 0) << j;
					}
				}
				byteArrayOutputStream.write(data);
			}

			return PackBits.compress(byteArrayOutputStream.toByteArray());
		} catch (IOException e) {
			System.err.println(e.getMessage());
			return new byte[0];
		}
	}
}
