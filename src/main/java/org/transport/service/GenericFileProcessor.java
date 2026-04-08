package org.transport.service;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.transport.entity.Display;
import org.transport.entity.GenericAnimatedDisplay;
import org.transport.entity.GenericImageDisplay;
import org.transport.entity.ImageFrame;
import org.transport.tool.OpenCVHelper;

public final class GenericFileProcessor extends FileProcessorBase {

	private int width;
	private int height;

	public GenericFileProcessor(ObjectArrayList<String> groups, String fileName, byte[] rawImageBytes) {
		super(groups, fileName, rawImageBytes);
	}

	@Override
	protected boolean[] convertTo1Bit(int width, int height, int[] pixels) {
		final Mat image = OpenCVHelper.pixelsToMat(width, height, pixels);
		final Mat binary = new Mat();

		try {
			this.width = width;
			this.height = height;
			opencv_imgproc.threshold(image, binary, 0, 255, opencv_imgproc.THRESH_BINARY | opencv_imgproc.THRESH_OTSU);
			final boolean[] output = new boolean[width * height];

			for (int x = 0; x < width; x++) {
				for (int y = 0; y < height; y++) {
					output[x + y * width] = OpenCVHelper.getPixel(binary, x, y) > 0;
				}
			}

			return output;
		} finally {
			image.release();
			binary.release();
		}
	}

	@Override
	protected Display getDisplay(ObjectImmutableList<ImageFrame> imageFrames) {
		return imageFrames.size() == 1 ? new GenericImageDisplay(groups, width, height, fileName, imageFrames) : new GenericAnimatedDisplay(groups, width, height, fileName, imageFrames);
	}
}
