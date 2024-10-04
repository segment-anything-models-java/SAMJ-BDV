package ai.nets.samj.bdv.polygonconsumers;

import ai.nets.samj.util.PlanarShapesRasterizer;
import bdv.interactive.prompts.planarshapes.PlanarPolygonIn3D;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;

import java.util.function.Consumer;

public class PolygonsRasterizerInto3dImgConsumer<T extends RealType<T>> implements Consumer<PlanarPolygonIn3D> {
	final PlanarShapesRasterizer rasterizer = new PlanarShapesRasterizer();
	final RandomAccessibleInterval<T> targetRasterImg;
	int currentDrawingValue = 1;

	public PolygonsRasterizerInto3dImgConsumer(final RandomAccessibleInterval<T> targetImg) {
		if (targetImg.numDimensions() < 2)
			throw new IllegalArgumentException("Provide 2D, 3D (or more dimensional) image to draw polygons into.");

		this.targetRasterImg = targetImg;
	}

	@Override
	public void accept(PlanarPolygonIn3D polygon) {
		rasterizer.rasterizeIntoImg(polygon, Views.extendValue(targetRasterImg,0.0), currentDrawingValue++);
	}
}