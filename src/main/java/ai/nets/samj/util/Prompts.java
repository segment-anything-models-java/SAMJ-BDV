package ai.nets.samj.util;

import bdv.tools.brightness.ConverterSetup;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.labeling.ConnectedComponents;
import net.imglib2.algorithm.morphology.Closing;
import net.imglib2.algorithm.neighborhood.RectangleShape;
import net.imglib2.algorithm.neighborhood.Shape;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

public class Prompts {
	public static final int SHOW_NO_DBGIMAGES = 0;
	public static final int SHOW_ORIGINAL_DBGIMAGE = 1;
	public static final int SHOW_SOURCE_DBGIMAGE = 2;
	public static final int SHOW_THRESHOLDED_DBGIMAGE = 4;
	public static final int SHOW_CLOSED_DBGIMAGE = 8;
	public static final int SHOW_COMPONENTS_DBGIMAGE = 16;
	public static final int SHOW_PROMPTS_DBGIMAGE = 32;

	private static int SHOW_DBGIMAGE_COUNTER = 0;
	private static final Shape SE_FOR_CLOSING = new RectangleShape(1, false);

	public static int increaseDebugImagesCounter() {
		SHOW_DBGIMAGE_COUNTER++;
		return SHOW_DBGIMAGE_COUNTER;
	}

	public static <T extends RealType<T> & NativeType<T>>
	Img<T> createImgOfSameTypeAndSize(final RandomAccessibleInterval<T> templateImg) {
		T type = templateImg.getAt( templateImg.minAsLongArray() );
		return new ArrayImgFactory<>(type).create(templateImg);
	}

	public static <I extends RealType<I>> Img<FloatType> createFloatImgOfSameSize(RandomAccessibleInterval<I> in) {
		return ArrayImgs.floats(in.dimensionsAsLongArray());
	}

	public static <I extends RealType<I>, O extends RealType<O>> void copyFromTo(RandomAccessibleInterval<I> in, RandomAccessibleInterval<O> out) {
		LoopBuilder.setImages(in,out).forEachPixel( (i,o) -> o.setReal(i.getRealDouble()) );
	}


	public static <T extends RealType<T> & NativeType<T>>
	Img<T> getSeedsByContrastThresholdingAndClosing(final RandomAccessibleInterval<T> originalRAI,
	                                                final ConverterSetup contrastAdjustment,
	                                                final int showDebugImagesFlag) {
		if ((showDebugImagesFlag & SHOW_ORIGINAL_DBGIMAGE) > 0) {
			ImageJFunctions.show(originalRAI, SHOW_DBGIMAGE_COUNTER + ": source original image");
		}

		//params for the contrast adjustment of the originalRAI
		final double min = contrastAdjustment.getDisplayRangeMin();
		double max = contrastAdjustment.getDisplayRangeMax();
		if (max == min) max += 1.0;
		final double range = max - min;

		Img<T> thresholdedImg = createImgOfSameTypeAndSize(originalRAI);
		LoopBuilder.setImages(originalRAI, thresholdedImg).forEachPixel( (i, o) -> {
			double px = Math.min(Math.max(i.getRealDouble() - min, 0.0) / range, 1.0);
			if (px > 0) o.setOne(); else o.setZero();
		} );

		if ((showDebugImagesFlag & SHOW_SOURCE_DBGIMAGE) > 0) {
			ImageJFunctions.show(
				Converters.convert(originalRAI, (i, o) -> o.setReal(Math.min(Math.max(i.getRealDouble() - min, 0.0) / range, 1.0)), new FloatType()),
				SHOW_DBGIMAGE_COUNTER + ": source contrast-adjusted image"
			);
		}
		if ((showDebugImagesFlag & SHOW_THRESHOLDED_DBGIMAGE) > 0) {
			ImageJFunctions.show(thresholdedImg, SHOW_DBGIMAGE_COUNTER+": thresholded image");
		}

		final Img<T> closedThesholdedImg = thresholdedImg.copy();
		//NB: close() requires that input and output image be different memory, which is why the closedThesholdedImg is created
		Closing.close(Views.extendValue(thresholdedImg,0), closedThesholdedImg, SE_FOR_CLOSING, 4);
		if ((showDebugImagesFlag & SHOW_CLOSED_DBGIMAGE) > 0) {
			ImageJFunctions.show(closedThesholdedImg, SHOW_DBGIMAGE_COUNTER+": thresholded then closed image");
		}

		return closedThesholdedImg;
	}


