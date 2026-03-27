package org.transport.entity;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public record Index(ObjectArrayList<String> groups, String fileName, int cppIndex) {
}
