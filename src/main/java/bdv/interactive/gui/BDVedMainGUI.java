package bdv.interactive.gui;

import ai.nets.samj.annotation.Mask;
import ai.nets.samj.gui.MainGUI;
import ai.nets.samj.gui.components.ComboBoxItem;
import ai.nets.samj.ui.ConsumerInterface;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.miginfocom.layout.CC;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

public class BDVedMainGUI extends MainGUI {
	public BDVedMainGUI(final String bdvWindowTitle) {
		super(emptyFakeConsumer);
		associatedBdvLabelComponent.setText(" Associated to: "+bdvWindowTitle);
		touchUpForBdv();
	}

	@Override
	protected void makeVisibleOnInstantiation() {
		//intentionally empty to introduce the behaviour that this GUI
		//is *not* displayed immediately after the GUI (class) is constructed
		//(which is BTW the default behaviour of MainGui)
	}


	@Override
	protected JPanel createFirstComponent() {
		JPanel origPanel = super.createFirstComponent();
		origPanel.remove(cmbImages);
		origPanel.remove(go);

		//copied from MainGUI
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(2, 2, 2, 2); // Adjust insets as needed
		gbc.gridy = 1;
		gbc.gridx = 0;
		gbc.weightx = 1.0;
		gbc.weighty = 10.0;
		gbc.fill = GridBagConstraints.BOTH;
		associatedBdvLabelComponent = new JLabel();
		origPanel.add(associatedBdvLabelComponent, gbc);
		return origPanel;
	}
	protected JLabel associatedBdvLabelComponent;


	@Override
	protected JPanel createSecondComponent() {
		JPanel origPanel = super.createSecondComponent();
		cardPanel.remove(0);
		cardPanel.remove(0);

		JPanel card1 = new JPanel(new MigLayout("fill","center","[]push"));
		JTextPane htmlText = new JTextPane();
		htmlText.setContentType("text/html");
		htmlText.setText(SAMJ_CONTROLS_HELP_HTML);
		card1.add( new JScrollPane(htmlText,
				  JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED) );

		JPanel card2 = new JPanel(new MigLayout("fill","[c][c][c]"));
		card2.add(new JTextField("some script path"), new CC().grow().span());
		card2.add(new JButton("Template"), new CC().grow(1));
		card2.add(new JButton("Browse"), new CC().grow(1));
		card2.add(new JButton("Run..."), new CC().grow(2));

		cardPanel.add(card1, MANUAL_STR);
		cardPanel.add(card2, PRESET_STR);
		cardPanel.setPreferredSize(new Dimension(MAIN_HORIZONTAL_SIZE-30, (int)(0.3*MAIN_VERTICAL_SIZE)));
		return origPanel;
	}


	@Override
	protected JPanel createThirdComponent() {
		JPanel origPanel = super.createThirdComponent();
		origPanel.remove(chkRoiManager);
		return origPanel;
	}
	protected static int MAIN_HORIZONTAL_SIZE = 400;


	protected void touchUpForBdv() {
		retunLargest.setEnabled(true);
		export.setEnabled(true);

		radioButton1.setEnabled(true);
		radioButton2.setEnabled(true);
		cardPanel.setEnabled(true);

		setSize(MAIN_HORIZONTAL_SIZE, MAIN_VERTICAL_SIZE - 20);

		setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
		close.removeActionListener( close.getActionListeners()[0] ); //removes the original action
		close.addActionListener( (ignore) -> hideWindow() );
	}

	public void hideWindow() {
		this.setVisible(false);
	}

	public void showWindow() {
		this.setVisible(true);
	}

	public static final String SAMJ_CONTROLS_HELP_HTML =
			"<html><b>Hold down L</b> key, <b>Left Click and Drag</b> and <b>Release L</b>" +
			" on the image to select a region inside which SAMJ will annotate," +
			" operating on the <b>original image</b>.<br/><br/>" +
			"<b>Hold down K</b> key, <b>Left Click and Drag</b> and <b>Release K</b>" +
			" on the image to select a region inside which SAMJ will annotate," +
			" operating <b>under the current contrast</b> setting.</html>";

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
		new BDVedMainGUI("some BDV window").setVisible(true);
	}
}
