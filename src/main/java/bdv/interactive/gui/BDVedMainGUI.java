package bdv.interactive.gui;

import ai.nets.samj.annotation.Mask;
import ai.nets.samj.gui.MainGUI;
import ai.nets.samj.gui.components.ComboBoxItem;
import ai.nets.samj.ui.ConsumerInterface;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

public class BDVedMainGUI extends MainGUI {
	public BDVedMainGUI(final String bdvWindowTitle) {
		super(emptyFakeConsumer);
		touchUpForBdv();
	}

	@Override
	protected JPanel createFirstComponent() {
		JPanel origPanel = super.createFirstComponent();
		origPanel.remove(cmbImages);
		origPanel.remove(go);
		return origPanel;
	}

	@Override
	protected JPanel createThirdComponent() {
		JPanel origPanel = super.createThirdComponent();
		origPanel.remove(chkRoiManager);
		return origPanel;
	}

	protected void touchUpForBdv() {
		retunLargest.setEnabled(true);
		export.setEnabled(true);
		setSize(MAIN_HORIZONTAL_SIZE, MAIN_VERTICAL_SIZE - 90);
	}


	final static ConsumerInterface emptyFakeConsumer = new ConsumerInterface() {
		@Override
		public List<ComboBoxItem> getListOfOpenImages() { return Collections.emptyList(); }
		@Override
		public List<Polygon> getPolygonsFromRoiManager() { return Collections.emptyList(); }
		@Override
		public void enableAddingToRoiManager(boolean shouldBeAdding) { /* intentionally empty */ }

		@Override
		public void exportImageLabeling() {
			System.out.println("export happens here");
		}

		@Override
		public Object getFocusedImage() { return null; }
		@Override
		public <T extends RealType<T> & NativeType<T>> RandomAccessibleInterval<T> getFocusedImageAsRai() { return null; }
		@Override
		public List<int[]> getPointRoisOnFocusImage() { return Collections.emptyList(); }
		@Override
		public List<Rectangle> getRectRoisOnFocusImage() { return Collections.emptyList(); }
		@Override
		public void addPolygonsFromGUI(List<Mask> masks) { /* intentionally empty */ }
		@Override
		public void activateListeners() { /* intentionally empty */ }
		@Override
		public void deactivateListeners() { /* intentionally empty */ }
		@Override
		public void setFocusedImage(Object image) { /* intentionally empty */ }
		@Override
		public void deselectImage() { /* intentionally empty */ }
		@Override
		public void deletePointRoi(int[] pp) { /* intentionally empty */ }
		@Override
		public void deleteRectRoi(Rectangle rect) { /* intentionally empty */ }
		@Override
		public boolean isValidPromptSelected() { return false; }
	};


	public static void main(String[] args) {
		new BDVedMainGUI(null).setVisible(true);
	}
}
