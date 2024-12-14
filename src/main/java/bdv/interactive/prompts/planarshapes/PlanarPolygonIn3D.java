package bdv.interactive.prompts.planarshapes;

import net.imglib2.realtransform.AffineTransform3D;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class PlanarPolygonIn3D extends AbstractPlanarShapeIn3D {
	public PlanarPolygonIn3D(final int expectedNoOfVertices, final AffineTransform3D planeTo3dTransform) {
		super(expectedNoOfVertices, planeTo3dTransform);
	}

	@Override
	public void addPoint(double x, double y) {
		super.addPoint(x,y);
		isEdgesListUpdated = false;
	}

	List<double[]> edges_xyxy;
	boolean isEdgesListUpdated = false;

	void rebuildEdgesList() {
		edges_xyxy = new ArrayList<>(coords2D.size()); //NB: this is still an empty list!
		isEdgesListUpdated = true;

		if (coords2D.size() < 2) return;

		Iterator<double[]> c_it = coords2D.iterator();
		double[] xyA = c_it.next();
		double[] xyB;
		while (c_it.hasNext()) {
			xyB = c_it.next();
			edges_xyxy.add(new double[] {xyA[0],xyA[1], xyB[0],xyB[1]});
			xyA = xyB;
		}
		//finish the loop...
		xyB = coords2D.get(0);
		edges_xyxy.add(new double[] {xyA[0],xyA[1], xyB[0],xyB[1]});
	}


	@Override
	public boolean isPointInShape(double x, double y) {
		if (!isEdgesListUpdated) rebuildEdgesList();

		//Even-odd rule for polygons "isInside?"
		//adapted from: https://en.wikipedia.org/wiki/Even-odd_rule
		long edgeCrossingsCnt = edges_xyxy.parallelStream().filter(edge -> {
			if (edge[0] == x && edge[1] == y) return true;     //is vertex
			if ((edge[1] > y) == (edge[3] > y)) return false;  //is outside y-range
			double slope = (x-edge[0])*(edge[3]-edge[1]) - (y-edge[1])*(edge[2]-edge[0]);
			if (slope == 0) return true;                       //is exactly on the edge
			return (slope < 0) != (edge[3]<edge[1]);
		}).count();

		//the "counter edgeCrossingsCnt" is odd iff the point is inside the polygon
		return (edgeCrossingsCnt & 1) == 1;
	}
}
