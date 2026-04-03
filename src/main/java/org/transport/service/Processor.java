package org.transport.service;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.AllArgsConstructor;
import org.apache.commons.imaging.common.PackBits;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Animation;
import org.opencv.imgcodecs.Imgcodecs;
import org.transport.Application;
import org.transport.entity.Display;
import org.transport.entity.Frame;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;

@AllArgsConstructor
public final class Processor {

	private final ObjectArrayList<String> groups;
	private final String source;

	private static final String FILE_FORMAT = ".png";
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(10)).build();

	public void process(Consumer<Display> callback) {
		getGoogleDriveImage(bytes -> {
			final MatOfByte inputMatOfByte = new MatOfByte(bytes);
			final Animation inputAnimation = new Animation();
			final ObjectArrayList<Mat> rawFrames = new ObjectArrayList<>();
			final ObjectArrayList<Mat> processedFrames = new ObjectArrayList<>();
			final MatOfByte outputMatOfByte = new MatOfByte();

			try {
				// Decode image
				Imgcodecs.imdecodeanimation(inputMatOfByte, inputAnimation);
				rawFrames.addAll(inputAnimation.get_frames());
				final int[] durations = inputAnimation.get_durations().toArray();
				final ObjectArrayList<Frame> frames = new ObjectArrayList<>();
				final ImageProcessor imageProcessor = new ImageProcessor();

				for (int i = 0; i < Math.min(rawFrames.size(), durations.length); i++) {
					final int duration = durations[i];
					imageProcessor.process(rawFrames.get(i), processedFrame -> {
						if (processedFrames.isEmpty()) {
							frames.add(new Frame(duration, getImageBytes(processedFrame)));
						} else {
							final Mat delta = new Mat();
							try {
								Core.bitwise_xor(processedFrames.getLast(), processedFrame, delta);
								frames.add(new Frame(duration, getImageBytes(delta)));
							} finally {
								delta.release();
							}
						}
						processedFrames.add(processedFrame.clone());
					});
				}

				if (processedFrames.isEmpty()) {
					System.err.printf("Image [%s] has no frames%n", getGroupName());
				} else {
					if (processedFrames.size() == 1) {
						Imgcodecs.imencode(FILE_FORMAT, processedFrames.getFirst(), outputMatOfByte);
					} else {
						final Animation outputAnimation = new Animation();
						outputAnimation.set_frames(processedFrames);
						outputAnimation.set_durations(inputAnimation.get_durations());
						Imgcodecs.imencodeanimation(FILE_FORMAT, outputAnimation, outputMatOfByte);
					}

					final String fileName = cleanString(String.format("%s_%s", getGroupName(), source.toLowerCase().replace("_", ""))) + FILE_FORMAT;
					callback.accept(new Display(new ObjectArrayList<>(groups), imageProcessor.getWidth(), imageProcessor.getHeight(), fileName, frames, outputMatOfByte.toArray()));
				}
			} catch (Exception e) {
				System.err.printf("Failed to process image [%s]: %s%n", getGroupName(), e.getMessage());
			} finally {
				inputMatOfByte.release();
				rawFrames.forEach(Mat::release);
				processedFrames.forEach(Mat::release);
				outputMatOfByte.release();
			}
		});
	}

	private void getGoogleDriveImage(Consumer<byte[]> callback) {
		try {
			final HttpResponse<byte[]> httpResponse = HTTP_CLIENT.send(
					HttpRequest.newBuilder().uri(URI.create(String.format("https://lh3.googleusercontent.com/d/%s", source))).timeout(Duration.ofSeconds(20)).GET().build(),
					HttpResponse.BodyHandlers.ofByteArray()
			);

			if (httpResponse.statusCode() == 200) {
				callback.accept(httpResponse.body());
			} else {
				System.err.printf("HTTP %d for [%s]%n", httpResponse.statusCode(), source);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			System.err.println(e.getMessage());
		} catch (IOException e) {
			System.err.println(e.getMessage());
		}
	}

	private String getGroupName() {
		final ObjectArrayList<String> text = new ObjectArrayList<>();

		for (int i = 0; i < Math.min(groups.size(), 2); i++) {
			text.add(groups.get(i).toUpperCase());
		}

		return String.join("_", text);
	}

	private static byte[] getImageBytes(Mat output) {
		final int width = output.width();
		final int height = output.height();
		final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

		for (int i = 0; i < width * height; i += Application.BITS_PER_BYTE) {
			int data = 0;
			for (int j = 0; j < Application.BITS_PER_BYTE; j++) {
				if (i + j < width * height) {
					data |= (output.get((i + j) / width, (i + j) % width)[0] > 0 ? 1 : 0) << j;
				}
			}
			byteArrayOutputStream.write(data);
		}

		try {
			return PackBits.compress(byteArrayOutputStream.toByteArray());
		} catch (IOException e) {
			System.err.println(e.getMessage());
			return new byte[0];
		}
	}

	private static String cleanString(String text) {
		return text.trim().replaceAll("\\W+", "_").replaceAll("_+", "_");
	}
}
