package org.transport.entity;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;

import java.util.Objects;

public record Display(ObjectArrayList<String> groups, int width, int height, String fileName, ObjectImmutableList<ImageFrame> frames) {

	@Override
	public boolean equals(Object object) {
		if (object instanceof Display display) {
			return display == this || width == display.width && height == display.height && frames.equals(display.frames);
		} else {
			return false;
		}
	}

	@Override
	public int hashCode() {
		return Objects.hash(width, height, frames);
	}
}
