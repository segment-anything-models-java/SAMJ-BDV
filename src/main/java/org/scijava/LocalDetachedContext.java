package org.scijava;

import io.scif.SCIFIOService;
import net.imagej.ImageJService;
import org.scijava.service.SciJavaService;

public class LocalDetachedContext {
	private LocalDetachedContext() {};
	private static Context localContext = null;

	public static void memorizeThisContext(final Context scijavaContext) {
		if (scijavaContext == null) {
			System.out.println("INFO FOR DEVELOPERS: Refusing to memorize NULL Context... expect troubles later.");
			return;
		}
		if (localContext != null && localContext != scijavaContext) {
			System.out.println("INFO FOR DEVELOPERS: RE-Memorizing some new Context...");
		}
		localContext = scijavaContext;
	}

	public static void startWithThisContext(final Context scijavaContext) {
		if (localContext != null) {
			if (localContext == scijavaContext) return; //NB: don't complain when the very same Context is given again
			throw new IllegalStateException("Can't accept a new Context when one is already available!");
		}
		localContext = scijavaContext;
		System.out.println("Caching access to some global Context.");
	}

	public static Context getContext() {
		if (localContext == null) localContext = createOfficialLikeContext();
		return localContext;
	}

	private static Context createOfficialLikeContext() {
		//this is copied from net.imageJ default constructor
		return new Context(SciJavaService.class, SCIFIOService.class, ImageJService.class);
	}
}
