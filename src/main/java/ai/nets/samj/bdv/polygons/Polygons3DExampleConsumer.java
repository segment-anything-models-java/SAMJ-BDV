package ai.nets.samj.bdv.polygons;

import java.util.function.Consumer;

public class Polygons3DExampleConsumer implements Consumer<Polygon3D> {
	@Override
	public void accept(Polygon3D polygon) {
		StringBuilder sb = new StringBuilder("Got 3D poly: [ ");
		for (int i = 0; i < polygon.size(); ++i) {
			double[] c = polygon.coordinate3D(i);
			sb.append("["+c[0]+","+c[1]+","+c[2]+"]");
			if (i < polygon.size()-1) sb.append(",");
		}
		sb.append(" ] - image grid (global) coordinates");
		System.out.println(sb);

		sb = new StringBuilder("Got 3D poly: [ ");
		for (int i = 0; i < polygon.size(); ++i) {
			double[] c = polygon.coordinate2D(i);
			sb.append("["+Math.round(c[0])+","+Math.round(c[1])+"]");
			if (i < polygon.size()-1) sb.append(",");
		}
		sb.append(" ] - view (local) coordinates");
		System.out.println(sb);
	}
}
