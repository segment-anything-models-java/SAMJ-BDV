package org.scijava;

import io.scif.SCIFIOService;
import net.imagej.ImageJService;
import org.scijava.service.SciJavaService;

public class LocalDetachedContext {
	private LocalDetachedContext() {};
	private static Context localContext = null;

	public static Context getContext() {
		if (localContext == null) localContext = createOfficialLikeContext();
		return localContext;
	}

	private static Context createOfficialLikeContext() {
		//this is copied from net.imageJ default constructor
		return new Context(SciJavaService.class, SCIFIOService.class, ImageJService.class);
	}
}
