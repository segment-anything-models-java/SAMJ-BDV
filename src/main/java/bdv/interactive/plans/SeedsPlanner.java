package bdv.interactive.plans;

import bdv.interactive.services.BdvPromptsEvent;
import bdv.tools.brightness.ConverterSetup;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

import java.util.ArrayList;
import java.util.List;

/**
 * Plans for the multi-prompt ("J"): seeds are found inside the user's box, and the plan
 * has one item per seed, a box around it. All items share the view of the user's event,
 * so their view images are the same (see {@link BdvPromptsEvent#hasSameViewAs(BdvPromptsEvent)}).
 * <p>
 * The view image to search the seeds in is the client's choice (which source, original
 * or contrast-adjusted pixels, ...), e.g., from {@code OriginalViewsService.getEventView(...)}.
 */
public final class SeedsPlanner {
	private SeedsPlanner() {}

	/** Finds boxes around objects in a part of a view image. */
	@FunctionalInterface
	public interface SeedsFinder {
		/**
		 * @param roiImage the part of the view image inside the user's box; its interval
		 *                 is that box, i.e., it is <i>not</i> zero-min
		 * @return boxes in the coordinates of the {@code roiImage} (that is, in screen coordinates),
		 *         ends inclusive; an empty list if nothing is found, never null
		 */
		List<Interval> findSeeds(RandomAccessibleInterval<? extends RealType<?>> roiImage);
	}

	/**
	 * @param userEvent the user's box, and the view it was drawn on
	 * @param viewImage the 2D image of that view, zero-min, of the event's canvas size
	 *                  (pixel (x,y) is the screen pixel (x,y))
	 * @return the plan, one item per seed, in the order the finder returned them; possibly empty
	 */
	public static List<BdvPromptsEvent> plan(final BdvPromptsEvent userEvent,
	                                         final RandomAccessibleInterval<? extends RealType<?>> viewImage,
	                                         final SeedsFinder finder) {
		final List<Interval> seeds = finder.findSeeds( Views.interval(viewImage, userEvent.asInterval()) );
		final List<BdvPromptsEvent> plan = new ArrayList<>(seeds.size());
		for (Interval s : seeds)
			plan.add(userEvent.withBox((int) s.min(0), (int) s.min(1), (int) s.max(0), (int) s.max(1)));
		return plan;
	}

	/**
	 * The seeds finder of the original SAMJ-BDV "J" action: pixels above the lower end of the
	 * display range are thresholded, morphologically closed, connected components are found,
	 * and a box is returned for every component that's not too small and doesn't touch the
	 * border of the user's box. See {@link SeedsUtils#getSeedsByContrastThresholdingAndClosing}.
	 *
	 * @param contrastSetting whose display range is used for the thresholding; typically the
	 *                        source's ConverterSetup, so the user steers the seeds with the contrast
	 * @param debugImagesFlags see {@link SeedsUtils#SHOW_NO_DBGIMAGES} and friends
	 */
	public static SeedsFinder contrastThresholdingSeeds(final ConverterSetup contrastSetting, final int debugImagesFlags) {
		return roiImage -> {
			SeedsUtils.increaseDebugImagesCounter();
			final RandomAccessibleInterval<FloatType> seedsImg =
					SeedsUtils.getSeedsByContrastThresholdingAndClosing(asFloats(roiImage), contrastSetting, debugImagesFlags);

			//NB: the boxes come in the zero-min coordinates of the seedsImg, we want screen coordinates
			final long offX = roiImage.min(0), offY = roiImage.min(1);
			final List<Interval> boxes = new ArrayList<>();
			for (int[] b : SeedsUtils.returnSeedsAsBoxes(seedsImg, debugImagesFlags))
				boxes.add(new FinalInterval(new long[] {b[0] + offX, b[1] + offY}, new long[] {b[2] + offX, b[3] + offY}));
			return boxes;
		};
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static RandomAccessibleInterval<FloatType> asFloats(final RandomAccessibleInterval<? extends RealType<?>> img) {
		return Converters.convert((RandomAccessibleInterval) img,
				(i, o) -> ((FloatType) o).setReal(((RealType) i).getRealDouble()), new FloatType());
	}
}
