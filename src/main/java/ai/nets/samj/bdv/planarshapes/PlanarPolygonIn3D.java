package ai.nets.samj.bdv.planarshapes;

import net.imglib2.realtransform.AffineTransform3D;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlanarPolygonIn3D extends AbstractPlanarShapeIn3D {
	public PlanarPolygonIn3D(final int expectedNoOfVertices, final AffineTransform3D viewTo3dTransform) {
		super(expectedNoOfVertices, viewTo3dTransform);
	}
}
