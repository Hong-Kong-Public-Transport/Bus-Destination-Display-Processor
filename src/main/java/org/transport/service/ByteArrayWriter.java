package org.transport.service;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.transport.Application;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@RequiredArgsConstructor
public final class ByteArrayWriter {

	@Getter
	private int rawOffset;

	private final int initialOffset;
	private final IntArrayList offsets = new IntArrayList();
	private final ByteArrayOutputStream dataStream = new ByteArrayOutputStream();

	public void write(int header, byte[] data) {
		write32(dataStream, header);
		write(data);
		rawOffset += Application.BYTES_PER_INT;
	}

	public void write(byte[] data) {
		offsets.add(rawOffset);
		dataStream.writeBytes(data);
		rawOffset += data.length;
	}

	public byte[] getResult() {
		final ByteArrayOutputStream resultStream = new ByteArrayOutputStream();
		final int count = offsets.size();
		write32(resultStream, count);
		offsets.forEach(offset -> write32(resultStream, initialOffset + (count + 1) * Application.BYTES_PER_INT + offset));

		try {
			dataStream.writeTo(resultStream);
		} catch (IOException e) {
			System.err.println(e.getMessage());
		}

		return resultStream.toByteArray();
	}

	public static void write32(ByteArrayOutputStream byteArrayOutputStream, int data) {
		for (int i = 0; i < Application.BYTES_PER_INT; i++) {
			byteArrayOutputStream.write((byte) ((data >> i * Application.BITS_PER_BYTE) & 0xFF));
		}
	}
}
