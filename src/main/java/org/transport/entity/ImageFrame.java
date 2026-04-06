package org.transport.entity;

import java.util.Arrays;
import java.util.Objects;

public record ImageFrame(int delayMicros, boolean[] pixels) {

	@Override
	public boolean equals(Object object) {
		if (object instanceof ImageFrame imageFrame) {
			return imageFrame == this || delayMicros == imageFrame.delayMicros && Arrays.equals(pixels, imageFrame.pixels);
		} else {
			return false;
		}
	}

	@Override
	public int hashCode() {
		return Objects.hash(delayMicros, Arrays.hashCode(pixels));
	}
}
