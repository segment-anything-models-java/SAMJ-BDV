package ai.nets.samj.bdv.prompts;

import ai.nets.samj.bdv.planarshapes.PlanarPolygonIn3D;
import ai.nets.samj.bdv.planarshapes.PlanarRectangleIn3D;
import net.imglib2.Interval;
import net.imglib2.type.numeric.RealType;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class FakeResponder {
	public static <T extends RealType<T>>
	List<PlanarPolygonIn3D> polygons(final PlanarRectangleIn3D<T> prompt) {
		final PlanarPolygonIn3D polygon = new PlanarPolygonIn3D(1000, prompt.getTransformTo3d());
		final Interval insideThisBox = prompt.getBbox2D();

		Random rand = new Random();
		final int BOUND = 8;

		int minx = (int)insideThisBox.min(0), maxx = (int)insideThisBox.max(0);
		int miny = (int)insideThisBox.min(1), maxy = (int)insideThisBox.max(1);

		int r = rand.nextInt(BOUND);
		addPointsAlongLine(polygon, minx, miny, (minx+maxx)/2, miny+r);
		addPointsAlongLine(polygon, (minx+maxx)/2, miny+r, maxx, miny);

		r = rand.nextInt(BOUND);
		addPointsAlongLine(polygon, maxx, miny, maxx-r, (miny+maxy)/2);
		addPointsAlongLine(polygon, maxx-r, (miny+maxy)/2, maxx, maxy);

		r = rand.nextInt(BOUND);
		addPointsAlongLine(polygon, maxx, maxy, (minx+maxx)/2, maxy-r);
		addPointsAlongLine(polygon, (minx+maxx)/2, maxy-r, minx, maxy);

		r = rand.nextInt(BOUND);
		addPointsAlongLine(polygon, minx, maxy, minx+r, (miny+maxy)/2);
		addPointsAlongLine(polygon, minx+r, (miny+maxy)/2, minx, miny);

		return Arrays.asList(polygon);
	}

	public static void addPointsAlongLine(final PlanarPolygonIn3D polygon, int sx, int sy, int ex, int ey) {
		float steps = Math.max(Math.abs(ex-sx), Math.abs(ey-sy));
		for (int i = 0; i < steps; ++i) {
			int x = (int)( (float)i * (float)(ex-sx)/steps );
			int y = (int)( (float)i * (float)(ey-sy)/steps );
			polygon.addPoint(sx+x,sy+y);
		}
	}
}
