package ai.nets.samj.util;

import ij.ImagePlus;
import ij.process.FloatProcessor;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;

/**
 * Creates IJ1 and ImgLib2 _2D_ (and only 2D) images of the same size,
 * of float voxel type, and especially over the same shared memory --
 * so pixel values changes in one image are immediately visible
 * in the other image.
 */
public class ImgPlusOverImgSharedMem {
	/**
	 * Creates empty (pixels values uninitialized) shared pair of images.
	 * @param referenceInterval Size of the created 2D images.
	 */
	public ImgPlusOverImgSharedMem(final Interval referenceInterval) {
		//IJ ImagePlus native image
		this.floatImagePlus = new ImagePlus(
				  "BDV_ROI_for_creating_seeds",
				  new FloatProcessor((int)referenceInterval.dimension(0), (int)referenceInterval.dimension(1))
		);

		//ImgLib2 image over the same pixel-buffer, so changes in one image are visible in the other image
		this.floatTypeImg = ArrayImgs.floats(
				  (float[])floatImagePlus.getProcessor().getPixels(),
				  referenceInterval.dimensionsAsLongArray()
		);
	}

	/**
	 * Creates initialized shared pair of images, their content is copied
	 * from the given reference image.
	 * @param toBeClonedImg Size and content of the created 2D images.
	 */
	public <T extends RealType<T>> ImgPlusOverImgSharedMem(final RandomAccessibleInterval<T> toBeClonedImg) {
		this((Interval)toBeClonedImg);
		Prompts.copyFromTo(toBeClonedImg, this.floatTypeImg);
	}

	/**
	 * Only a convenience factory method.
	 */
	public static <T extends RealType<T>>
	ImgPlusOverImgSharedMem cloneThis(final RandomAccessibleInterval<T> originalRAI) {
		return new ImgPlusOverImgSharedMem(originalRAI);
	}

	public final ImagePlus floatImagePlus;
	public final Img<FloatType> floatTypeImg;
}
