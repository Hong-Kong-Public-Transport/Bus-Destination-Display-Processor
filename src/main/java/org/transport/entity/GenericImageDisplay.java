package org.transport.entity;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;

import java.io.ByteArrayOutputStream;

public final class GenericImageDisplay extends Display {

	public GenericImageDisplay(ObjectArrayList<String> groups, int width, int height, String fileName, ObjectImmutableList<ImageFrame> frames) {
		super(groups, width, height, fileName, frames, DisplayType.IMAGE);
	}

	@Override
	protected void writeBinaryBytes(int initialOffset, ByteArrayOutputStream byteArrayOutputStream) {
		byteArrayOutputStream.writeBytes(getImageBytes(getFrames().getFirst().pixels()));
	}
}
