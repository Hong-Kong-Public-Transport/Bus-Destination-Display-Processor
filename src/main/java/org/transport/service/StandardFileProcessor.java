package org.transport.service;

import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Range;
import org.jspecify.annotations.Nullable;
import org.transport.tool.OpenCVHelper;

import java.awt.*;
import java.util.Collections;

public final class StandardFileProcessor extends FileProcessorBase {

	private int width;
	private int height;
	private int pitchX;
	private int pitchY;
	@Nullable
	private Range rangeX;
	@Nullable
	private Range rangeY;

	private static final int MAX_SMOOTH_AMOUNT = 5;

	public StandardFileProcessor(ObjectArrayList<String> groups, String source, byte[] rawImageBytes) {
		super(groups, source, rawImageBytes);
	}

	@Override
	protected boolean[] convertTo1Bit(int width, int height, int[] pixels) {
		final Mat grayscaleImage = new Mat(height, width, opencv_core.CV_8UC1);

		try {
			for (int x = 0; x < width; x++) {
				for (int y = 0; y < height; y++) {
					final Color color = new Color(pixels[x + y * width]);
					OpenCVHelper.setPixel(grayscaleImage, x, y, (byte) ((color.getRed() + color.getGreen() + color.getBlue()) / 3));
				}
			}

			if (rangeX == null || rangeY == null) {
				rangeX = getCropRange(grayscaleImage, true);
				rangeY = getCropRange(grayscaleImage, false);
			}

			return process(grayscaleImage);
		} finally {
			grayscaleImage.release();
		}
	}

	@Override
	protected int getOutputWidth() {
		return width;
	}

	@Override
	protected int getOutputHeight() {
		return height;
	}

	/**
	 * Processes a raw image and outputs a black and white, cropped, and resized image. Note that the output image is released after the callback.
	 *
	 * @param grayscaleImage the raw image
	 * @return processed and resized image as a 1-bit pixel array
	 */
	private boolean[] process(Mat grayscaleImage) {
		final Mat croppedImage = new Mat(grayscaleImage, rangeX, rangeY);

		try {
			if (pitchX <= 0 || pitchY <= 0) {
				pitchX = estimatePitch(croppedImage, false);
				pitchY = estimatePitch(croppedImage, true);
			}

			return getResult(croppedImage);
		} finally {
			croppedImage.release();
		}
	}

	/**
	 * @param image the input image
	 * @return processed and resized image as a 1-bit pixel array
	 */
	private boolean[] getResult(Mat image) {
		final Mat binary = new Mat();

		try {
			final int rawWidth = image.cols();
			final int rawHeight = image.rows();
			final int newPitchX = Math.max(1, pitchX);
			final int newPitchY = Math.max(1, pitchY);
			width = Math.round((float) rawWidth / newPitchX);
			height = Math.round((float) rawHeight / newPitchY);
			opencv_imgproc.threshold(image, binary, 0, 255, opencv_imgproc.THRESH_BINARY | opencv_imgproc.THRESH_OTSU);
			final int[][] pixels = new int[width][height];
			final IntOpenHashSet values = new IntOpenHashSet();

			for (int x = 0; x < width; x++) {
				for (int y = 0; y < height; y++) {
					final int x1 = x * newPitchX;
					final int x2 = Math.min((x + 1) * newPitchX, rawWidth);
					final int y1 = y * newPitchY;
					final int y2 = Math.min((y + 1) * newPitchY, rawHeight);
					final int value = getFilledPixelCount(binary, x1, y1, x2, y2);
					values.add(value);
					pixels[x][y] = value;
				}
			}

			final int threshold = getMedian(new IntArrayList(values));
			final boolean[] output = new boolean[width * height];

			for (int x = 0; x < width; x++) {
				for (int y = 0; y < height; y++) {
					output[x + y * width] = pixels[x][y] >= threshold;
				}
			}

			return output;
		} finally {
			binary.release();
		}
	}

