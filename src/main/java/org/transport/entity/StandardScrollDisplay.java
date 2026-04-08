package org.transport.entity;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.bytedeco.opencv.opencv_core.Mat;
import org.transport.service.ByteArrayWriter;
import org.transport.tool.OpenCVHelper;

import java.io.ByteArrayOutputStream;

public final class StandardScrollDisplay extends Display {

	public StandardScrollDisplay(ObjectArrayList<String> groups, int width, int height, String fileName, ObjectImmutableList<ImageFrame> frames) {
		super(groups, width, height, fileName, frames, DisplayType.STANDARD_SCROLL);
	}

	@Override
	protected void writeBinaryBytes(int initialOffset, ByteArrayOutputStream byteArrayOutputStream) {
		final ObjectArrayList<boolean[]> animatedColumns = new ObjectArrayList<>();
		int sameColumnCount = getWidth();
		double[] lastProjection = new double[getWidth()];

		for (int i = 0; i < getFrames().size(); i++) {
			final ImageFrame frame = getFrames().get(i);
			final Mat mat = OpenCVHelper.pixelsToMat(getWidth(), getHeight(), frame.pixels());

			try {
				// Find non-animated column count
				final double[] projection = OpenCVHelper.getProjection(mat, false);
				for (int j = 0; j < sameColumnCount; j++) {
					if (i > 0 && lastProjection[j] != projection[j]) {
						sameColumnCount = j;
						break;
					}
					lastProjection[j] = projection[j];
				}

				// Copy last column of each frame
				final boolean[] column = new boolean[getHeight()];
				for (int y = 0; y < getHeight(); y++) {
					column[y] = frame.pixels()[getWidth() * (y + 1) - 1];
				}
				animatedColumns.add(column);
			} finally {
				mat.release();
			}
		}

		final int animatedColumnCount = animatedColumns.size() - getWidth() + sameColumnCount;
		final int totalWidth = sameColumnCount + animatedColumnCount;
		final boolean[] firstFramePixels = getFrames().getFirst().pixels();
		final boolean[] output = new boolean[totalWidth * getHeight()];

		for (int x = 0; x < totalWidth; x++) {
			for (int y = 0; y < getHeight(); y++) {
				output[x + y * totalWidth] = x < sameColumnCount ? firstFramePixels[x + y * getWidth()] : animatedColumns.get(x - sameColumnCount)[y];
			}
		}

		ByteArrayWriter.write32(byteArrayOutputStream, sameColumnCount);
		ByteArrayWriter.write32(byteArrayOutputStream, animatedColumnCount);
		byteArrayOutputStream.writeBytes(getImageBytes(output));
	}
}
