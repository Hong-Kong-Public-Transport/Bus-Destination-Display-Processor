package org.transport.tool;

import org.bytedeco.javacpp.indexer.DoubleIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;

import java.awt.*;

public final class OpenCVHelper {

	/**
	 * Gets the value of a pixel.
	 *
	 * @param image the input image
	 * @param x     the x value
	 * @param y     the y value
	 * @return the value of the pixel
	 */
	public static int getPixel(Mat image, int x, int y) {
		return image.ptr(y).get(x) & 0xFF;
	}

	/**
	 * Sets the value of a pixel.
	 *
	 * @param image the input image
	 * @param x     the x value
	 * @param y     the y value
	 * @param value the value of the pixel
	 */
	public static void setPixel(Mat image, int x, int y, byte value) {
		image.ptr(y).put(x, value);
	}

	/**
	 * Converts a 1-bit pixel array to a {@link Mat} object.
	 *
	 * @param width  the width of the image
	 * @param height the height of the image
	 * @param pixels the pixels of the image
	 * @return the 1-bit OpenCV {@link Mat} image
	 */
	public static Mat pixelsToMat(int width, int height, boolean[] pixels) {
		final Mat output = new Mat(height, width, opencv_core.CV_8UC1);
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				setPixel(output, x, y, pixels[x + y * width] ? (byte) 0xFF : 0);
			}
		}
		return output;
	}

	/**
	 * Converts a colour pixel array to a grayscale {@link Mat} object.
	 *
	 * @param width  the width of the image
	 * @param height the height of the image
	 * @param pixels the pixels of the image
	 * @return the grayscale OpenCV {@link Mat} image
	 */
	public static Mat pixelsToMat(int width, int height, int[] pixels) {
		final Mat output = new Mat(height, width, opencv_core.CV_8UC1);
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				final Color color = new Color(pixels[x + y * width]);
				setPixel(output, x, y, (byte) ((color.getRed() + color.getGreen() + color.getBlue()) / 3));
			}
		}
		return output;
	}

	/**
	 * @param image the input image
	 * @param axis  {@code false} for X-axis, {@code true} for Y-axis
	 * @return an array of summed brightness values across an axis of the image
	 */
	public static double[] getProjection(Mat image, boolean axis) {
		final Mat sum = new Mat();

		try {
			opencv_core.reduce(image, sum, axis ? 1 : 0, opencv_core.REDUCE_SUM, opencv_core.CV_64F);
			final int width = sum.cols();
			final int height = sum.rows();
			final double[] output = new double[width * height];

			try (final DoubleIndexer doubleIndexer = sum.createIndexer()) {
				int i = 0;
				for (int y = 0; y < height; y++) {
					for (int x = 0; x < width; x++) {
						output[i++] = doubleIndexer.get(y, x);
					}
				}
			}

			return output;
		} finally {
			sum.release();
		}
	}
}
