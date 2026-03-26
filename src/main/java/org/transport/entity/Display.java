package org.transport.entity;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public record Display(ObjectArrayList<String> groups, int width, int height, byte[] imageBytes) {
}
