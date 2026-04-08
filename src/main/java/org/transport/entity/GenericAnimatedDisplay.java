package org.transport.entity;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.transport.service.ByteArrayWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class GenericAnimatedDisplay extends Display {

	public GenericAnimatedDisplay(ObjectArrayList<String> groups, int width, int height, String fileName, ObjectImmutableList<ImageFrame> frames) {
		super(groups, width, height, fileName, frames, DisplayType.ANIMATED);
	}

	@Override
	protected void writeBinaryBytes(int initialOffset, ByteArrayOutputStream byteArrayOutputStream) {
		final ByteArrayWriter byteArrayWriter = new ByteArrayWriter(initialOffset);
		getFrames().forEach(imageFrame -> byteArrayWriter.write(offset -> {
			try (final ByteArrayOutputStream innerByteArrayOutputStream = new ByteArrayOutputStream()) {
				ByteArrayWriter.write32(innerByteArrayOutputStream, imageFrame.delayMicros());
				innerByteArrayOutputStream.writeBytes(getImageBytes(imageFrame.pixels()));
				return innerByteArrayOutputStream.toByteArray();
			} catch (IOException e) {
				System.err.println(e.getMessage());
				return new byte[0];
			}
		}));
		byteArrayOutputStream.writeBytes(byteArrayWriter.getResult());
	}
}
