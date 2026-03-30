package org.transport.entity;

import it.unimi.dsi.fastutil.objects.ObjectArraySet;

public record Index(ObjectArraySet<String> groups, String fileName, String base64) {
}
