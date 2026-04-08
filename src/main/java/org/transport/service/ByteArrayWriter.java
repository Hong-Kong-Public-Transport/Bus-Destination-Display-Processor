package org.transport.service;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.RequiredArgsConstructor;
import org.transport.Application;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.IntFunction;

@RequiredArgsConstructor
public final class ByteArrayWriter {

	private final int initialOffset;
	private final ObjectArrayList<IntFunction<byte[]>> writeDataList = new ObjectArrayList<>();

	public void write(IntFunction<byte[]> writeData) {
		writeDataList.add(writeData);
	}

	public byte[] getResult() {
		try (final ByteArrayOutputStream resultStream = new ByteArrayOutputStream()) {
			final int count = writeDataList.size();
			write32(resultStream, count);
			final int headerOffset = initialOffset + (count + 1) * Application.BYTES_PER_INT;
			int offset = headerOffset;
			final ObjectArrayList<byte[]> dataList = new ObjectArrayList<>();

			for (final IntFunction<byte[]> writeData : writeDataList) {
				write32(resultStream, offset);
				final byte[] data = writeData.apply(headerOffset);
				offset += data.length;
				dataList.add(data);
			}

			dataList.forEach(resultStream::writeBytes);
			return resultStream.toByteArray();
		} catch (IOException e) {
			System.err.println(e.getMessage());
			return new byte[0];
		}
	}

	public static void write32(ByteArrayOutputStream byteArrayOutputStream, int data) {
		for (int i = 0; i < Application.BYTES_PER_INT; i++) {
			byteArrayOutputStream.write((byte) ((data >> i * Application.BITS_PER_BYTE) & 0xFF));
		}
	}
}
