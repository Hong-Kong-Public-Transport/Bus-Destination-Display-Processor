package org.transport.entity;

import it.unimi.dsi.fastutil.objects.ObjectArraySet;

public record Index(ObjectArraySet<String> groups, boolean isCurrent, String fileName) {
}
