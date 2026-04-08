package org.transport;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.transport.service.FileProcessorBase;
import org.transport.service.StandardFileProcessor;

public final class StandardBinaryFileTest extends BinaryFileTest {

	@Test
	@EnabledIfSystemProperty(named = DIRECTORY_PROPERTY, matches = "(\\w+\\/)*\\w+")
	public void validateSerialization() {
		setup();
	}

	@Override
	protected FileProcessorBase getFileProcessor(String fileName, byte[] rawImageBytes) {
		return new StandardFileProcessor(ObjectArrayList.of(), fileName, rawImageBytes);
	}
}