	/**
	 * @param seedsRAI A binary image (zero vs. non-zero values) for CCA, to establish bounding boxes
	 * @param showDebugImagesFlag Possibly show debug images; sensitive only to {@link #SHOW_COMPONENTS_DBGIMAGE} and {@link #SHOW_PROMPTS_DBGIMAGE}
	 * @return List of axis-aligned bounding boxes around CCA'ed seeds, a box is [x0,y0, x1,y1] in _local_ (0-based) coordinates
	 * @param <T> pixel type of the image submitted to the prompts processors, see {@link bdv.interactive.prompts.BdvPrompts}
	 */
	public static <T extends RealType<T> & NativeType<T>>
	List<int[]> returnSeedsAsBoxes(final RandomAccessibleInterval<T> seedsRAI,
	                               final int showDebugImagesFlag) {
		final Img<UnsignedShortType> ccaImg = ArrayImgs.unsignedShorts(seedsRAI.dimensionsAsLongArray());
		ccaImg.forEach(UnsignedShortType::setZero);

		ConnectedComponents.labelAllConnectedComponents(
			Converters.convert(seedsRAI, (i,o) -> o.setInteger(i.getRealDouble() > 0.0 ? 1 : 0), new ByteType()),
			ccaImg,
			ConnectedComponents.StructuringElement.EIGHT_CONNECTED);

		if ((showDebugImagesFlag & SHOW_COMPONENTS_DBGIMAGE) > 0) {
			ImageJFunctions.show(ccaImg, SHOW_DBGIMAGE_COUNTER+": thresholded then closed then labels image");
		}

		final Map<Integer, int[]> boxes = new HashMap<>(100);
		//NB: minX,minY, maxX,maxY

		final Cursor<UnsignedShortType> ccaPx = ccaImg.localizingCursor();
		final int[] ccaPxPos = new int[2];
		while (ccaPx.hasNext()) {
			final int px = ccaPx.next().get();
			if (px == 0) continue; //NB: skip background pixel
			ccaPx.localize(ccaPxPos);
			if (!boxes.containsKey(px)) boxes.put(px, new int[] {ccaPxPos[0],ccaPxPos[1],ccaPxPos[0],ccaPxPos[1]});
			int[] box = boxes.get(px);
			box[0] = Math.min(box[0], ccaPxPos[0]);
			box[1] = Math.min(box[1], ccaPxPos[1]);
			box[2] = Math.max(box[2], ccaPxPos[0]);
			box[3] = Math.max(box[3], ccaPxPos[1]);
		}

		final boolean doPromptsDebug = (showDebugImagesFlag & SHOW_PROMPTS_DBGIMAGE) > 0;
		if (doPromptsDebug) ccaImg.forEach(UnsignedShortType::setZero);
		//
		List<int[]> seeds = new ArrayList<>(boxes.size());
		for (int[] box : boxes.values()) {
			if ((box[2]-box[0])*(box[3]-box[1]) < 25) continue;         //skip over very small patches
			//if (box[4] < minimalBrightestIntensityThreshold) continue;  //skip over non-bright patches
			seeds.add(box);
			if (doPromptsDebug) {
				Views.interval(ccaImg, new long[] {box[0], box[1]}, new long[] {box[2], box[3]})
						  .forEach(UnsignedShortType::setOne);
			}
		}
		//
		if (doPromptsDebug) ImageJFunctions.show(ccaImg, SHOW_DBGIMAGE_COUNTER+": prompts as boxes image");
		return seeds;
	}
}