	/**
	 * @param image the input image
	 * @param axis  {@code false} for X-axis, {@code true} for Y-axis
	 * @return a range of pixels with the black border removed
	 */
	private static Range getCropRange(Mat image, boolean axis) {
		final int width = image.cols();
		final int height = image.rows();
		final Mat croppedImage = new Mat(
				image,
				axis ? Range.all() : new Range((int) Math.floor(height * 0.4), (int) Math.ceil(height * 0.6) + 1),
				axis ? new Range((int) Math.floor(width * 0.4), (int) Math.ceil(width * 0.6) + 1) : Range.all()
		);

		try {
			final double[] projection = OpenCVHelper.getProjection(croppedImage, axis);
			int start = -1;
			int end = -1;

			for (int i = 0; i < projection.length; i++) {
				if (start < 0 && projection[i] > 0) {
					start = i;
				}

				if (end < 0 && projection[projection.length - i - 1] > 0) {
					end = projection.length - i;
				}
			}

			return end > start ? new Range(start, end) : Range.all();
		} finally {
			croppedImage.release();
		}
	}

	/**
	 * @param image the input image
	 * @param axis  {@code false} for X-axis, {@code true} for Y-axis
	 * @return the estimated pixels between LEDs on the image
	 */
	private static int estimatePitch(Mat image, boolean axis) {
		final double[] projection = OpenCVHelper.getProjection(image, axis);
		final IntArrayList pitches = new IntArrayList();

		for (int smoothAmount = 0; smoothAmount < MAX_SMOOTH_AMOUNT; smoothAmount++) {
			double previousValue = getSmoothedValue(projection, 0, smoothAmount);
			boolean previousIncreasing = false;
			boolean previousDecreasing = false;
			int previousMaxIndex = -1;
			int previousMinIndex = -1;

			for (int i = 1; i < projection.length; i++) {
				final double currentValue = getSmoothedValue(projection, i, smoothAmount);
				final boolean currentIncreasing = currentValue > previousValue;
				final boolean currentDecreasing = currentValue < previousValue;

				if (previousIncreasing && !currentIncreasing) {
					if (previousMaxIndex >= 0) {
						pitches.add(i - previousMaxIndex);
					}
					previousMaxIndex = i;
				}

				if (previousDecreasing && !currentDecreasing) {
					if (previousMinIndex >= 0) {
						pitches.add(i - previousMinIndex);
					}
					previousMinIndex = i;
				}

				previousValue = currentValue;
				previousIncreasing = currentIncreasing;
				previousDecreasing = currentDecreasing;
			}
		}

		return getMode(pitches);
	}

	/**
	 * @param image the input image
	 * @param x1    left bound (inclusive)
	 * @param y1    top bound (inclusive)
	 * @param x2    right bound (exclusive)
	 * @param y2    bottom bound (exclusive)
	 * @return the number of filled pixels in the area
	 */
	private static int getFilledPixelCount(Mat image, int x1, int y1, int x2, int y2) {
		int count = 0;
		for (int x = x1; x < x2; x++) {
			for (int y = y1; y < y2; y++) {
				if (OpenCVHelper.getPixel(image, x, y) > 0) {
					count++;
				}
			}
		}
		return count;
	}

	/**
	 * @param values       an input array of values
	 * @param index        the index to retrieve
	 * @param smoothAmount how many adjacent values to smooth from
	 * @return a smoothed value created by the mean of adjacent values
	 */
	private static double getSmoothedValue(double[] values, int index, int smoothAmount) {
		double sum = 0;

		for (int i = -smoothAmount; i <= smoothAmount; i++) {
			sum += values[Math.clamp(index + i, 0, values.length - 1)];
		}

		return sum / (smoothAmount * 2 + 1);
	}

	private static int getMedian(IntArrayList values) {
		Collections.sort(values);
		return values.isEmpty() ? 0 : values.getInt(values.size() / 2);
	}

	private static int getMode(IntArrayList values) {
		if (values.isEmpty()) {
			return 0;
		}

		final Int2LongOpenHashMap frequencyMap = new Int2LongOpenHashMap();
		for (int i = 0; i < values.size(); i++) {
			frequencyMap.addTo(values.getInt(i), 1);
		}

		final long maxFrequency = Collections.max(frequencyMap.values());
		return frequencyMap.int2LongEntrySet()
				.stream()
				.filter(entry -> entry.getLongValue() == maxFrequency)
				.mapToInt(Int2LongMap.Entry::getIntKey)
				.findFirst()
				.orElse(0);
	}
}
