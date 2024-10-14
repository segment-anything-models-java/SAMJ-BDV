package ai.nets.samj.util;

import bdv.tools.brightness.ConverterSetup;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.labeling.ConnectedComponents;
import net.imglib2.algorithm.morphology.Closing;
import net.imglib2.algorithm.neighborhood.RectangleShape;
import net.imglib2.algorithm.neighborhood.Shape;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class Prompts {
	public static final int SHOW_NO_DBGIMAGES = 0;
	public static final int SHOW_ORIGINAL_DBGIMAGE = 1;
	public static final int SHOW_SOURCE_DBGIMAGE = 2;
	public static final int SHOW_THRESHOLDED_DBGIMAGE = 4;
	public static final int SHOW_CLOSED_DBGIMAGE = 8;
	public static final int SHOW_COMPONENTS_DBGIMAGE = 16;

	private static int SHOW_DBGIMAGE_COUNTER = 0;
	private static final Shape SE_FOR_CLOSING = new RectangleShape(1, false);

	public static <T extends RealType<T>>
	List<int[]> findSeedsAndReturnAsBoxes(final RandomAccessibleInterval<T> originalRAI,
	                                      final ConverterSetup contrastAdjustment,
	                                      final int showDebugImagesFlag) {
		SHOW_DBGIMAGE_COUNTER++;
		if ((showDebugImagesFlag & SHOW_ORIGINAL_DBGIMAGE) > 0) {
			ImageJFunctions.show(originalRAI, SHOW_DBGIMAGE_COUNTER + ": source original image");
		}

		//params for the contrast adjustment of the originalRAI
		final double min = contrastAdjustment.getDisplayRangeMin();
		double max = contrastAdjustment.getDisplayRangeMax();
		if (max == min) max += 1.0;
		final double range = max - min;

		final RandomAccessibleInterval<UnsignedByteType> thresholdedImg = ArrayImgs.unsignedBytes(originalRAI.dimensionsAsLongArray());
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

		final Img<UnsignedByteType> closedThesholdedImg = ArrayImgs.unsignedBytes(originalRAI.dimensionsAsLongArray());
		//NB: close() requires that input and output image be different memory, which is why the closedThesholdedImg is created
		Closing.close(Views.extendValue(thresholdedImg,0), closedThesholdedImg, SE_FOR_CLOSING, 4);
		if ((showDebugImagesFlag & SHOW_CLOSED_DBGIMAGE) > 0) {
			ImageJFunctions.show(closedThesholdedImg, SHOW_DBGIMAGE_COUNTER+": thresholded then closed image");
		}

		final Img<UnsignedShortType> ccaImg = ArrayImgs.unsignedShorts(originalRAI.dimensionsAsLongArray());
		ccaImg.forEach(UnsignedShortType::setZero);
		ConnectedComponents.labelAllConnectedComponents(closedThesholdedImg, ccaImg, ConnectedComponents.StructuringElement.EIGHT_CONNECTED);
		if ((showDebugImagesFlag & SHOW_COMPONENTS_DBGIMAGE) > 0) {
			ImageJFunctions.show(ccaImg, SHOW_DBGIMAGE_COUNTER+": thresholded then closed then labels image");
		}

		final Map<Integer, int[]> boxesAndStats = new HashMap<>(100);
		//NB: minX,minY, maxX,maxY, maxIntensity

		final RandomAccess<T> origPx = originalRAI.randomAccess();
		final Cursor<UnsignedShortType> ccaPx = ccaImg.localizingCursor();
		final int[] ccaPxPos = new int[2];
		while (ccaPx.hasNext()) {
			final int px = ccaPx.next().get();
			if (px == 0) continue; //NB: skip background pixel
			ccaPx.localize(ccaPxPos);
			ccaPxPos[0] += originalRAI.min(0); //NB: brings it to the coord system of originalRAI
			ccaPxPos[1] += originalRAI.min(1);
			if (!boxesAndStats.containsKey(px)) boxesAndStats.put(px, new int[] {ccaPxPos[0],ccaPxPos[1],ccaPxPos[0],ccaPxPos[1],px});
			int[] box = boxesAndStats.get(px);
			box[0] = Math.min(box[0], ccaPxPos[0]);
			box[1] = Math.min(box[1], ccaPxPos[1]);
			box[2] = Math.max(box[2], ccaPxPos[0]);
			box[3] = Math.max(box[3], ccaPxPos[1]);
			origPx.setPosition(ccaPxPos);
			box[4] = Math.max(box[4], (int)origPx.get().getRealDouble());
		}

		final int minimalBrightestIntensityThreshold = (int)(0.8*range + min);
		System.out.println("Considering only components brighter than "+minimalBrightestIntensityThreshold);

		List<int[]> seeds = new ArrayList<>(boxesAndStats.size());
		for (int[] box : boxesAndStats.values()) {
			if ((box[2]-box[0])*(box[3]-box[1]) < 25) continue;         //skip over very small patches
			if (box[4] < minimalBrightestIntensityThreshold) continue;  //skip over non-bright patches
			seeds.add(box);
		}
		return seeds;
	}

	//public static Map<Integer,int[]> findSeedsAndReturnAsCentres() {
	//}
}
