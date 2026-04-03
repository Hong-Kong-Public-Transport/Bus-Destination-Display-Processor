package org.transport.entity;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public record Display(ObjectArrayList<String> groups, int width, int height, String fileName, ObjectArrayList<Frame> frames, byte[] fileBytes) {
}
