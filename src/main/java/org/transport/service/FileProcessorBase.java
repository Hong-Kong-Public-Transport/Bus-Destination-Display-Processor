package org.transport.service;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import lombok.AllArgsConstructor;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.transport.entity.Display;
import org.transport.entity.ImageFrame;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.function.Consumer;

/**
 * Processes a raw image file into an {@link Display}. Supports animated formats as well.
 */
@AllArgsConstructor
public abstract class FileProcessorBase {

	private final ObjectArrayList<String> groups;
	private final String fileName;
	private final byte[] rawImageBytes;

	public final void process(Consumer<Display> callback) {
		try (
				final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(rawImageBytes);
				final FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(byteArrayInputStream);
				final Java2DFrameConverter frameConverter = new Java2DFrameConverter()
		) {
			grabber.start();
			final ObjectArrayList<ImageFrame> imageFrames = new ObjectArrayList<>();
			final int width = grabber.getImageWidth();
			final int height = grabber.getImageHeight();
			Long previousTimestamp = null;
			Frame frame;

			while ((frame = grabber.grabImage()) != null) {
				if (frame.image == null) {
					continue;
				}

				final BufferedImage bufferedImage = frameConverter.convert(frame);

				if (bufferedImage == null) {
					continue;
				}

				final int[] pixels = bufferedImage.getRGB(0, 0, width, height, null, 0, width);
				final long timestamp = frame.timestamp;
				imageFrames.add(new ImageFrame(previousTimestamp == null ? 0 : (int) Math.max(1, timestamp - previousTimestamp), convertTo1Bit(width, height, pixels)));
				previousTimestamp = timestamp;
			}

			if (imageFrames.isEmpty()) {
				System.err.printf("No frames could be extracted from image [%s]%n", fileName);
			} else {
				callback.accept(new Display(groups, getOutputWidth(), getOutputHeight(), fileName, new ObjectImmutableList<>(imageFrames)));
			}
		} catch (Exception e) {
			System.err.printf("Failed to process image [%s] with FFmpeg: %s%n", fileName, e.getMessage());
		}
	}

	/**
	 * Convert RGB pixels to a 1-bit array. The output image may have a different size.
	 *
	 * @param width  the width of the raw image
	 * @param height the height of the raw image
	 * @param pixels the RGB pixels of the raw image
	 * @return the 1-bit array
	 */
	protected abstract boolean[] convertTo1Bit(int width, int height, int[] pixels);

	protected abstract int getOutputWidth();

	protected abstract int getOutputHeight();
}
