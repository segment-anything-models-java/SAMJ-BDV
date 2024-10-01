package bdv.interactive.prompts.planarshapes;

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

	@Override
	public void addPoint(double x, double y) {
		super.addPoint(x,y);
		isHorizontalEdgesUpdated = false;
	}

	private Map<Integer, List<Double>> horizontalEdges = new HashMap<>(1000);
	private boolean isHorizontalEdgesUpdated = false;

	private void rebuildHorizontalEdgesMap() {
		//reset (aka clear)
		horizontalEdges = new HashMap<>(1000);

		//fill the horizontal map
		for (double[] xy : coords2D) {
			int yy = (int)xy[1];
			List<Double> edges = horizontalEdges.getOrDefault(yy, null);
			if (edges == null) {
				edges = new ArrayList<>(20);
				horizontalEdges.put(yy, edges);
			}
			edges.add(xy[0]);
		}

		//make the horizontal map ready for the evaluation
		for (int y : horizontalEdges.keySet()) {
			List<Double> edges = horizontalEdges.get(y);
			edges.sort(Comparator.naturalOrder());
		}

		isHorizontalEdgesUpdated = true;
	}

	@Override
	public boolean isPointInShape(double x, double y) {
		if (!isHorizontalEdgesUpdated) rebuildHorizontalEdgesMap();

		final List<Double> edges = horizontalEdges.getOrDefault((int)y, null);
		if (edges == null) return false;
		if (x < edges.get(0)) return false;

		boolean isInside = false;
		boolean wasLarger = true;
		for (double xThres : edges) {
			if (wasLarger && x < xThres) return isInside;
			isInside = !isInside;
			wasLarger = x >= xThres;
		}
		return false;
	}
}
